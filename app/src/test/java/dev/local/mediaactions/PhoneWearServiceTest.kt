package dev.local.mediaactions

import android.app.NotificationManager
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import dev.local.mediaactions.link.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 36])
class PhoneWearServiceTest {
    @Test fun rpcValidatesRoutesAndPermissionThenExecutesAgainstTheExpectedAndroidSession() {
        val app = RuntimeEnvironment.getApplication()
        val controller = Robolectric.buildService(PhoneWearService::class.java).create()
        val service = controller.get()
        val session = MediaSession(app, "wear-rpc-test")
        try {
            fun call(path: String, data: ByteArray) = service.onRequest("test-watch", path, data).result
            assertEquals(LinkResult.INVALID, LinkProtocol.result(call("/other", byteArrayOf(1))))
            assertEquals(LinkResult.INVALID, LinkProtocol.result(call(LinkProtocol.COMMAND, "bad".toByteArray())))
            assertEquals(LinkResult.NO_ACCESS, LinkProtocol.offer(call(LinkProtocol.PROBE, byteArrayOf(1))).result)
            shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationListenerAccessGranted(AndroidSessionGateway.listenerComponent(app), true)
            val media = session.controller
            shadowOf(media).setPackageName("test.player")
            shadowOf(media).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f)
                .setActions(PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_PAUSE).build())
            shadowOf(app.getSystemService(MediaSessionManager::class.java)).addController(media)
            val offer = LinkProtocol.offer(call(LinkProtocol.PROBE, byteArrayOf(1)))
            assertEquals(SessionState.PLAYING, offer.state)
            val request = MediaRequest(LinkProtocol.id(), offer.ticket!!, TransportCommand.NEXT)
            assertEquals(LinkResult.SENT, LinkProtocol.result(call(LinkProtocol.COMMAND, LinkProtocol.encode(request))))
            assertEquals(PlaybackState.ACTION_SKIP_TO_NEXT, shadowOf(media.transportControls).lastPerformedAction)
            shadowOf(media).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED, 0, 0f).setActions(PlaybackState.ACTION_PLAY).build())
            assertEquals(LinkResult.SENT, LinkProtocol.result(call(LinkProtocol.COMMAND, LinkProtocol.encode(request))))
            assertEquals(LinkResult.EXPIRED, LinkProtocol.result(call(LinkProtocol.COMMAND, LinkProtocol.encode(request.copy(id = LinkProtocol.id())))))
        } finally { session.release(); controller.destroy() }
    }
}
