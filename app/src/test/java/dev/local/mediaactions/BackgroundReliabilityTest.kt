package dev.local.mediaactions

import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.widget.ImageButton
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33,36], shadows=[CountingSessionsShadow::class,CountingControllerShadow::class,CountingWidgetsShadow::class])
class BackgroundReliabilityTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private val widgets get()=AppWidgetManager.getInstance(app)
    private lateinit var session:MediaSession
    private var id=0
    private fun idle()=shadowOf(Looper.getMainLooper()).idle()
    private fun advance(seconds:Long=5)=shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds))
    private fun grant(value:Boolean)=shadowOf(app.getSystemService(NotificationManager::class.java))
        .setNotificationListenerAccessGranted(AndroidSessionGateway.listenerComponent(app),value)
    private fun state(value:Int,position:Long=1000)=PlaybackState.Builder().setState(value,position,
        if(value==PlaybackState.STATE_PLAYING)1f else 0f,SystemClock.elapsedRealtime())
        .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE).build()
    private fun center()=shadowOf(widgets).getViewFor(id).findViewById<ImageButton>(R.id.widget_toggle)
    @Before fun setup() {
        EnergyCounters.tracked.clear();EnergyCounters.clear();EnergyCounters.failSessionReads=0;EnergyCounters.failUpdates=0
        grant(true)
        shadowOf(app.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        EnergyCounters.state=state(PlaybackState.STATE_PLAYING)
        EnergyCounters.metadata=MediaMetadata.Builder().putLong(MediaMetadata.METADATA_KEY_DURATION,300000).build()
        session=MediaSession(app,"background-reliability")
        shadowOf(session.controller).setPackageName("test.player")
        shadowOf(app.getSystemService(MediaSessionManager::class.java)).addController(session.controller)
        id=shadowOf(widgets).createWidget(MediaWidgetProvider::class.java,R.layout.widget_media_controls)
        shadowOf(widgets).addBoundWidget(id,AppWidgetProviderInfo().apply {provider=ComponentName(app,MediaWidgetProvider::class.java)})
        WidgetStyleStore.save(app,WidgetAppearance(progressRing=true),id)
        EnergyCounters.tracked+=id
    }
    @After fun cleanup() {session.release();EnergyCounters.state=null;EnergyCounters.metadata=null;EnergyCounters.tracked.clear();EnergyCounters.failSessionReads=0;EnergyCounters.failUpdates=0}

    @Test fun tickDetectsRevocationAndScreenEventRecoversWithoutARetryTimerOrOldCallback() {
        WidgetPlaybackObserver(app).use {observer ->
            observer.start();idle();assertEquals("Pause",center().contentDescription)
            val old=shadowOf(session.controller).callbacks.single()
            grant(false);advance()
            assertTrue(center().contentDescription.contains("unavailable"))
            assertTrue(shadowOf(session.controller).callbacks.isEmpty())
            EnergyCounters.clear();advance(60)
            assertEquals(0,EnergyCounters.sessions);assertEquals(0,EnergyCounters.updates)
            grant(true);app.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON));idle()
            assertEquals("Pause",center().contentDescription)
            assertEquals(1,shadowOf(session.controller).callbacks.size)
            EnergyCounters.clear()
            old.onPlaybackStateChanged(state(PlaybackState.STATE_PAUSED));old.onSessionDestroyed();idle()
            assertEquals("Pause",center().contentDescription)
            assertEquals(0,EnergyCounters.sessions);assertEquals(0,EnergyCounters.updates)
            advance();assertEquals(1,EnergyCounters.updates)
        }
    }
    @Test fun serviceDestructionCancelsPendingWorkAndNewServiceReadsCurrentState() {
        val service=Robolectric.buildService(MediaNotificationListener::class.java).create()
        service.get().onListenerConnected();idle()
        val old=shadowOf(session.controller).callbacks.single()
        service.destroy();idle()
        assertTrue(center().contentDescription.contains("unavailable"))
        assertTrue(shadowOf(session.controller).callbacks.isEmpty())
        EnergyCounters.clear()
        old.onPlaybackStateChanged(state(PlaybackState.STATE_PLAYING));advance(60)
        assertEquals(0,EnergyCounters.sessions);assertEquals(0,EnergyCounters.updates)
        EnergyCounters.state=state(PlaybackState.STATE_PAUSED)
        val restarted=Robolectric.buildService(MediaNotificationListener::class.java).create()
        try { restarted.get().onListenerConnected();idle();assertEquals("Play",center().contentDescription)
            assertEquals(1,shadowOf(session.controller).callbacks.size)
        } finally {restarted.destroy()}
    }
    @Test fun commandsReadLiveStateAndPermissionEvenWhenPresentationCacheDiffers() {
        EnergyCounters.state=state(PlaybackState.STATE_PAUSED)
        WidgetPlaybackObserver(app).use {observer ->
            observer.start();idle();assertEquals("Play",center().contentDescription)
            // Framework state changes before its callback reaches the presentation observer.
            EnergyCounters.state=state(PlaybackState.STATE_PLAYING)
            EnergyCounters.clear()
            val dispatcher=MediaActionDispatcher(AndroidSessionGateway(app))
            assertTrue(dispatcher.execute(MediaAction.PLAY_PAUSE) is ActionResult.CommandSent)
            assertEquals(1,EnergyCounters.sessions)
            assertEquals(PlaybackState.ACTION_PAUSE,shadowOf(session.controller.transportControls).lastPerformedAction)
            grant(false);EnergyCounters.clear()
            assertEquals(ActionResult.MissingNotificationAccess,dispatcher.execute(MediaAction.PLAY_PAUSE))
            assertEquals(0,EnergyCounters.sessions)
        }
    }
    @Test fun screenOffBroadcastWinsOverStaleInteractiveFlagAndCallbacksDoNotRestartTicks() {
        WidgetPlaybackObserver(app).use {observer ->
            observer.start();idle()
            app.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF));idle()
            shadowOf(session.controller).executeOnPlaybackStateChanged(EnergyCounters.state);idle()
            EnergyCounters.clear();advance(60)
            assertEquals(0,EnergyCounters.sessions);assertEquals(0,EnergyCounters.updates)
            app.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON));idle();EnergyCounters.clear();advance()
            assertEquals(1,EnergyCounters.updates);assertEquals(0,EnergyCounters.sessions)
        }
    }
    @Test fun finishedProjectionStopsTimerUntilPlaybackEventMakesProgressPossibleAgain() {
        EnergyCounters.state=state(PlaybackState.STATE_PLAYING,299000)
        var ticks=0
        WidgetPlaybackObserver(app).use {observer ->
            observer.observeWork={if(it==WidgetPlaybackObserver.Work.PERIODIC_TICK)ticks++}
            observer.start();idle();advance(60);assertEquals(1,ticks)
            EnergyCounters.state=state(PlaybackState.STATE_PLAYING,1000)
            shadowOf(session.controller).executeOnPlaybackStateChanged(EnergyCounters.state);idle()
            advance();assertEquals(2,ticks)
        }
    }
    @Test fun transientSessionReadFailureRecoversFromNextCallbackWithoutPolling() {
        WidgetPlaybackObserver(app).use {observer ->
            observer.start();idle()
            EnergyCounters.failSessionReads=1
            app.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON));idle()
            assertTrue(center().contentDescription.contains("unavailable"))
            assertEquals(1,shadowOf(session.controller).callbacks.size)
            EnergyCounters.clear();advance(60)
            assertEquals(0,EnergyCounters.sessions)
            shadowOf(session.controller).executeOnPlaybackStateChanged(EnergyCounters.state);idle()
            assertEquals("Pause",center().contentDescription)
            assertEquals(1,EnergyCounters.sessions)
        }
    }
    @Test fun failedConfigurationDeliveryIsRetriedOnSameStateEventWithoutProgress() {
        EnergyCounters.state=state(PlaybackState.STATE_PAUSED)
        WidgetPlaybackObserver(app).use {observer ->
            observer.start();idle()
            WidgetStyleStore.save(app,WidgetAppearance(progressRing=false,darkIcons=true),id)
            EnergyCounters.failUpdates=1
            WidgetPlaybackObserver.settingsChanged(app);idle()
            EnergyCounters.clear()
            shadowOf(session.controller).executeOnPlaybackStateChanged(EnergyCounters.state);idle()
            assertEquals(1,EnergyCounters.updates)
            EnergyCounters.clear()
            shadowOf(session.controller).executeOnPlaybackStateChanged(EnergyCounters.state);idle()
            assertEquals(0,EnergyCounters.updates)
        }
    }
    @Test fun explicitProviderRefreshCannotLeavePlainWidgetWithAStalePresentationKey() {
        EnergyCounters.state=state(PlaybackState.STATE_PAUSED)
        WidgetStyleStore.save(app,WidgetAppearance(progressRing=false),id)
        WidgetPlaybackObserver(app).use {observer ->
            observer.start();idle();assertEquals("Play",center().contentDescription)
            EnergyCounters.state=state(PlaybackState.STATE_PLAYING)
            MediaWidgetProvider.refreshAll(app);idle()
            assertEquals("Pause",center().contentDescription)
            EnergyCounters.state=state(PlaybackState.STATE_PAUSED)
            shadowOf(session.controller).executeOnPlaybackStateChanged(EnergyCounters.state);idle()
            assertEquals("Play",center().contentDescription)
        }
    }
    @Test fun destroyedSessionCannotRemainSelectedEvenIfFrameworkListIsTemporarilyStale() {
        WidgetPlaybackObserver(app).use {observer ->
            observer.start();idle()
            val old=shadowOf(session.controller).callbacks.single()
            old.onSessionDestroyed();idle()
            assertTrue(center().contentDescription.contains("unavailable"))
            app.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON));idle()
            assertTrue(center().contentDescription.contains("unavailable"))
            old.onPlaybackStateChanged(EnergyCounters.state);idle();advance(60)
            assertTrue(center().contentDescription.contains("unavailable"))
        }
    }
}
