package dev.local.mediaactions

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.media.MediaMetadata
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper

/** Bound to the listener lifecycle; progress refresh is limited to opted-in widgets on an interactive screen. */
class WidgetPlaybackObserver(context: Context,
    private val progressReader: () -> WidgetTrackProgress? = { WidgetTrackProgress.read(context.applicationContext) },
    private val onChanged: ((WidgetPlaybackState) -> Unit)? = null,
) : AutoCloseable {
    private val app = context.applicationContext
    private val manager = app.getSystemService(MediaSessionManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val watched = mutableMapOf<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    private var running = false
    private var activeRegistered = false
    private var keyRegistered = false
    private var lastState: WidgetPlaybackState? = null
    private var refreshPending = false
    private var forceRefresh = false
    private var receiverRegistered = false
    private val pulse = Runnable { scheduleRefresh(true) }
    private val visibilityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handler.removeCallbacks(pulse)
            if (intent.action != Intent.ACTION_SCREEN_OFF) scheduleRefresh(true)
        }
    }
    private fun progressWidgets(): Boolean = try {
        val widgets = AppWidgetManager.getInstance(app)
        widgets.getAppWidgetIds(ComponentName(app, MediaWidgetProvider::class.java)).any {
            WidgetStyleStore.load(app, it).progressRing
        }
    } catch (_: RuntimeException) { false }
    private fun updatePulse(state: WidgetPlaybackState) {
        handler.removeCallbacks(pulse)
        if (running && state == WidgetPlaybackState.PLAYING && progressWidgets() &&
            app.getSystemService(PowerManager::class.java).isInteractive && progressReader() != null)
            handler.postDelayed(pulse, 5_000)
    }
    private val refresh = Runnable {
        refreshPending = false
        if (running) {
            val state = WidgetPlaybackState.read(AndroidSessionGateway(app))
            if (state != lastState) emit(state)
            else if (forceRefresh && progressWidgets()) emit(state, progressOnly = true)
            forceRefresh = false
            updatePulse(state)
        }
    }
    private val activeListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        if (running) {
            watch(controllers.orEmpty())
            scheduleRefresh(true)
        }
    }
    private val keyListener = MediaSessionManager.OnMediaKeyEventSessionChangedListener { _, _ ->
        scheduleRefresh(true)
    }

    fun start() {
        if (running) return
        running = true
        try {
            app.registerReceiver(visibilityReceiver, IntentFilter(Intent.ACTION_SCREEN_ON).apply {
                addAction(Intent.ACTION_SCREEN_OFF); addAction(SETTINGS_CHANGED)
            }, Context.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
            manager.addOnActiveSessionsChangedListener(activeListener,
                AndroidSessionGateway.listenerComponent(app), handler)
            activeRegistered = true
            watch(manager.getActiveSessions(AndroidSessionGateway.listenerComponent(app)))
        } catch (_: RuntimeException) {
            // A revoked grant or a vendor failure must not crash the system-bound service.
            close()
            emit(WidgetPlaybackState.UNKNOWN)
            return
        }
        try {
            manager.addOnMediaKeyEventSessionChangedListener(app.mainExecutor, keyListener)
            keyRegistered = true
        } catch (_: RuntimeException) {
            // Playback and active-session callbacks remain useful if this hint is unavailable.
        }
        scheduleRefresh()
    }

    private fun watch(controllers: List<MediaController>) {
        val tokens = controllers.map { it.sessionToken }.toSet()
        for (token in watched.keys.toList()) if (token !in tokens) {
            watched.remove(token)?.let { (controller, callback) ->
                try { controller.unregisterCallback(callback) } catch (_: RuntimeException) { /* Already gone. */ }
            }
        }
        for (controller in controllers) {
            val token = controller.sessionToken
            if (token in watched) continue
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = scheduleRefresh(true)
                override fun onMetadataChanged(metadata: MediaMetadata?) = scheduleRefresh(true)
                override fun onSessionDestroyed() {
                    watched.remove(token)?.let { (old, registered) ->
                        try { old.unregisterCallback(registered) } catch (_: RuntimeException) { /* Already gone. */ }
                    }
                    scheduleRefresh(true)
                }
            }
            try {
                controller.registerCallback(callback, handler)
                watched[token] = controller to callback
            } catch (_: RuntimeException) { /* A session may disappear while the list is being read. */ }
        }
    }

    private fun scheduleRefresh(force: Boolean = false) {
        forceRefresh = forceRefresh || force
        if (!running || refreshPending) return
        refreshPending = true
        handler.post(refresh)
    }

    private fun emit(state: WidgetPlaybackState, progressOnly: Boolean = false) {
        try {
            if (onChanged != null) onChanged.invoke(state)
            else MediaWidgetProvider.refreshAll(app, state, progressOnly)
            lastState = state
        } catch (_: RuntimeException) {
            // A host failure must not kill the listener or mark an undelivered state as current.
        }
    }

    companion object {
        private const val SETTINGS_CHANGED = "dev.local.mediaactions.WIDGET_PROGRESS_SETTINGS"
        fun settingsChanged(context: Context) {
            context.sendBroadcast(Intent(SETTINGS_CHANGED).setPackage(context.packageName))
        }
    }

    override fun close() {
        running = false
        handler.removeCallbacks(refresh)
        handler.removeCallbacks(pulse)
        if (receiverRegistered) {
            try { app.unregisterReceiver(visibilityReceiver) } catch (_: RuntimeException) { /* Already gone. */ }
            receiverRegistered = false
        }
        refreshPending = false
        forceRefresh = false
        for ((controller, callback) in watched.values) {
            try { controller.unregisterCallback(callback) } catch (_: RuntimeException) { /* Already gone. */ }
        }
        watched.clear()
        if (activeRegistered) try {
            manager.removeOnActiveSessionsChangedListener(activeListener)
        } catch (_: RuntimeException) { /* Grant may already be revoked. */ }
        if (keyRegistered) try {
            manager.removeOnMediaKeyEventSessionChangedListener(keyListener)
        } catch (_: RuntimeException) { /* Grant may already be revoked. */ }
        activeRegistered = false
        keyRegistered = false
        lastState = null
    }
}
