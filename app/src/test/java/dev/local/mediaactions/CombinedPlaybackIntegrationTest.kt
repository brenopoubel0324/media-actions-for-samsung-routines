package dev.local.mediaactions

import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.widget.ImageView
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
@Config(sdk = [33, 36], shadows = [CountingSessionsShadow::class,
    CountingControllerShadow::class, CountingWidgetsShadow::class])
class CombinedPlaybackIntegrationTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val widgets get() = AppWidgetManager.getInstance(app)
    private lateinit var session: MediaSession
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun advance() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))
    private fun grant(value: Boolean) = shadowOf(app.getSystemService(NotificationManager::class.java))
        .setNotificationListenerAccessGranted(AndroidSessionGateway.listenerComponent(app), value)
    private fun state(value: Int) = PlaybackState.Builder().setState(value, 1000,
        if (value == PlaybackState.STATE_PLAYING) 1f else 0f, SystemClock.elapsedRealtime().coerceAtLeast(1))
        .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE).build()
    private fun options(width: Int, height: Int) = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
    }
    private fun widget(kind: WidgetKind, progress: Boolean = false, width: Int = 192, height: Int = 56): Int {
        val provider: Class<out AppWidgetProvider> = when (kind) {
            WidgetKind.MEDIA -> MediaWidgetProvider::class.java
            WidgetKind.VOLUME -> VolumeWidgetProvider::class.java
            WidgetKind.PLAYBACK_VOLUME -> PlaybackVolumeWidgetProvider::class.java
        }
        val id = shadowOf(widgets).createWidget(provider, R.layout.widget_media_controls)
        shadowOf(widgets).addBoundWidget(id, AppWidgetProviderInfo().apply { this.provider = ComponentName(app, provider) })
        WidgetStyleStore.save(app, WidgetAppearance(progressRing = progress, buttonBorder = true), id)
        widgets.updateAppWidgetOptions(id, options(width, height))
        EnergyCounters.tracked += id
        return id
    }
    private fun description(id: Int) = shadowOf(widgets).getViewFor(id)
        .findViewById<ImageView>(R.id.widget_toggle).contentDescription.toString()

    @Before fun setup() {
        EnergyCounters.tracked.clear(); EnergyCounters.clear()
        EnergyCounters.failSessionReads = 0; EnergyCounters.failUpdates = 0
        grant(true)
        shadowOf(app.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        EnergyCounters.state = state(PlaybackState.STATE_PAUSED)
        EnergyCounters.metadata = MediaMetadata.Builder().putLong(MediaMetadata.METADATA_KEY_DURATION, 300000).build()
        session = MediaSession(app, "combined-integration")
        shadowOf(session.controller).setPackageName("test.player")
        shadowOf(app.getSystemService(MediaSessionManager::class.java)).addController(session.controller)
    }
    @After fun cleanup() {
        session.release(); EnergyCounters.state = null; EnergyCounters.metadata = null
        EnergyCounters.tracked.clear(); EnergyCounters.failSessionReads = 0; EnergyCounters.failUpdates = 0
    }

    @Test fun mixedPlaybackBatchSharesOneSnapshotAndListenerDeduplicatesBothKinds() {
        val media = widget(WidgetKind.MEDIA)
        val combined = widget(WidgetKind.PLAYBACK_VOLUME)
        widget(WidgetKind.VOLUME)
        idle(); EnergyCounters.clear()
        WidgetRegistry.refreshPlayback(app)
        assertEquals(1, EnergyCounters.sessions)
        assertEquals(0, EnergyCounters.progress)
        assertEquals(2, EnergyCounters.updates)
        assertEquals(app.getString(R.string.widget_play_action), description(media))
        assertEquals(app.getString(R.string.widget_play_action), description(combined))
        WidgetPlaybackObserver(app).use { observer ->
            observer.start(); idle()
            assertEquals(1, shadowOf(session.controller).callbacks.size)
            EnergyCounters.clear()
            repeat(10) { shadowOf(session.controller).executeOnPlaybackStateChanged(EnergyCounters.state); idle() }
            assertEquals(0, EnergyCounters.sessions)
            assertEquals(0, EnergyCounters.progress)
            assertEquals(0, EnergyCounters.updates)
            EnergyCounters.state = state(PlaybackState.STATE_PLAYING)
            shadowOf(session.controller).executeOnPlaybackStateChanged(EnergyCounters.state); idle()
            assertEquals(2, EnergyCounters.updates)
            assertEquals(app.getString(R.string.widget_pause_action), description(combined))
        }
    }

    @Test fun unavailableCombinedDoesNotObserveOrTickAndResizeRecoversWithoutRecreation() {
        val combined = widget(WidgetKind.PLAYBACK_VOLUME, progress = true, width = 80, height = 80)
        idle(); EnergyCounters.clear()
        EnergyCounters.state = state(PlaybackState.STATE_PLAYING)
        var ticks = 0
        WidgetPlaybackObserver(app).use { observer ->
            observer.observeWork = { if (it == WidgetPlaybackObserver.Work.PERIODIC_TICK) ticks++ }
            observer.start(); idle(); advance()
            assertEquals(0, EnergyCounters.sessions)
            assertEquals(0, EnergyCounters.updates)
            assertEquals(0, ticks)
            assertTrue(shadowOf(session.controller).callbacks.isEmpty())
            widgets.updateAppWidgetOptions(combined, options(56, 192))
            WidgetPlaybackObserver.settingsChanged(app); idle()
            assertEquals(app.getString(R.string.widget_pause_action), description(combined))
            assertEquals(1, shadowOf(session.controller).callbacks.size)
            advance(); assertTrue(ticks > 0)
            app.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF)); idle()
            val stopped = ticks; EnergyCounters.clear(); advance()
            assertEquals(stopped, ticks)
            assertEquals(0, EnergyCounters.sessions)
            assertEquals(0, EnergyCounters.updates)
        }
    }

    @Test fun serviceDestructionClearsBothCentersWithoutSessionReadsAndReconnectReadsCurrentState() {
        val media = widget(WidgetKind.MEDIA, progress = true)
        val combined = widget(WidgetKind.PLAYBACK_VOLUME, progress = true)
        widget(WidgetKind.VOLUME)
        EnergyCounters.state = state(PlaybackState.STATE_PLAYING)
        val service = Robolectric.buildService(MediaNotificationListener::class.java).create()
        service.get().onListenerConnected(); idle()
        val old = shadowOf(session.controller).callbacks.single()
        EnergyCounters.clear(); service.destroy(); idle()
        assertEquals(0, EnergyCounters.sessions)
        assertEquals(app.getString(R.string.widget_state_unknown), description(media))
        assertEquals(app.getString(R.string.widget_state_unknown), description(combined))
        EnergyCounters.clear()
        old.onPlaybackStateChanged(state(PlaybackState.STATE_PLAYING)); advance()
        assertEquals(0, EnergyCounters.updates)
        grant(false)
        WidgetRegistry.refreshPlayback(app)
        assertEquals(0, EnergyCounters.sessions)
        grant(true); EnergyCounters.state = state(PlaybackState.STATE_PAUSED)
        val restarted = Robolectric.buildService(MediaNotificationListener::class.java).create()
        try {
            restarted.get().onListenerConnected(); idle()
            assertEquals(app.getString(R.string.widget_play_action), description(media))
            assertEquals(app.getString(R.string.widget_play_action), description(combined))
            assertEquals(1, shadowOf(session.controller).callbacks.size)
        } finally { restarted.destroy() }
    }

    @Test fun teardownFailureForOneHostStillClearsTheOtherPlaybackConsumer() {
        widget(WidgetKind.MEDIA, progress = true)
        val combined = widget(WidgetKind.PLAYBACK_VOLUME, progress = true)
        EnergyCounters.state = state(PlaybackState.STATE_PLAYING)
        val service = Robolectric.buildService(MediaNotificationListener::class.java).create()
        service.get().onListenerConnected(); idle()
        assertEquals(app.getString(R.string.widget_pause_action), description(combined))
        EnergyCounters.clear(); EnergyCounters.failUpdates = 1
        service.destroy(); idle()
        assertEquals(app.getString(R.string.widget_state_unknown), description(combined))
        assertEquals(0, EnergyCounters.sessions)
        // Framework teardown can signal both disconnection and destruction.
        assertEquals(0, EnergyCounters.failUpdates)
        assertTrue(EnergyCounters.updates >= 1)
        EnergyCounters.clear(); advance()
        assertEquals(0, EnergyCounters.updates)
        assertTrue(shadowOf(session.controller).callbacks.isEmpty())
    }

    @Test fun cachedDeliveryFailureDoesNotStarveAnotherKindOrRedeliverSuccessfulViews() {
        val media = widget(WidgetKind.MEDIA)
        val combined = widget(WidgetKind.PLAYBACK_VOLUME)
        WidgetPlaybackObserver(app).use { observer ->
            observer.start(); idle()
            EnergyCounters.state = state(PlaybackState.STATE_PLAYING)
            EnergyCounters.clear(); EnergyCounters.failUpdates = 1
            shadowOf(session.controller).executeOnPlaybackStateChanged(EnergyCounters.state); idle()
            assertEquals(app.getString(R.string.widget_pause_action), description(combined))
            assertEquals(app.getString(R.string.widget_play_action), description(media))
            assertEquals(1, EnergyCounters.updates)
            EnergyCounters.clear()
            shadowOf(session.controller).executeOnPlaybackStateChanged(EnergyCounters.state); idle()
            assertEquals(app.getString(R.string.widget_pause_action), description(media))
            assertEquals(1, EnergyCounters.updates)
            assertEquals(0, EnergyCounters.sessions)
        }
    }
}
