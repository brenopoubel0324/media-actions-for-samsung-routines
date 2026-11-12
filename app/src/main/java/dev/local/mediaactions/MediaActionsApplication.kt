package dev.local.mediaactions

import android.app.Application
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import java.lang.ref.WeakReference
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Wallpaper observation is process-local and optional; no service, alarm, job or wake lock. */
class MediaActionsApplication : Application() {
    internal val wallpaperColors by lazy { WidgetWallpaperRuntime(this) }
    override fun onCreate() {
        super.onCreate()
        wallpaperColors.settingsChanged()
    }
}

internal class WidgetWallpaperRuntime(private val app: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var executor: ExecutorService? = null
    private var listener: WallpaperManager.OnColorsChangedListener? = null
    private var receiver: BroadcastReceiver? = null
    private var reconcilePending = false
    internal var observer: WeakReference<WidgetPlaybackObserver>? = null
    internal val coordinator = WallpaperColorCoordinator(::subscribe, ::unsubscribe, ::read,
        { task -> handler.post { task() } }, ::render)

    init { coordinator.screen(app.getSystemService(PowerManager::class.java).isInteractive) }

    fun settingsChanged() {
        if (reconcilePending) return
        reconcilePending = true
        handler.post {
            reconcilePending = false
            try {
                val manager = AppWidgetManager.getInstance(app)
                coordinator.reconcile(WidgetRegistry.ids(app).map { id ->
                    val style = WidgetStyleStore.load(app, id)
                    WallpaperColorCoordinator.Target(id, WidgetRegistry.isVolume(app, id), style,
                        WidgetWallpaperColors.destination(style, WidgetLocationStore.load(app, id), manager.getAppWidgetOptions(id)))
                })
            } catch (_: RuntimeException) { /* Preserve the last registry; retry on the next host/configuration event. */ }
        }
    }

    fun resolve(id: Int, style: WidgetAppearance, options: android.os.Bundle): WidgetAppearance {
        if (style.colorMode == WidgetColorMode.FIXED) return style
        return coordinator.resolve(style, WidgetWallpaperColors.destination(style, WidgetLocationStore.load(app, id), options))
    }

    private fun subscribe(changed: (WallpaperDestination, WallpaperColorReading) -> Unit): Boolean {
        unsubscribe()
        val manager = app.getSystemService(WallpaperManager::class.java)
        val callback = WallpaperManager.OnColorsChangedListener { colors, which ->
            val value = WallpaperColorReading.fromColors(colors)
            if (which and WallpaperManager.FLAG_SYSTEM != 0) changed(WallpaperDestination.HOME, value)
            if (which and WallpaperManager.FLAG_LOCK != 0) changed(WallpaperDestination.LOCK, value)
        }
        val visibility = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                coordinator.screen(intent.action == Intent.ACTION_SCREEN_ON)
            }
        }
        return try {
            app.registerReceiver(visibility, IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_SCREEN_OFF) }, Context.RECEIVER_NOT_EXPORTED)
            receiver = visibility
            manager.addOnColorsChangedListener(callback, handler)
            listener = callback
            true
        } catch (_: RuntimeException) { unsubscribe(); false }
    }

    private fun unsubscribe() {
        listener?.let { try { app.getSystemService(WallpaperManager::class.java).removeOnColorsChangedListener(it) } catch (_: RuntimeException) { /* Already detached. */ } }
        listener = null
        receiver?.let { try { app.unregisterReceiver(it) } catch (_: RuntimeException) { /* Already detached. */ } }
        receiver = null
        executor?.shutdownNow(); executor = null
    }

    private fun read(destination: WallpaperDestination, completed: (WallpaperColorReading) -> Unit) {
        val worker = executor ?: Executors.newSingleThreadExecutor { task -> Thread(task, "widget-wallpaper-colors").apply { isDaemon = true } }.also { executor = it }
        worker.execute {
            val value = try {
                WallpaperColorReading.fromColors(app.getSystemService(WallpaperManager::class.java).getWallpaperColors(
                    if (destination == WallpaperDestination.HOME) WallpaperManager.FLAG_SYSTEM else WallpaperManager.FLAG_LOCK))
            } catch (error: RuntimeException) { WallpaperColorReading(null, errorClass = error.javaClass.simpleName) }
            handler.post { completed(value) }
        }
    }

    private fun render(changes: List<Pair<WallpaperColorCoordinator.Target, WidgetAppearance>>): Set<Int> {
        if (!app.getSystemService(PowerManager::class.java).isInteractive) return emptySet()
        val manager = AppWidgetManager.getInstance(app)
        val mediaObserver = observer?.get()
        // One permission/state check per batch, independent of the number of media widgets.
        val snapshot = if (changes.any { !it.first.volume }) mediaObserver?.wallpaperSnapshot() else null
        val successful = mutableSetOf<Int>()
        for ((target, resolved) in changes) try {
            // A queued callback must never overwrite a saved manual choice or a removed instance.
            val owns = if (target.volume) VolumeWidgetProvider.isOwnWidget(app, target.id) else MediaWidgetProvider.isOwnWidget(app, target.id)
            if (!owns || WidgetStyleStore.load(app, target.id) != target.style) continue
            if (target.volume) manager.updateAppWidget(target.id,
                VolumeWidgetProvider.render(app, target.id, manager.getAppWidgetOptions(target.id), resolved))
            else if (snapshot == null || mediaObserver?.refreshWallpaper(target.id, snapshot) != true)
                manager.updateAppWidget(target.id, WidgetRenderer.render(app, target.id,
                    manager.getAppWidgetOptions(target.id), WidgetUpdateSnapshot(WidgetPlaybackState.UNKNOWN, null), resolved))
            successful += target.id
        } catch (_: RuntimeException) { /* Retry this instance on the next event, without a timer. */ }
        return successful
    }

    companion object {
        fun get(context: Context): WidgetWallpaperRuntime? = (context.applicationContext as? MediaActionsApplication)?.wallpaperColors
    }
}
