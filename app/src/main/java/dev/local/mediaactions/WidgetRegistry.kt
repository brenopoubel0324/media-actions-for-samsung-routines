package dev.local.mediaactions

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/** Catalog for editing and presentation; command receivers validate their own provider independently. */
internal object WidgetRegistry {
    private fun component(context: Context, kind: WidgetKind) = ComponentName(context, when (kind) {
        WidgetKind.MEDIA -> MediaWidgetProvider::class.java
        WidgetKind.VOLUME -> VolumeWidgetProvider::class.java
        WidgetKind.PLAYBACK_VOLUME -> PlaybackVolumeWidgetProvider::class.java
    })

    fun kind(context: Context, id: Int): WidgetKind? {
        if (id <= AppWidgetManager.INVALID_APPWIDGET_ID) return null
        return try {
            val provider = AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider ?: return null
            WidgetKind.entries.firstOrNull { component(context, it) == provider }
        } catch (_: RuntimeException) { null }
    }

    fun isVolume(context: Context, id: Int) = kind(context, id) == WidgetKind.VOLUME
    fun isOwnWidget(context: Context, id: Int) = kind(context, id) != null

    fun ids(context: Context): List<Int> {
        val manager = AppWidgetManager.getInstance(context)
        return WidgetKind.entries.flatMap { manager.getAppWidgetIds(component(context, it)).toList() }
            .filter { isOwnWidget(context, it) }.distinct().sorted()
    }

    fun playbackIds(context: Context): List<Int> = ids(context).filter { kind(context, it)?.hasPlayback == true }

    fun refresh(context: Context, id: Int): MediaWidgetProvider.RefreshResult = when (kind(context, id)) {
        WidgetKind.MEDIA -> MediaWidgetProvider.refreshWidgetResult(context, id)
        WidgetKind.VOLUME -> VolumeWidgetProvider.refresh(context, id)
        WidgetKind.PLAYBACK_VOLUME -> PlaybackVolumeWidgetProvider.refresh(context, id)
        null -> MediaWidgetProvider.RefreshResult.MISSING_WIDGET
    }

    /** A single snapshot serves every usable playback consumer in an explicit presentation batch. */
    internal fun updatePlayback(context: Context, manager: AppWidgetManager, ids: List<Int>,
        playback: WidgetPlaybackState? = null, includeUnavailable: Boolean = true) {
        val targets = ids.mapNotNull { id ->
            val kind = kind(context, id)?.takeIf { it.hasPlayback } ?: return@mapNotNull null
            val options = manager.getAppWidgetOptions(id)
            val usable = WidgetRenderer.hasUsablePlaybackSize(options, kind)
            if (!usable && !includeUnavailable) return@mapNotNull null
            PlaybackTarget(id, kind, options, WidgetStyleStore.load(context, id), usable)
        }
        if (targets.isEmpty()) return
        WidgetOptionsUpdates.forget(context, targets.map { it.id }.toIntArray())
        val usable = targets.filter { it.usable }
        val snapshot = if (usable.isEmpty()) WidgetUpdateSnapshot(WidgetPlaybackState.UNKNOWN, null)
            else WidgetUpdateSnapshot.capture(AndroidSessionGateway(context), usable.any { it.style.progressRing }, playback)
        var failure: RuntimeException? = null
        for (target in targets) try {
            manager.updateAppWidget(target.id,
                WidgetRenderer.render(context, target.id, target.options, snapshot, target.style, kind = target.kind))
        } catch (error: RuntimeException) {
            // One failing host must not leave every other widget showing stale playback.
            if (failure == null) failure = error
        }
        failure?.let { throw it }
    }

    private data class PlaybackTarget(val id: Int, val kind: WidgetKind, val options: android.os.Bundle,
        val style: WidgetAppearance, val usable: Boolean)

    fun refreshPlayback(context: Context, playback: WidgetPlaybackState? = null,
        progressOnly: Boolean = false, includeUnavailable: Boolean = false) {
        val ids = playbackIds(context).filter { !progressOnly || WidgetStyleStore.load(context, it).progressRing }
        if (ids.isEmpty()) return
        updatePlayback(context, AppWidgetManager.getInstance(context), ids, playback, includeUnavailable)
        if (playback != WidgetPlaybackState.UNKNOWN)
            WidgetPlaybackObserver.settingsChanged(context, resyncSessions = true)
    }

    fun refreshAll(context: Context) {
        refreshPlayback(context, includeUnavailable = true)
        VolumeWidgetProvider.refreshAll(context)
    }

    fun label(context: Context, id: Int) = context.getString(when (kind(context, id)) {
        WidgetKind.VOLUME -> R.string.volume_widget_name
        WidgetKind.PLAYBACK_VOLUME -> R.string.playback_volume_widget_name
        else -> R.string.widget_name
    })
}
