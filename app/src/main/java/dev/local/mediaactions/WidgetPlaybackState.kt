package dev.local.mediaactions

/** Confirmed player state, never an optimistic response to a button tap. */
enum class WidgetPlaybackState {
    PLAYING, PAUSED, TRANSITIONING, UNKNOWN;

    companion object {
        fun read(gateway: SessionGateway): WidgetPlaybackState = try {
            if (!gateway.hasNotificationAccess()) UNKNOWN
            else when (val selection = MediaSessionSelector.select(gateway.snapshot())) {
                is Selection.Selected -> when (selection.session.state) {
                    SessionState.PLAYING -> PLAYING
                    SessionState.PAUSED, SessionState.STOPPED -> PAUSED
                    SessionState.TRANSITIONING -> TRANSITIONING
                    SessionState.UNAVAILABLE -> UNKNOWN
                }
                else -> UNKNOWN
            }
        } catch (_: RuntimeException) { UNKNOWN }
    }
}
