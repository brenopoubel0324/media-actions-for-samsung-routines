package dev.local.mediaactions

import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Looper
import android.os.PowerManager
import android.widget.ImageButton
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 36])
class WidgetProgressReviewTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val widgets get() = AppWidgetManager.getInstance(app)
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun advance() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(15))
    private fun widget(progress: Boolean): Int {
        val id = shadowOf(widgets).createWidget(MediaWidgetProvider::class.java, R.layout.widget_media_controls)
        shadowOf(widgets).addBoundWidget(id, AppWidgetProviderInfo().apply {
            provider = ComponentName(app, MediaWidgetProvider::class.java)
        })
        WidgetStyleStore.save(app, WidgetAppearance(progressRing = progress), id)
        return id
    }
    private fun player(): MediaSession {
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationListenerAccessGranted(
            AndroidSessionGateway.listenerComponent(app), true)
        shadowOf(app.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        return MediaSession(app, "review-no-duration").also {
            shadowOf(it.controller).setPackageName("test.player")
            shadowOf(it.controller).setPlaybackState(PlaybackState.Builder()
                .setState(PlaybackState.STATE_PLAYING, 1000, 1f).build())
            shadowOf(app.getSystemService(MediaSessionManager::class.java)).addController(it.controller)
        }
    }
    private fun withPlayer(block: (MediaSession) -> Unit) {
        val session = player()
        try { block(session) } finally { session.release() }
    }
    private fun center(id: Int) = shadowOf(widgets).getViewFor(id).findViewById<ImageButton>(R.id.widget_toggle)

    @Test fun unknownDurationDoesNotKeepRedrawingTheTrackOnATimer() {
        widget(true)
        withPlayer { session ->
            assertNull(session.controller.metadata)
            var count = 0
            WidgetPlaybackObserver(app) { count++ }.use { observer ->
                observer.start(); idle(); assertEquals(1, count)
                advance(); assertEquals("No calculable progress should mean no periodic updates", 1, count)
            }
        }
    }

    @Test fun progressCallbackLeavesPlainWidgetUntouchedButStateChangeStillUpdatesBoth() {
        val ring = widget(true)
        val plain = widget(false)
        withPlayer { session ->
            WidgetPlaybackObserver(app).use { observer ->
                observer.start(); idle()
                center(plain).contentDescription = "plain widget untouched"
                center(ring).contentDescription = "ring awaiting progress update"
                shadowOf(session.controller).executeOnMetadataChanged(MediaMetadata.Builder()
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, 10000).build())
                idle()
                assertEquals("plain widget untouched", center(plain).contentDescription)
                assertEquals("Pause", center(ring).contentDescription)
                shadowOf(session.controller).executeOnPlaybackStateChanged(PlaybackState.Builder()
                    .setState(PlaybackState.STATE_PAUSED, 1000, 0f).build())
                idle()
                assertEquals("Play", center(plain).contentDescription)
                assertEquals("Play", center(ring).contentDescription)
            }
        }
    }
}
