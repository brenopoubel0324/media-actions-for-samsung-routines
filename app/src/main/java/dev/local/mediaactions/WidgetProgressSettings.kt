package dev.local.mediaactions

import android.content.Context

/** Global timing is independent of each widget's appearance and enabled flag. */
object WidgetProgressSettings {
    val intervals = listOf(5, 15, 30)
    fun load(context: Context): Int =
        (context.getSharedPreferences("widget_progress_settings", Context.MODE_PRIVATE).all["interval_seconds"] as? Int)
            ?.takeIf { it in intervals } ?: 5

    fun save(context: Context, seconds: Int) {
        require(seconds in intervals)
        context.getSharedPreferences("widget_progress_settings", Context.MODE_PRIVATE).edit()
            .putInt("interval_seconds", seconds).apply()
        WidgetPlaybackObserver.settingsChanged(context)
    }
}
