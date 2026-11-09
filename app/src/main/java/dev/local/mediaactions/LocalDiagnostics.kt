package dev.local.mediaactions

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import java.text.DateFormat
import java.util.Date
import java.util.TimeZone

/** Ephemeral, redacted trace of the last user-initiated widget save. No media metadata. */
internal object WidgetSaveTrace {
    @Volatile var last: String = "none"
        private set

    fun record(widgetId: Int?, mode: String, stage: String, appearanceHash: Int) {
        last = "id=${widgetId ?: "defaults"} mode=$mode stage=$stage appearanceHash=$appearanceHash"
    }
}

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
        val ids = WidgetRegistry.ids(context)
        appendLine("lastWidgetSave=" + WidgetSaveTrace.last)
        appendLine("defaultsAppearanceHash=" + WidgetStyleStore.load(context).hashCode())
        for (id in ids) {
            val options = manager.getAppWidgetOptions(id)
            val style = WidgetStyleStore.load(context, id)
            appendLine("Widget #$id ["+WidgetRegistry.label(context,id)+"]: " + WidgetLocationStore.description(context, id))
            appendLine("hasOverride=" + WidgetStyleStore.hasOverride(context, id))
            appendLine("appearanceHash=" + style.hashCode() +
                " style=" + style.iconStyle.name + " dark=" + style.darkIcons +
                " scale=" + style.iconScalePercent + " progress=" + style.progressRing)
            appendLine("hostCategory=" + options.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, 0))
            val sizes = WidgetRenderer.reportedSizes(options)
            appendLine("sizesDp=" + sizes.joinToString { "${it.width} x ${it.height}" }.ifEmpty { "?" })
            sizes.firstOrNull()?.let { size ->
                val volumeLayout = if(WidgetRegistry.isVolume(context,id)) VolumeWidgetLayout.calculate(size.width,size.height,style) else null
                if(volumeLayout!=null) appendLine("volumeOrientation="+if(volumeLayout.vertical) "VERTICAL" else "HORIZONTAL")
                val effective = volumeLayout?.geometry ?: WidgetGeometry.calculate(size.width, size.height, style)
                appendLine("requestedSpacingDp=" + style.spacingDp + " effectiveSpacingDp=" + effective.gapDp)
                appendLine("requestedPaddingDp=" + style.widgetPaddingHorizontalDp + "," +
                    style.widgetPaddingVerticalDp + " effectivePaddingDp=" +
                    effective.widgetPaddingHorizontalDp + "," + effective.widgetPaddingVerticalDp)
            }
        }
        append(context.getString(R.string.diagnostic_versions_manual))
    }
}
