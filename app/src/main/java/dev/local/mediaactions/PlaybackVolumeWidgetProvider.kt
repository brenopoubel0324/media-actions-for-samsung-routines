package dev.local.mediaactions

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import android.widget.Toast
import java.util.concurrent.atomic.AtomicBoolean

/** Three fixed commands. Audio-only gestures never consult or refresh media sessions. */
class PlaybackVolumeWidgetProvider : AppWidgetProvider() {
    enum class Command(internal val path: String) { LOWER("down"), TOGGLE("toggle"), RAISE("up") }

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        WidgetPlaybackObserver.settingsChanged(context)
        WidgetRegistry.updatePlayback(context, manager, appWidgetIds.filter { isOwnWidget(context, it) })
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int,
        newOptions: Bundle) {
        if (!isOwnWidget(context, appWidgetId)) return
        WidgetOptionsUpdates.deliver(context, appWidgetId, WidgetKind.PLAYBACK_VOLUME, newOptions) { appearance ->
            manager.updateAppWidget(appWidgetId, WidgetRenderer.render(context, appWidgetId, newOptions,
                appearance = appearance, kind = WidgetKind.PLAYBACK_VOLUME))
            WidgetPlaybackObserver.settingsChanged(context, resyncSessions = true)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CONTROL) { super.onReceive(context, intent); return }
        if (intent.component != ComponentName(context, PlaybackVolumeWidgetProvider::class.java)) return
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (!isOwnWidget(context, id)) return
        val command = Command.entries.firstOrNull { intent.data == uri(id, it) } ?: return
        when (command) {
            Command.LOWER -> VolumeActions.step(context, false)
            Command.RAISE -> VolumeActions.step(context, true)
            Command.TOGGLE -> toggle(context, id)
        }
    }

    private fun toggle(context: Context, id: Int) {
        val pending = goAsync()
        val completed = AtomicBoolean()
        val app = context.applicationContext
        fun finish(shouldRefresh: Boolean, missingAccess: Boolean = false) {
            if (!completed.compareAndSet(false, true)) return
            try {
                if (missingAccess) Toast.makeText(app, R.string.playback_volume_missing_access, Toast.LENGTH_LONG).show()
                if (shouldRefresh) refresh(app, id)
            } finally { pending?.finish() }
        }
        try {
            PhoneMediaActions(app).execute(MediaAction.PLAY_PAUSE) { result ->
                Log.i(ActionActivity.LOG_TAG, "Playback/volume widget: ${result.javaClass.simpleName}")
                finish(true, result == ActionResult.MissingNotificationAccess)
            }
        } catch (_: RuntimeException) { finish(false) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetOptionsUpdates.forget(context, appWidgetIds)
        for (id in appWidgetIds) {
            WidgetStyleStore.remove(context, id)
            WidgetLocationStore.remove(context, id)
            for (action in Command.entries) PendingIntent.getBroadcast(context, id, commandIntent(context, id, action),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.cancel()
        }
        WidgetPlaybackObserver.settingsChanged(context)
    }

    companion object {
        private const val ACTION_CONTROL = "dev.local.mediaactions.PLAYBACK_VOLUME_CONTROL"
        private fun uri(id: Int, command: Command) = Uri.parse("mediaactions://playback-volume/$id/${command.path}")
        private fun commandIntent(context: Context, id: Int, command: Command) =
            Intent(context, PlaybackVolumeWidgetProvider::class.java).setAction(ACTION_CONTROL).setData(uri(id, command))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).addFlags(Intent.FLAG_RECEIVER_FOREGROUND)

        internal fun controlIntent(context: Context, id: Int, command: Command): PendingIntent =
            PendingIntent.getBroadcast(context, id, commandIntent(context, id, command),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        internal fun isOwnWidget(context: Context, id: Int): Boolean = id > AppWidgetManager.INVALID_APPWIDGET_ID && try {
            AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider ==
                ComponentName(context, PlaybackVolumeWidgetProvider::class.java)
        } catch (_: RuntimeException) { false }

        internal fun render(context: Context, id: Int, options: Bundle, appearance: WidgetAppearance? = null): RemoteViews =
            WidgetRenderer.render(context, id, options, appearance = appearance, kind = WidgetKind.PLAYBACK_VOLUME)

        internal fun refresh(context: Context, id: Int): MediaWidgetProvider.RefreshResult {
            if (!isOwnWidget(context, id)) return MediaWidgetProvider.RefreshResult.MISSING_WIDGET
            WidgetOptionsUpdates.forget(context, intArrayOf(id))
            return try {
                WidgetPlaybackObserver.settingsChanged(context, resyncSessions = true)
                WidgetRegistry.updatePlayback(context, AppWidgetManager.getInstance(context), listOf(id))
                MediaWidgetProvider.RefreshResult.REQUESTED
            } catch (_: RuntimeException) { MediaWidgetProvider.RefreshResult.FAILED }
        }
    }
}
