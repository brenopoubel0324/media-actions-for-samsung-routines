package dev.local.mediaactions

import android.content.Context
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
        internal fun fromPosition(position: SessionPosition): WidgetTrackProgress? = with(position) {
            fraction(this.position, duration, updated, speed, advancing, SystemClock.elapsedRealtime())
        }
        fun read(context: Context): WidgetTrackProgress? = try {
            val gateway = AndroidSessionGateway(context)
            if (!gateway.hasNotificationAccess()) null else {
                val selected = MediaSessionSelector.select(gateway.snapshot()) as? Selection.Selected
                selected?.session?.let(AndroidSessionGateway::position)?.let(::fromPosition)
            }
        } catch (_: RuntimeException) { null }
    }
}
