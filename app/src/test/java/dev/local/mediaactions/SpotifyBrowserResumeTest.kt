package dev.local.mediaactions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.media.browse.MediaBrowser
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Looper
import android.service.media.MediaBrowserService
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.time.Duration

@Implements(MediaBrowser::class)
class ResumeBrowserShadow {
    lateinit var component: ComponentName
    lateinit var callback: MediaBrowser.ConnectionCallback
    var hints: Bundle? = null
    var token: MediaSession.Token? = null
    var disconnected = false
    var connections = 0
    @Implementation fun __constructor__(context: Context, component: ComponentName, callback: MediaBrowser.ConnectionCallback, hints: Bundle?) {
        this.component = component; this.callback = callback; this.hints = hints; instances += this
    }
    @Implementation fun connect() { connections++ }
    @Implementation fun disconnect() { disconnected = true }
    @Implementation fun getSessionToken(): MediaSession.Token = requireNotNull(token)
    companion object { val instances = mutableListOf<ResumeBrowserShadow>() }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 36], shadows = [ResumeBrowserShadow::class])
class SpotifyBrowserResumeTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val browser get() = ResumeBrowserShadow.instances.last()
    @Before fun reset() { ResumeBrowserShadow.instances.clear() }
    private fun service(name: String = "spotify.PublicBrowser", exported: Boolean = true, enabled: Boolean = true,
        permission: String? = null, pkg: String = SpotifyPlaybackResumer.SPOTIFY) {
        shadowOf(app.packageManager).addResolveInfoForIntent(Intent(MediaBrowserService.SERVICE_INTERFACE).setPackage(SpotifyPlaybackResumer.SPOTIFY),
            ResolveInfo().apply { serviceInfo = ServiceInfo().apply {
                this.name = name; packageName = pkg; this.exported = exported; this.enabled = enabled; this.permission = permission
                applicationInfo = ApplicationInfo().apply { packageName = pkg; this.enabled = true }
            } })
    }
    private fun withSession(block: (MediaSession, SpotifyPlaybackResumer) -> Unit) {
        val session = MediaSession(app, "spotify-browser-test")
        shadowOf(session.controller).setPackageName(SpotifyPlaybackResumer.SPOTIFY)
        shadowOf(session.controller).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED, 0, 0f).build())
        val resumer = SpotifyPlaybackResumer { _, token -> assertEquals(session.sessionToken, token); session.controller }
        try { block(session, resumer) } finally { session.release() }
    }
    private fun connect(session: MediaSession) { browser.token = session.sessionToken; browser.callback.onConnected() }
    private fun timeout() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(SpotifyPlaybackResumer.TIMEOUT_MS))

    @Test fun bindsExplicitSpotifyBrowserAndWaitsForActualPlayingBeforeReportingSuccess() {
        service(); withSession { session, resumer ->
            val results = mutableListOf<SpotifyPlaybackResumer.Status>()
            resumer.resume(app, { PlayerStatus.NoActiveSession }, results::add)
            assertEquals(ComponentName(SpotifyPlaybackResumer.SPOTIFY, "spotify.PublicBrowser"), browser.component)
            assertTrue(browser.hints!!.getBoolean(MediaBrowserService.BrowserRoot.EXTRA_RECENT))
            assertEquals(SpotifyPlaybackResumer.Status.IDLE, SpotifyPlaybackResumer.lastStatus(app))
            connect(session)
            assertTrue(results.isEmpty()); assertFalse(browser.disconnected)
            assertEquals(PlaybackState.ACTION_PLAY, shadowOf(session.controller.transportControls).lastPerformedAction)
            shadowOf(session.controller).executeOnPlaybackStateChanged(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf(SpotifyPlaybackResumer.Status.PLAYING), results); assertTrue(browser.disconnected)
            assertTrue(shadowOf(session.controller).callbacks.isEmpty())
            timeout(); assertEquals(1, results.size)
            assertTrue(shadowOf(app).broadcastIntents.none { it.action == Intent.ACTION_MEDIA_BUTTON })
            assertNull(shadowOf(app).nextStartedActivity)
        }
    }
    @Test fun noConnectionTimesOutAndLateCallbackCannotSendPlay() {
        service(); withSession { session, resumer ->
            val results=mutableListOf<SpotifyPlaybackResumer.Status>()
            resumer.resume(app,{PlayerStatus.NoActiveSession},results::add); val old=browser
            timeout(); assertEquals(listOf(SpotifyPlaybackResumer.Status.TIMED_OUT),results); assertTrue(old.disconnected)
            old.token=session.sessionToken;old.callback.onConnected()
            assertNotEquals(PlaybackState.ACTION_PLAY,shadowOf(session.controller.transportControls).lastPerformedAction)
        }
    }
    @Test fun connectedWithoutPlaybackIsNotReportedAsSuccess() {
        service(); withSession { session,resumer ->
            val results=mutableListOf<SpotifyPlaybackResumer.Status>()
            resumer.resume(app,{PlayerStatus.NoActiveSession},results::add);connect(session);timeout()
            assertEquals(listOf(SpotifyPlaybackResumer.Status.NOT_PLAYING),results)
            assertEquals(SpotifyPlaybackResumer.Status.NOT_PLAYING,SpotifyPlaybackResumer.lastStatus(app))
            assertTrue(browser.disconnected);assertTrue(shadowOf(session.controller).callbacks.isEmpty())
        }
    }
    @Test fun deniedConnectionReturnsExplicitReasonAndReleasesTheAttempt() {
        service(); withSession { _,resumer ->
            val results=mutableListOf<SpotifyPlaybackResumer.Status>()
            resumer.resume(app,{PlayerStatus.NoActiveSession},results::add);browser.callback.onConnectionFailed()
            assertEquals(listOf(SpotifyPlaybackResumer.Status.REJECTED),results);assertTrue(browser.disconnected)
            resumer.resume(app,{PlayerStatus.NoActiveSession},results::add)
            assertEquals(2,ResumeBrowserShadow.instances.size);timeout()
        }
    }
    @Test fun permissionRevocationAndAPlayerStartingDuringConnectionCancelWithoutCommands() {
        for(status in listOf(PlayerStatus.MissingNotificationAccess,PlayerStatus.Selected("other.player"),PlayerStatus.AmbiguousSession)) {
            service();withSession { session,resumer ->
                val results=mutableListOf<SpotifyPlaybackResumer.Status>()
                resumer.resume(app,{status},results::add);connect(session)
                assertEquals(if(status==PlayerStatus.MissingNotificationAccess) SpotifyPlaybackResumer.Status.NO_ACCESS else SpotifyPlaybackResumer.Status.SESSION_CHANGED,results.single())
                assertNotEquals(PlaybackState.ACTION_PLAY,shadowOf(session.controller.transportControls).lastPerformedAction)
            }
        }
    }
    @Test fun onlyOneAttemptCanRunAndDuplicateConnectedCallbacksNeverReplayCommands() {
        service();withSession { session,resumer ->
            val results=mutableListOf<SpotifyPlaybackResumer.Status>()
            resumer.resume(app,{PlayerStatus.NoActiveSession},results::add)
            resumer.resume(app,{PlayerStatus.NoActiveSession},results::add)
            assertEquals(listOf(SpotifyPlaybackResumer.Status.BUSY),results);assertEquals(1,ResumeBrowserShadow.instances.size)
            connect(session)
            session.controller.transportControls.pause() // Detect a repeated play if callback is duplicated.
            browser.callback.onConnected()
            assertEquals(PlaybackState.ACTION_PAUSE,shadowOf(session.controller.transportControls).lastPerformedAction)
            timeout();assertEquals(2,results.size)
        }
    }
    @Test fun missingPrivateDisabledAndPermissionProtectedServicesCannotBeBound() {
        for(kind in 0..3) {
            if(kind>0) service(exported=kind!=1,enabled=kind!=2,permission=if(kind==3)"spotify.private.permission" else null)
            val results=mutableListOf<SpotifyPlaybackResumer.Status>()
            SpotifyPlaybackResumer().resume(app,{PlayerStatus.NoActiveSession},results::add)
            assertEquals(listOf(SpotifyPlaybackResumer.Status.NOT_AVAILABLE),results)
            assertTrue(ResumeBrowserShadow.instances.isEmpty())
        }
    }
    @Test fun multipleBrowserServicesAreNotAllConnectedTo() {
        service("spotify.One");service("spotify.Two")
        val results=mutableListOf<SpotifyPlaybackResumer.Status>()
        SpotifyPlaybackResumer().resume(app,{PlayerStatus.NoActiveSession},results::add)
        assertEquals(listOf(SpotifyPlaybackResumer.Status.NOT_AVAILABLE),results);assertTrue(ResumeBrowserShadow.instances.isEmpty())
    }
    @Test fun wrongPackageTokenIsRejectedBeforePlay() {
        service();withSession { session,resumer ->
            shadowOf(session.controller).setPackageName("other.package")
            val results=mutableListOf<SpotifyPlaybackResumer.Status>()
            resumer.resume(app,{PlayerStatus.NoActiveSession},results::add);connect(session)
            assertEquals(listOf(SpotifyPlaybackResumer.Status.REJECTED),results)
            assertNotEquals(PlaybackState.ACTION_PLAY,shadowOf(session.controller.transportControls).lastPerformedAction)
        }
    }
    @Test fun asyncPhoneActionWithoutAnySessionUsesBrowserAndOnlyCompletesAfterConfirmation() {
        service(); withSession { session, resumer ->
            val gateway = object : SessionGateway {
                override fun hasNotificationAccess() = true
                override fun snapshot() = SessionSnapshot(emptyList())
            }
            val results = mutableListOf<ActionResult>()
            PhoneMediaActions(app, MediaActionDispatcher(gateway), resumer).execute(MediaAction.PLAY_PAUSE, results::add)
            assertTrue(results.isEmpty()); connect(session); assertTrue(results.isEmpty())
            shadowOf(session.controller).executeOnPlaybackStateChanged(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf(ActionResult.CommandSent(SpotifyPlaybackResumer.SPOTIFY)), results)
        }
    }
    @Test fun suspendedConnectionAndPlaybackErrorsFailOnceWithoutLateRetry() {
        service(); withSession { session, resumer ->
            val results = mutableListOf<SpotifyPlaybackResumer.Status>()
            resumer.resume(app, { PlayerStatus.NoActiveSession }, results::add)
            browser.callback.onConnectionSuspended(); timeout()
            assertEquals(listOf(SpotifyPlaybackResumer.Status.REJECTED), results)
            resumer.resume(app, { PlayerStatus.NoActiveSession }, results::add); connect(session)
            shadowOf(session.controller).executeOnPlaybackStateChanged(PlaybackState.Builder().setState(PlaybackState.STATE_ERROR, 0, 0f).build())
            shadowOf(Looper.getMainLooper()).idle(); timeout()
            assertEquals(listOf(SpotifyPlaybackResumer.Status.REJECTED, SpotifyPlaybackResumer.Status.NOT_PLAYING), results)
            assertTrue(shadowOf(session.controller).callbacks.isEmpty())
        }
    }
    @Test fun widgetBroadcastRemainsPendingUntilBoundedAsyncAttemptCompletes() {
        service()
        shadowOf(app.getSystemService(android.app.NotificationManager::class.java)).setNotificationListenerAccessGranted(
            AndroidSessionGateway.listenerComponent(app), true)
        val manager = android.appwidget.AppWidgetManager.getInstance(app)
        val id = shadowOf(manager).createWidget(MediaWidgetProvider::class.java, R.layout.widget_media_controls)
        shadowOf(manager).addBoundWidget(id, android.appwidget.AppWidgetProviderInfo().apply {
            provider = ComponentName(app, MediaWidgetProvider::class.java)
        })
        val intent = Intent(shadowOf(MediaWidgetProvider.controlIntent(app, id, MediaAction.PLAY_PAUSE)).savedIntent).setComponent(null)
        val receiver = MediaWidgetProvider()
        app.registerReceiver(receiver, android.content.IntentFilter(intent.action).apply { addDataScheme("mediaactions") }, Context.RECEIVER_NOT_EXPORTED)
        try {
            app.sendBroadcast(intent); shadowOf(Looper.getMainLooper()).idle()
            assertTrue(shadowOf(receiver).wentAsync())
            val pending = requireNotNull(shadowOf(receiver).originalPendingResult)
            val pendingShadow = org.robolectric.shadow.api.Shadow.extract<org.robolectric.shadows.ShadowBroadcastPendingResult>(pending)
            assertFalse(pendingShadow.future.isDone)
            timeout(); assertTrue(pendingShadow.future.isDone)
            browser.callback.onConnectionFailed(); assertTrue(pendingShadow.future.isDone)
        } finally { app.unregisterReceiver(receiver) }
    }

    @Test fun completionFailureCannotLeakTheConnectionOrPreventTheNextAttempt() {
        service(); withSession { _, resumer ->
            resumer.resume(app, { PlayerStatus.NoActiveSession }) { throw IllegalStateException("Host callback failure") }
            browser.callback.onConnectionFailed(); assertTrue(browser.disconnected)
            val results = mutableListOf<SpotifyPlaybackResumer.Status>()
            resumer.resume(app, { PlayerStatus.NoActiveSession }, results::add)
            timeout(); assertEquals(listOf(SpotifyPlaybackResumer.Status.TIMED_OUT), results)
        }
    }
    @Test fun shortcutFinishesImmediatelyWhileTheApplicationContextOwnsTheConnection() {
        service()
        shadowOf(app.getSystemService(android.app.NotificationManager::class.java)).setNotificationListenerAccessGranted(
            AndroidSessionGateway.listenerComponent(app), true)
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("mediaactions://run/toggle"))
        org.robolectric.Robolectric.buildActivity(ActionActivity::class.java, intent).use { controller ->
            val activity = controller.create().get()
            assertTrue(activity.isFinishing); assertFalse(browser.disconnected)
            assertNull(shadowOf(activity).nextStartedActivity)
            timeout(); assertTrue(browser.disconnected)
            assertEquals(SpotifyPlaybackResumer.Status.TIMED_OUT, SpotifyPlaybackResumer.lastStatus(app))
        }
    }

    @Test fun delayedCallbackCannotSendPlayAfterDeadlineEvenBeforeTimeoutRunnableRuns() {
        service(); withSession { session, resumer ->
            val results = mutableListOf<SpotifyPlaybackResumer.Status>()
            resumer.resume(app, { PlayerStatus.NoActiveSession }, results::add)
            org.robolectric.shadows.ShadowSystemClock.advanceBy(Duration.ofMillis(SpotifyPlaybackResumer.TIMEOUT_MS + 1))
            connect(session)
            assertEquals(listOf(SpotifyPlaybackResumer.Status.TIMED_OUT), results)
            assertNotEquals(PlaybackState.ACTION_PLAY, shadowOf(session.controller.transportControls).lastPerformedAction)
            assertTrue(browser.disconnected)
            timeout(); assertEquals(1, results.size)
        }
    }

}
