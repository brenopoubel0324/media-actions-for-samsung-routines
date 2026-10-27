package dev.local.mediaactions

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.SystemClock

/** Only duration/position are read; no track title, artist or artwork is retained. */
data class WidgetTrackProgress(val fraction: Float) {
    companion object {
        fun fraction(position: Long, duration: Long, updated: Long, speed: Float, playing: Boolean, now: Long): WidgetTrackProgress? {
            if (duration <= 0 || position < 0) return null
            val elapsed = if (playing && updated > 0 && now >= updated && speed.isFinite())
                (now - updated).toDouble() * speed else 0.0
            return WidgetTrackProgress(((position.toDouble() + elapsed) / duration).coerceIn(0.0, 1.0).toFloat())
        }
        fun read(context: Context): WidgetTrackProgress? = try {
            val gateway = AndroidSessionGateway(context)
            if (!gateway.hasNotificationAccess()) null else {
                val selected = MediaSessionSelector.select(gateway.snapshot()) as? Selection.Selected
                val token = selected?.session?.sessionIdentity as? MediaSession.Token
                if (token == null) null else {
                    val controller = MediaController(context, token)
                    val state = controller.playbackState
                    if (state == null || state.state == PlaybackState.STATE_NONE || state.state == PlaybackState.STATE_ERROR) null
                    else fraction(state.position, controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0,
                        state.lastPositionUpdateTime, state.playbackSpeed, state.state == PlaybackState.STATE_PLAYING,
                        SystemClock.elapsedRealtime())
                }
            }
        } catch (_: RuntimeException) { null }
    }
}
