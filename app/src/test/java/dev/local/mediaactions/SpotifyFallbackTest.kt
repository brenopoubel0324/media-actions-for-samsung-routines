package dev.local.mediaactions

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.view.KeyEvent
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
            error?.let { throw it }
            return SessionSnapshot(sessions)
        }
    }
    private class Session(override val state: SessionState,
        override val supportedCommands: Set<TransportCommand> = TransportCommand.entries.toSet(),
        val error: RuntimeException? = null) : PlayerSession {
        override val packageName = "last.player"
        val sent = mutableListOf<TransportCommand>()
        override fun send(command: TransportCommand) { error?.let { throw it }; sent += command }
    }
    private fun actions(gateway: Gateway = Gateway()) = PhoneMediaActions(app, MediaActionDispatcher(gateway))
    private fun receiver(name: String = "test.MediaReceiver", exported: Boolean = true,
        enabled: Boolean = true, permission: String? = null, pkg: String = PhoneMediaActions.SPOTIFY) {
        val resolve = ResolveInfo().apply { activityInfo = ActivityInfo().apply {
            this.name = name; packageName = pkg; this.exported = exported; this.enabled = enabled
            this.permission = permission
            applicationInfo = ApplicationInfo().apply { packageName = pkg; this.enabled = true }
        } }
        shadowOf(app.packageManager).addResolveInfoForIntent(
            Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(PhoneMediaActions.SPOTIFY), resolve)
    }
    private fun broadcasts() = shadowOf(app).broadcastIntents.filter { it.action == Intent.ACTION_MEDIA_BUTTON }

    @Test fun noSessionSendsOneExplicitPlayGestureOnlyToSpotifyWithoutActivity() {
        receiver()
        assertEquals(ActionResult.CommandSent(PhoneMediaActions.SPOTIFY), actions().execute(MediaAction.PLAY_PAUSE))
        val intents = broadcasts()
        assertEquals(2, intents.size)
        val events = intents.map {
            assertEquals(PhoneMediaActions.SPOTIFY, it.component!!.packageName)
            assertEquals("test.MediaReceiver", it.component!!.className)
            assertEquals(PhoneMediaActions.SPOTIFY, it.`package`)
            it.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)!!
        }
        assertEquals(listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP), events.map { it.action })
        assertTrue(events.all { it.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY && it.repeatCount == 0 })
        assertEquals(events[0].downTime, events[1].downTime)
        assertNull(shadowOf(app).nextStartedActivity)
    }
    @Test fun existingPausedSessionResumesInsteadOfSpotify() {
        receiver(); val session = Session(SessionState.PAUSED)
        assertEquals(ActionResult.CommandSent("last.player"), actions(Gateway(listOf(session))).execute(MediaAction.PLAY_PAUSE))
        assertEquals(listOf(TransportCommand.PLAY), session.sent); assertTrue(broadcasts().isEmpty())
    }
    @Test fun existingPlayingSessionPausesInsteadOfSpotify() {
        receiver(); val session = Session(SessionState.PLAYING)
        actions(Gateway(listOf(session))).execute(MediaAction.PLAY_PAUSE)
        assertEquals(listOf(TransportCommand.PAUSE), session.sent); assertTrue(broadcasts().isEmpty())
    }
    @Test fun unavailableSessionAllowsFallback() {
        receiver(); val session = Session(SessionState.UNAVAILABLE)
        assertEquals(ActionResult.CommandSent(PhoneMediaActions.SPOTIFY), actions(Gateway(listOf(session))).execute(MediaAction.PLAY_PAUSE))
        assertTrue(session.sent.isEmpty())
    }
    @Test fun nextAndPreviousNeverStartSpotify() {
        receiver()
        for (action in listOf(MediaAction.NEXT, MediaAction.PREVIOUS))
            assertEquals(ActionResult.NoActiveSession, actions().execute(action))
        assertTrue(broadcasts().isEmpty())
    }
    @Test fun absentOrPrivateReceiverDoesNotClaimDelivery() {
        assertEquals(ActionResult.NoActiveSession, actions().execute(MediaAction.PLAY_PAUSE))
        receiver(exported = false)
        assertEquals(ActionResult.NoActiveSession, actions().execute(MediaAction.PLAY_PAUSE))
        assertTrue(broadcasts().isEmpty())
    }
    @Test fun disabledReceiverIsIgnored() {
        receiver(enabled = false)
        assertEquals(ActionResult.NoActiveSession, actions().execute(MediaAction.PLAY_PAUSE)); assertTrue(broadcasts().isEmpty())
    }
    @Test fun receiverWithUnavailablePermissionIsIgnored() {
        receiver(permission = "spotify.private.PERMISSION")
        assertEquals(ActionResult.NoActiveSession, actions().execute(MediaAction.PLAY_PAUSE)); assertTrue(broadcasts().isEmpty())
    }
    @Test fun ambiguousReceiversAreNotAllBroadcastTo() {
        receiver("test.First"); receiver("test.Second")
        assertEquals(ActionResult.NoActiveSession, actions().execute(MediaAction.PLAY_PAUSE)); assertTrue(broadcasts().isEmpty())
    }
    @Test fun missingAccessAndLookupFailureDoNotFallback() {
        receiver()
        assertEquals(ActionResult.MissingNotificationAccess, actions(Gateway(access = false)).execute(MediaAction.PLAY_PAUSE))
        assertEquals(ActionResult.Failure, actions(Gateway(error = IllegalStateException())).execute(MediaAction.PLAY_PAUSE))
        assertTrue(broadcasts().isEmpty())
    }
    @Test fun ambiguityTransitionUnsupportedAndCommandFailureDoNotFallback() {
        receiver()
        val cases = listOf(
            Gateway(listOf(Session(SessionState.PAUSED), Session(SessionState.STOPPED))) to ActionResult.AmbiguousSession,
            Gateway(listOf(Session(SessionState.TRANSITIONING))) to ActionResult.UnsupportedAction,
            Gateway(listOf(Session(SessionState.PAUSED, emptySet()))) to ActionResult.UnsupportedAction,
            Gateway(listOf(Session(SessionState.PAUSED, error = IllegalStateException()))) to ActionResult.Failure)
        for ((gateway, result) in cases) assertEquals(result, actions(gateway).execute(MediaAction.PLAY_PAUSE))
        assertTrue(broadcasts().isEmpty())
    }
    @Test fun statusReadHasNoSideEffectsOrInventedSpotifyState() {
        receiver(); assertEquals(PlayerStatus.NoActiveSession, actions().playerStatus()); assertTrue(broadcasts().isEmpty())
    }
}
