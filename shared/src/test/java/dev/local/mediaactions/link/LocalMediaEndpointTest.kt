package dev.local.mediaactions.link

import dev.local.mediaactions.*
import org.junit.Assert.*
import org.junit.Test

class LocalMediaEndpointTest {
    private class Player(override val sessionIdentity: Any = "session-1", override var state: SessionState = SessionState.PLAYING) : PlayerSession {
        override val packageName = "test.player"
        override var supportedCommands = TransportCommand.entries.toSet()
        val sent = mutableListOf<TransportCommand>()
        var fail = false
        override fun send(command: TransportCommand) { sent += command; if (fail) error("binder failure") }
    }
    private class Gateway(var players: List<PlayerSession>) : SessionGateway {
        var access = true
        var fail = false
        var onRead: () -> Unit = {}
        override fun hasNotificationAccess() = access
        override fun snapshot(): SessionSnapshot { onRead(); if (fail) error("lookup failure"); return SessionSnapshot(players) }
    }
    private val player = Player()
    private val gateway = Gateway(listOf(player))
    private var time = 100L
    private val endpoint = LocalMediaEndpoint(gateway) { time }
    private fun request(ticket: String, command: TransportCommand = TransportCommand.NEXT) = MediaRequest(LinkProtocol.id(), ticket, command)

