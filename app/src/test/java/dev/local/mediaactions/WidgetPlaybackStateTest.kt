package dev.local.mediaactions

import org.junit.Assert.*
import org.junit.Test

class WidgetPlaybackStateTest {
    private class Player(override val state: SessionState, override val packageName: String = "player") : PlayerSession {
        override val supportedCommands = TransportCommand.entries.toSet()
        override fun send(command: TransportCommand) = error("State queries must never send commands")
    }

    private fun gateway(snapshot: SessionSnapshot, access: Boolean = true) = object : SessionGateway {
        override fun hasNotificationAccess() = access
        override fun snapshot() = snapshot
    }

    @Test fun mapsOnlyConfirmedStatesToPlayOrPause() {
        for ((source, expected) in listOf(SessionState.PLAYING to WidgetPlaybackState.PLAYING,
            SessionState.PAUSED to WidgetPlaybackState.PAUSED, SessionState.STOPPED to WidgetPlaybackState.PAUSED,
            SessionState.TRANSITIONING to WidgetPlaybackState.TRANSITIONING,
            SessionState.UNAVAILABLE to WidgetPlaybackState.UNKNOWN)) {
            assertEquals(expected, WidgetPlaybackState.read(gateway(SessionSnapshot(listOf(Player(source))))))
        }
    }

    @Test fun absentAmbiguousAndUnauthorizedSessionsStayUnknown() {
        assertEquals(WidgetPlaybackState.UNKNOWN, WidgetPlaybackState.read(gateway(SessionSnapshot(emptyList()))))
        val players = listOf(Player(SessionState.PAUSED, "a"), Player(SessionState.PAUSED, "b"))
        assertEquals(WidgetPlaybackState.UNKNOWN, WidgetPlaybackState.read(gateway(SessionSnapshot(players))))
        assertEquals(WidgetPlaybackState.UNKNOWN, WidgetPlaybackState.read(gateway(
            SessionSnapshot(listOf(Player(SessionState.PLAYING))), access = false)))
    }

    @Test fun stateUsesTheSameFrameworkTargetAsCommandDispatch() {
        val playing = Player(SessionState.PLAYING, "a")
        val paused = Player(SessionState.PAUSED, "b")
        assertEquals(WidgetPlaybackState.PAUSED, WidgetPlaybackState.read(gateway(
            SessionSnapshot(listOf(playing, paused), mediaKeySession = paused))))
        assertEquals(WidgetPlaybackState.PAUSED, WidgetPlaybackState.read(gateway(
            SessionSnapshot(listOf(playing, paused), mediaKeyPackage = "b"))))
    }

    @Test fun revokedAccessAndBinderFailuresDoNotExposeAStaleState() {
        for (failure in listOf(SecurityException(), IllegalStateException())) {
            val broken = object : SessionGateway {
                override fun hasNotificationAccess() = true
                override fun snapshot(): SessionSnapshot = throw failure
            }
            assertEquals(WidgetPlaybackState.UNKNOWN, WidgetPlaybackState.read(broken))
        }
    }
}
