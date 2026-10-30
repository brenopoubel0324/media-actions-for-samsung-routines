package dev.local.mediaactions

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import java.text.DateFormat
import java.util.Date
import java.util.TimeZone

/** Local, on-demand support text. Never includes player metadata, session tokens or node identifiers. */
object LocalDiagnostics {
    internal fun completionTime(context: Context, time: Long?): String {
        if (time == null) return context.getString(R.string.diagnostic_time_unknown)
        val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, context.resources.configuration.locales[0])
        format.timeZone = TimeZone.getDefault()
        return format.format(Date(time))
    }
    internal fun spotifyLine(context: Context, completion: SpotifyPlaybackResumer.Companion.Completion = SpotifyPlaybackResumer.lastCompletion(context)): String =
        context.getString(R.string.spotify_resume_last, context.getString(completion.status.message), completionTime(context, completion.completedAtEpochMs))

    fun text(context: Context): String = buildString {
        val completion = SpotifyPlaybackResumer.lastCompletion(context)
        appendLine("Media Actions " + context.packageManager.getPackageInfo(context.packageName, 0).versionName)
        appendLine("Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")")
        appendLine("Spotify: " + completion.status.name)
        appendLine(spotifyLine(context, completion))
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, MediaWidgetProvider::class.java))
            .filter { MediaWidgetProvider.isOwnWidget(context, it) }.sorted()
        for (id in ids) {
            val options = manager.getAppWidgetOptions(id)
            appendLine("Widget #$id: " + WidgetLocationStore.description(context, id))
            appendLine("hostCategory=" + options.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, 0))
            val sizes = WidgetRenderer.reportedSizes(options)
            appendLine("sizesDp=" + sizes.joinToString { "${it.width} x ${it.height}" }.ifEmpty { "?" })
        }
        append(context.getString(R.string.diagnostic_versions_manual))
    }
}
