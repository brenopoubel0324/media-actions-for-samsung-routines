package dev.local.mediaactions

import android.content.Intent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 36])
class SpotifyFallbackTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private class Gateway(val sessions: List<PlayerSession> = emptyList(), val access: Boolean = true,
        val error: RuntimeException? = null) : SessionGateway {
        override fun hasNotificationAccess() = access
        override fun snapshot(): SessionSnapshot {
            error?.let { throw it }; return SessionSnapshot(sessions)
        }
    }
    private class Session(override val state: SessionState,
        override val supportedCommands: Set<TransportCommand> = TransportCommand.entries.toSet(),
        val error: RuntimeException? = null) : PlayerSession {
        override val packageName = "last.player"
        val sent = mutableListOf<TransportCommand>()
        override fun send(command: TransportCommand) { error?.let { throw it }; sent += command }
    }
    private fun execute(action: MediaAction, gateway: Gateway = Gateway()): ActionResult {
        var result: ActionResult? = null
        PhoneMediaActions(app, MediaActionDispatcher(gateway), SpotifyPlaybackResumer()).execute(action) { result = it }
        return requireNotNull(result)
    }
    @Test fun absentBrowserServiceReportsFailureInsteadOfClaimingBroadcastSuccess() {
        assertEquals(ActionResult.Failure, execute(MediaAction.PLAY_PAUSE))
        assertEquals(SpotifyPlaybackResumer.Status.NOT_AVAILABLE, SpotifyPlaybackResumer.lastStatus(app))
        assertTrue(shadowOf(app).broadcastIntents.none { it.action == Intent.ACTION_MEDIA_BUTTON })
        assertNull(shadowOf(app).nextStartedActivity)
    }
    @Test fun existingPausedAndPlayingSessionsKeepTheirOriginalCommands() {
        for ((state, command) in listOf(SessionState.PAUSED to TransportCommand.PLAY, SessionState.PLAYING to TransportCommand.PAUSE)) {
            val session = Session(state)
            assertEquals(ActionResult.CommandSent("last.player"), execute(MediaAction.PLAY_PAUSE, Gateway(listOf(session))))
            assertEquals(listOf(command), session.sent)
        }
        assertEquals(SpotifyPlaybackResumer.Status.IDLE, SpotifyPlaybackResumer.lastStatus(app))
    }
    @Test fun nextAndPreviousDoNotInvokeResumption() {
        for (action in listOf(MediaAction.NEXT, MediaAction.PREVIOUS)) assertEquals(ActionResult.NoActiveSession, execute(action))
        assertEquals(SpotifyPlaybackResumer.Status.IDLE, SpotifyPlaybackResumer.lastStatus(app))
    }
    @Test fun missingAccessAndLookupFailureDoNotInvokeResumption() {
        assertEquals(ActionResult.MissingNotificationAccess, execute(MediaAction.PLAY_PAUSE, Gateway(access = false)))
        assertEquals(ActionResult.Failure, execute(MediaAction.PLAY_PAUSE, Gateway(error = IllegalStateException())))
        assertEquals(SpotifyPlaybackResumer.Status.IDLE, SpotifyPlaybackResumer.lastStatus(app))
    }
    @Test fun ambiguityTransitionUnsupportedAndTransportFailureDoNotFallback() {
        val cases = listOf(
            Gateway(listOf(Session(SessionState.PAUSED), Session(SessionState.STOPPED))) to ActionResult.AmbiguousSession,
            Gateway(listOf(Session(SessionState.TRANSITIONING))) to ActionResult.UnsupportedAction,
            Gateway(listOf(Session(SessionState.PAUSED, emptySet()))) to ActionResult.UnsupportedAction,
            Gateway(listOf(Session(SessionState.PAUSED, error = IllegalStateException()))) to ActionResult.Failure)
        for ((gateway, result) in cases) assertEquals(result, execute(MediaAction.PLAY_PAUSE, gateway))
        assertEquals(SpotifyPlaybackResumer.Status.IDLE, SpotifyPlaybackResumer.lastStatus(app))
    }
    @Test fun statusQueryHasNoResumptionSideEffect() {
        val actions = PhoneMediaActions(app, MediaActionDispatcher(Gateway()), SpotifyPlaybackResumer())
        assertEquals(PlayerStatus.NoActiveSession, actions.playerStatus())
        assertEquals(SpotifyPlaybackResumer.Status.IDLE, SpotifyPlaybackResumer.lastStatus(app))
    }
}