    @Test fun eachCommandSendsExactlyOnceAndDuplicateReturnsSameResult() {
        for (command in listOf(TransportCommand.NEXT, TransportCommand.PREVIOUS, TransportCommand.PAUSE)) {
            val request = request(endpoint.probe("watch").ticket!!, command)
            assertEquals(LinkResult.SENT, endpoint.execute("watch", request))
            assertEquals(LinkResult.SENT, endpoint.execute("watch", request))
        }
        assertEquals(listOf(TransportCommand.NEXT, TransportCommand.PREVIOUS, TransportCommand.PAUSE), player.sent)
    }
    @Test fun separateGesturesMaySkipTheSameActionTwice() {
        repeat(2) { assertEquals(LinkResult.SENT, endpoint.execute("watch", request(endpoint.probe("watch").ticket!!))) }
        assertEquals(2, player.sent.size)
    }
    @Test fun ticketCannotExecuteASecondRequest() {
        val ticket = endpoint.probe("watch").ticket!!
        assertEquals(LinkResult.SENT, endpoint.execute("watch", request(ticket)))
        assertEquals(LinkResult.EXPIRED, endpoint.execute("watch", request(ticket)))
        assertEquals(1, player.sent.size)
    }
    @Test fun anotherPeerCannotUseTheTicket() {
        val request = request(endpoint.probe("watch").ticket!!)
        assertEquals(LinkResult.INVALID, endpoint.execute("other-watch", request))
        assertTrue(player.sent.isEmpty())
    }
    @Test fun expiredAndPreviousProcessTicketsNeverExecute() {
        val request = request(endpoint.probe("watch").ticket!!)
        time += LinkProtocol.LEASE_MS
        assertEquals(LinkResult.EXPIRED, endpoint.execute("watch", request))
        assertEquals(LinkResult.EXPIRED, LocalMediaEndpoint(gateway) { time }.execute("watch", request))
        assertTrue(player.sent.isEmpty())
    }
    @Test fun leaseExpiringDuringLookupDoesNotSend() {
        val request = request(endpoint.probe("watch").ticket!!)
        gateway.onRead = { time += LinkProtocol.LEASE_MS }
        assertEquals(LinkResult.EXPIRED, endpoint.execute("watch", request))
        assertTrue(player.sent.isEmpty())
    }
    @Test fun callerDeadlineAlsoPreventsALateLocalCommand() {
        val request = request(endpoint.probe("watch").ticket!!)
        val deadline = time + 100
        gateway.onRead = { time += 100 }
        assertEquals(LinkResult.EXPIRED, endpoint.execute("watch", request, deadline))
        assertTrue(player.sent.isEmpty())
    }
    @Test fun alteredRequestIdCannotChangeAnAlreadyExecutedAction() {
        val request = request(endpoint.probe("watch").ticket!!)
        assertEquals(LinkResult.SENT, endpoint.execute("watch", request))
        assertEquals(LinkResult.INVALID, endpoint.execute("watch", request.copy(command = TransportCommand.PREVIOUS)))
        assertEquals(1, player.sent.size)
    }
    @Test fun changedSessionWithSamePackageIsRejected() {
        val request = request(endpoint.probe("watch").ticket!!)
        val other = Player("different-token")
        gateway.players = listOf(other)
        assertEquals(LinkResult.CHANGED, endpoint.execute("watch", request))
        assertTrue(player.sent.isEmpty() && other.sent.isEmpty())
    }
    @Test fun newControllerObjectWithSameTokenStillMatches() {
        val request = request(endpoint.probe("watch").ticket!!)
        val fresh = Player()
        gateway.players = listOf(fresh)
        assertEquals(LinkResult.SENT, endpoint.execute("watch", request))
        assertEquals(listOf(TransportCommand.NEXT), fresh.sent)
    }
    @Test fun externalPauseDoesNotGetTurnedIntoAResume() {
        val request = request(endpoint.probe("watch").ticket!!, TransportCommand.PAUSE)
        player.state = SessionState.PAUSED
        assertEquals(LinkResult.CHANGED, endpoint.execute("watch", request))
        assertTrue(player.sent.isEmpty())
    }
    @Test fun pausedSessionResumesOnlyWithExplicitPlay() {
        player.state = SessionState.PAUSED
        assertEquals(LinkResult.SENT, endpoint.execute("watch", request(endpoint.probe("watch").ticket!!, TransportCommand.PLAY)))
        assertEquals(listOf(TransportCommand.PLAY), player.sent)
    }
    @Test fun revokedAccessAndUnsupportedActionsNeverSend() {
        val request = request(endpoint.probe("watch").ticket!!)
        gateway.access = false
        assertEquals(LinkResult.NO_ACCESS, endpoint.execute("watch", request))
        assertEquals(LinkResult.NO_ACCESS, endpoint.probe("watch").result)
        gateway.access = true
        player.supportedCommands = emptySet()
        assertEquals(LinkResult.UNSUPPORTED, endpoint.execute("watch", request(endpoint.probe("watch").ticket!!)))
        assertTrue(player.sent.isEmpty())
    }
    @Test fun failureAfterSideEffectDoesNotRepeat() {
        val request = request(endpoint.probe("watch").ticket!!)
        player.fail = true
        assertEquals(LinkResult.FAILED, endpoint.execute("watch", request))
        player.fail = false
        assertEquals(LinkResult.FAILED, endpoint.execute("watch", request))
        assertEquals(1, player.sent.size)
    }
    @Test fun noPlayerAmbiguityAndLookupFailureAreDistinct() {
        gateway.players = emptyList()
        assertEquals(LinkResult.NO_SESSION, endpoint.probe("watch").result)
        gateway.players = listOf(Player("one", SessionState.PAUSED), Player("two", SessionState.PAUSED))
        assertEquals(LinkResult.AMBIGUOUS, endpoint.probe("watch").result)
        gateway.fail = true
        assertEquals(LinkResult.FAILED, endpoint.probe("watch").result)
    }
    @Test fun leasesAreBoundedAndExpiredSlotsRecover() {
        repeat(256) { assertNotNull(endpoint.probe("watch").ticket) }
        assertEquals(LinkResult.BUSY, endpoint.probe("watch").result)
        time += LinkProtocol.LEASE_MS
        assertNotNull(endpoint.probe("watch").ticket)
    }
    @Test fun ledgerNeverEvictsLiveIdsToMakeRoomForDuplicateCommands() {
        repeat(256) { assertEquals(LinkResult.SENT, endpoint.execute("watch", request(endpoint.probe("watch").ticket!!))) }
        assertEquals(LinkResult.BUSY, endpoint.execute("watch", request(endpoint.probe("watch").ticket!!)))
        assertEquals(256, player.sent.size)
    }
    @Test fun transitioningPlayerDoesNotReceiveTransportCommand() {
        player.state = SessionState.TRANSITIONING
        assertEquals(LinkResult.UNSUPPORTED, endpoint.execute("watch", request(endpoint.probe("watch").ticket!!)))
        assertTrue(player.sent.isEmpty())
    }
}
