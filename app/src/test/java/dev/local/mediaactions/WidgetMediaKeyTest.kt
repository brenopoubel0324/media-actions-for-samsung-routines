package dev.local.mediaactions

import android.app.NotificationManager
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowMediaSessionManager
import java.util.concurrent.Executor

/** Supplies the API 33 media-key listener, not yet simulated by Robolectric's stock shadow. */
@Implements(MediaSessionManager::class)
class MediaKeyManagerShadow : ShadowMediaSessionManager() {
    private var target: MediaController? = null
    val listeners = mutableMapOf<MediaSessionManager.OnMediaKeyEventSessionChangedListener, Executor>()

    @Implementation
    protected fun getMediaKeyEventSession(): MediaSession.Token? = target?.sessionToken

    @Implementation
    protected fun getMediaKeyEventSessionPackageName(): String = target?.packageName.orEmpty()

    @Implementation
    protected fun addOnMediaKeyEventSessionChangedListener(executor: Executor,
        listener: MediaSessionManager.OnMediaKeyEventSessionChangedListener) {
        listeners[listener] = executor
    }

    @Implementation
    protected fun removeOnMediaKeyEventSessionChangedListener(
        listener: MediaSessionManager.OnMediaKeyEventSessionChangedListener) {
        listeners.remove(listener)
    }

    fun changeTarget(controller: MediaController) {
        target = controller
        for ((listener, executor) in listeners) executor.execute {
            listener.onMediaKeyEventSessionChanged(controller.packageName, controller.sessionToken)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 36], shadows = [MediaKeyManagerShadow::class])
class WidgetMediaKeyTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Test fun changingTheFrameworkMediaTargetRefreshesStateAndListenerIsRemovedOnClose() {
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationListenerAccessGranted(
            AndroidSessionGateway.listenerComponent(app), true)
        val manager = app.getSystemService(MediaSessionManager::class.java)
        val shadow = Shadow.extract<MediaKeyManagerShadow>(manager)
        val first = MediaSession(app, "key-first")
        val second = MediaSession(app, "key-second")
        try {
            fun add(session: MediaSession, value: Int, name: String) = session.controller.apply {
                shadowOf(this).setPackageName(name)
                shadowOf(this).setPlaybackState(PlaybackState.Builder().setState(value, 0, 0f).build())
                shadow.addController(this)
            }
            val playing = add(first, PlaybackState.STATE_PLAYING, "first.player")
            val paused = add(second, PlaybackState.STATE_PAUSED, "second.player")
            val changes = mutableListOf<WidgetPlaybackState>()
            WidgetPlaybackObserver(app) { changes += it }.use { observer ->
                observer.start(); shadowOf(Looper.getMainLooper()).idle()
                assertEquals(listOf(WidgetPlaybackState.PLAYING), changes)
                assertEquals(1, shadow.listeners.size)
                shadow.changeTarget(paused); shadowOf(Looper.getMainLooper()).idle()
                assertEquals(WidgetPlaybackState.PAUSED, changes.last())
                shadow.changeTarget(playing); shadowOf(Looper.getMainLooper()).idle()
                assertEquals(WidgetPlaybackState.PLAYING, changes.last())
                assertEquals(3, changes.size)
            }
            assertTrue(shadow.listeners.isEmpty())
        } finally { first.release(); second.release() }
    }
}
