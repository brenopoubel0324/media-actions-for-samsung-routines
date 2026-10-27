package dev.local.mediaactions.link

import dev.local.mediaactions.*
import org.junit.Assert.*
import org.junit.Test

class LinkProtocolTest {
    @Test fun roundTripsAndActionsPreserveExplicitMeaning() {
        val offer = MediaOffer(LinkResult.SENT, SessionState.PLAYING, LinkProtocol.id())
        assertEquals(offer, LinkProtocol.offer(LinkProtocol.encode(offer)))
        assertEquals(TransportCommand.PAUSE, offer.command(MediaAction.PLAY_PAUSE))
        assertEquals(TransportCommand.PLAY, offer.copy(state = SessionState.PAUSED).command(MediaAction.PLAY_PAUSE))
        assertNull(offer.copy(state = SessionState.TRANSITIONING).command(MediaAction.PLAY_PAUSE))
        val request = MediaRequest(LinkProtocol.id(), offer.ticket!!, TransportCommand.NEXT)
        assertEquals(request, LinkProtocol.request(LinkProtocol.encode(request)))
        for (result in LinkResult.entries) assertEquals(result, LinkProtocol.result(LinkProtocol.encode(result)))
    }
    @Test fun rejectsUnknownVersionsActionsExtraFieldsOversizeAndMalformedIds() {
        for (bytes in listOf(byteArrayOf(), ByteArray(4097) { 65 }, "2|SENT|-|-".toByteArray(),
            "1|SENT|PLAYING|bad-id".toByteArray(), "1|NO_ACCESS|PLAYING|-".toByteArray(),
            "1|NO_ACCESS|-|-|extra".toByteArray(), byteArrayOf(1, 2), "1|NO_ACCESS|-|é".toByteArray())) {
            try { LinkProtocol.offer(bytes); fail("Accepted invalid offer") } catch (_: IllegalArgumentException) { }
        }
        val id = LinkProtocol.id()
        for (payload in listOf("1|bad|$id|NEXT", "1|$id|$id|TOGGLE", "1|$id|$id|NEXT|extra")) {
            try { LinkProtocol.request(payload.toByteArray()); fail("Accepted invalid command") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun autoTargetsPlayingDeviceAndPhoneWinsWhenBothPlay() {
        val playing = MediaOffer(LinkResult.SENT, SessionState.PLAYING, LinkProtocol.id())
        val paused = playing.copy(state = SessionState.PAUSED)
        assertEquals(DeviceTarget.PHONE, TargetPolicy.choose(playing, paused, DeviceTarget.WATCH))
        assertEquals(DeviceTarget.WATCH, TargetPolicy.choose(paused, playing, DeviceTarget.PHONE))
        assertEquals(DeviceTarget.PHONE, TargetPolicy.choose(playing, playing, DeviceTarget.WATCH))
        for (last in DeviceTarget.entries) assertEquals(last, TargetPolicy.choose(paused, paused, last))
    }
    @Test fun unavailablePreferredDeviceDoesNotSilentlyResumeTheOther() {
        val idle = MediaOffer(LinkResult.SENT, SessionState.PAUSED, LinkProtocol.id())
        for (failure in listOf(LinkResult.UNCONFIRMED, LinkResult.NO_ACCESS, LinkResult.AMBIGUOUS, LinkResult.NO_SESSION)) {
            assertNull(TargetPolicy.choose(MediaOffer(failure), idle, DeviceTarget.PHONE))
            assertNull(TargetPolicy.choose(idle, MediaOffer(failure), DeviceTarget.WATCH))
            assertEquals(DeviceTarget.PHONE, TargetPolicy.choose(idle.copy(state = SessionState.PLAYING), MediaOffer(failure), DeviceTarget.WATCH))
        }
    }
}
