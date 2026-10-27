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

/** Direct broadcasts for media commands; playback changes arrive through the bound listener. */
class MediaWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        WidgetPlaybackObserver.settingsChanged(context)
        val playback = WidgetPlaybackState.read(AndroidSessionGateway(context))
        for (id in appWidgetIds) manager.updateAppWidget(id,
            WidgetRenderer.render(context, id, manager.getAppWidgetOptions(id), playback))
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        manager.updateAppWidget(appWidgetId, WidgetRenderer.render(context, appWidgetId, newOptions))
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CONTROL) {
            super.onReceive(context, intent)
            return
        }
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id <= AppWidgetManager.INVALID_APPWIDGET_ID) return
        val action = MediaAction.entries.firstOrNull { intent.data == commandUri(id, it) } ?: return
        // A removed widget's old PendingIntent must not keep controlling playback.
        if (!isOwnWidget(context, id)) return
        val pending = goAsync()
        var finished = false
        fun finishOnce() { if (!finished) { finished = true; pending?.finish() } }
        val app = context.applicationContext
        try {
            PhoneMediaActions(app).execute(action) { result ->
                try {
                    Log.i(ActionActivity.LOG_TAG, "Widget ${action.name}: ${result.javaClass.simpleName}")
                    refreshWidget(app, id)
                } finally { finishOnce() }
            }
        } catch (_: RuntimeException) { finishOnce() }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            WidgetStyleStore.remove(context, id)
            for (action in MediaAction.entries) {
                PendingIntent.getBroadcast(context, id, commandIntent(context, id, action),
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.cancel()
            }
        }
    }

    companion object {
        private const val ACTION_CONTROL = "dev.local.mediaactions.WIDGET_CONTROL"

        private fun commandUri(id: Int, action: MediaAction): Uri =
            Uri.parse("mediaactions://widget/$id/${when (action) {
                MediaAction.NEXT -> "next"
                MediaAction.PREVIOUS -> "previous"
                MediaAction.PLAY_PAUSE -> "toggle"
            }}")

        private fun commandIntent(context: Context, id: Int, action: MediaAction): Intent =
            Intent(context, MediaWidgetProvider::class.java)
                .setAction(ACTION_CONTROL)
                .setData(commandUri(id, action))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)

        internal fun controlIntent(context: Context, id: Int, action: MediaAction): PendingIntent =
            PendingIntent.getBroadcast(context, id, commandIntent(context, id, action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        internal fun createViews(context: Context, id: Int): RemoteViews =
            WidgetRenderer.render(context, id, AppWidgetManager.getInstance(context).getAppWidgetOptions(id))

        internal fun isOwnWidget(context: Context, id: Int): Boolean {
            if (id <= AppWidgetManager.INVALID_APPWIDGET_ID) return false
            return try {
                AppWidgetManager.getInstance(context.applicationContext).getAppWidgetInfo(id)?.provider ==
                    ComponentName(context, MediaWidgetProvider::class.java)
            } catch (_: RuntimeException) {
                false
            }
        }

        internal fun refreshWidget(context: Context, id: Int) {
            if (!isOwnWidget(context, id)) return
            val manager = AppWidgetManager.getInstance(context.applicationContext)
            MediaWidgetProvider().onUpdate(context.applicationContext, manager, intArrayOf(id))
        }

        fun refreshAll(context: Context, playback: WidgetPlaybackState? = null, progressOnly: Boolean = false) {
            val manager = AppWidgetManager.getInstance(context.applicationContext)
            val ids = manager.getAppWidgetIds(ComponentName(context, MediaWidgetProvider::class.java))
                .filter { !progressOnly || WidgetStyleStore.load(context, it).progressRing }
            if (ids.isEmpty()) return
            val state = playback ?: WidgetPlaybackState.read(AndroidSessionGateway(context))
            for (id in ids) manager.updateAppWidget(id,
                WidgetRenderer.render(context, id, manager.getAppWidgetOptions(id), state))
        }
    }
}
