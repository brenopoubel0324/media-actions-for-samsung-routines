package dev.local.mediaactions

/** Ephemeral data shared only within one rendering batch, including an explicitly unavailable progress. */
data class WidgetUpdateSnapshot(val state: WidgetPlaybackState, val progress: WidgetTrackProgress?) {
    companion object {
        internal fun capture(
            gateway: SessionGateway,
            needProgress: Boolean,
            forcedState: WidgetPlaybackState? = null,
            positionReader: (PlayerSession) -> SessionPosition? = AndroidSessionGateway::position,
        ): WidgetUpdateSnapshot {
            // Listener disconnection is authoritative even while the framework grant still exists.
            if (forcedState == WidgetPlaybackState.UNKNOWN) return WidgetUpdateSnapshot(forcedState, null)
            return try {
                if (!gateway.hasNotificationAccess()) return WidgetUpdateSnapshot(WidgetPlaybackState.UNKNOWN, null)
                val selected = MediaSessionSelector.select(gateway.snapshot()) as? Selection.Selected
                    ?: return WidgetUpdateSnapshot(WidgetPlaybackState.UNKNOWN, null)
                val state = WidgetPlaybackState.fromSessionState(selected.session.state)
                val progress = if (needProgress && state == WidgetPlaybackState.PLAYING)
                    positionReader(selected.session)?.let(WidgetTrackProgress::fromPosition) else null
                WidgetUpdateSnapshot(state, progress)
            } catch (_: RuntimeException) { WidgetUpdateSnapshot(WidgetPlaybackState.UNKNOWN, null) }
        }
    }
}
