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
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33,36])
class WidgetProgressObserverTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private fun idle()=shadowOf(Looper.getMainLooper()).idle()
    private fun advance()=shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
    private fun widget(): Int {
        val m=AppWidgetManager.getInstance(app);val id=shadowOf(m).createWidget(MediaWidgetProvider::class.java,R.layout.widget_media_controls)
        shadowOf(m).addBoundWidget(id,AppWidgetProviderInfo().apply { provider=ComponentName(app,MediaWidgetProvider::class.java) })
        WidgetStyleStore.save(app,WidgetAppearance(progressRing=true),id);return id
    }
    private fun setupPlayer(): MediaSession {
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationListenerAccessGranted(AndroidSessionGateway.listenerComponent(app),true)
        shadowOf(app.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        return MediaSession(app,"progress").also { session ->
            shadowOf(session.controller).setPackageName("test.player")
            shadowOf(session.controller).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING,1000,1f).build())
            shadowOf(app.getSystemService(MediaSessionManager::class.java)).addController(session.controller)
        }
    }
    @Test fun tickRunsOnlyForOptedInWidgetsAndStopsWhenDisabledOrClosed() {
        val id=widget();val session=setupPlayer();var count=0
        try {
            val observer=WidgetPlaybackObserver(app, progressReader = { WidgetTrackProgress(0.1f) }){count++};observer.start();idle();assertEquals(1,count)
            advance();assertEquals(2,count)
            WidgetStyleStore.save(app,WidgetAppearance(progressRing=false),id);WidgetPlaybackObserver.settingsChanged(app);idle()
            val stopped=count;advance();advance();assertEquals(stopped,count)
            observer.close();advance();assertEquals(stopped,count);assertTrue(shadowOf(session.controller).callbacks.isEmpty())
        } finally { session.release() }
    }
    @Test fun screenOffStopsTicksAndScreenOnResumesThem() {
        widget();val session=setupPlayer();var count=0
        try { WidgetPlaybackObserver(app, progressReader = { WidgetTrackProgress(0.1f) }){count++}.use { observer ->
            observer.start();idle();advance();assertEquals(2,count)
            shadowOf(app.getSystemService(PowerManager::class.java)).setIsInteractive(false)
            app.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF));idle();val stopped=count
            advance();advance();assertEquals(stopped,count)
            shadowOf(app.getSystemService(PowerManager::class.java)).setIsInteractive(true)
            app.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON));idle();assertEquals(stopped+1,count)
            advance();assertEquals(stopped+2,count)
        } } finally { session.release() }
    }
    @Test fun seekAndDurationCallbacksRefreshEvenIfPlayingStateDoesNotChangeAndPauseStopsTicks() {
        widget();val session=setupPlayer();var count=0
        try { WidgetPlaybackObserver(app, progressReader = { WidgetTrackProgress(0.1f) }){count++}.use { observer ->
            observer.start();idle()
            shadowOf(session.controller).executeOnMetadataChanged(MediaMetadata.Builder().putLong(MediaMetadata.METADATA_KEY_DURATION,10000).build());idle();assertEquals(2,count)
            shadowOf(session.controller).executeOnPlaybackStateChanged(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING,7000,1f).build());idle();assertEquals(3,count)
            shadowOf(session.controller).executeOnPlaybackStateChanged(PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED,7000,0f).build());idle();assertEquals(4,count)
            advance();advance();assertEquals(4,count)
        } } finally { session.release() }
    }
    @Test fun durationArrivingStartsTicksAndLosingDurationStopsThemAgain() {
        widget(); val session = setupPlayer(); var count = 0
        var progress: WidgetTrackProgress? = null
        try { WidgetPlaybackObserver(app, progressReader = { progress }) { count++ }.use { observer ->
            observer.start(); idle(); assertEquals(1, count)
            advance(); assertEquals(1, count)
            progress = WidgetTrackProgress(0.4f)
            shadowOf(session.controller).executeOnMetadataChanged(MediaMetadata.Builder()
                .putLong(MediaMetadata.METADATA_KEY_DURATION, 10000).build())
            idle(); assertEquals(2, count)
            advance(); assertEquals(3, count)
            progress = null
            shadowOf(session.controller).executeOnMetadataChanged(MediaMetadata.Builder().build())
            idle(); assertEquals(4, count)
            advance(); advance(); assertEquals(4, count)
        } } finally { session.release() }
    }

}
