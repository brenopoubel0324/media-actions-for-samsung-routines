package dev.local.mediaactions

import org.junit.Assert.*
import org.junit.Test

class MediaPolicyTest {
    private class Session(
        override val packageName: String = "player.one",
        override val state: SessionState = SessionState.PLAYING,
        override val supportedCommands: Set<TransportCommand> = TransportCommand.entries.toSet(),
    ) : PlayerSession {
        val sent = mutableListOf<TransportCommand>()
        var failure: RuntimeException? = null
        override fun send(command: TransportCommand) {
            failure?.let { throw it }
            sent += command
        }
    }

    private class Gateway(
        val data: SessionSnapshot,
        val allowed: Boolean = true,
        val failure: RuntimeException? = null,
    ) : SessionGateway {
        var reads = 0
        override fun hasNotificationAccess() = allowed
        override fun snapshot(): SessionSnapshot {
            reads++
            failure?.let { throw it }
            return data
        }
    }

    private fun selected(snapshot: SessionSnapshot) =
        (MediaSessionSelector.select(snapshot) as Selection.Selected).session

    @Test fun exactFrameworkSessionWinsOverAnotherPlayingPlayer() {
        val playing = Session()
        val paused = Session("player.two", SessionState.PAUSED)
        assertSame(paused, selected(SessionSnapshot(listOf(playing, paused), paused)))
    }

    @Test fun uniqueFrameworkPackageWins() {
        val playing = Session()
        val paused = Session("player.two", SessionState.PAUSED)
        assertSame(paused, selected(SessionSnapshot(listOf(playing, paused), mediaKeyPackage = "player.two")))
    }

    @Test fun staleFrameworkHintsCannotIntroduceAPlayer() {
        val actual = Session()
        val stale = Session("stale", SessionState.PAUSED)
        assertSame(actual, selected(SessionSnapshot(listOf(actual), stale, "stale")))
    }

    @Test fun unavailableFrameworkSessionFallsBackToPlaying() {
        val unavailable = Session("dead", SessionState.UNAVAILABLE)
        val playing = Session()
        assertSame(playing, selected(SessionSnapshot(listOf(unavailable, playing), unavailable, "dead")))
    }

    @Test fun playingWinsOverPausedWithoutFrameworkHint() {
        val paused = Session("paused", SessionState.PAUSED)
        val playing = Session()
        assertSame(playing, selected(SessionSnapshot(listOf(paused, playing))))
    }

    @Test fun frameworkOrderBreaksTiesBetweenPlayingPlayers() {
        val first = Session("first")
        assertSame(first, selected(SessionSnapshot(listOf(first, Session("second")))))
    }

    @Test fun onePausedOrStoppedPlayerIsEligible() {
        for (state in listOf(SessionState.PAUSED, SessionState.STOPPED)) {
            val session = Session(state = state)
            assertSame(session, selected(SessionSnapshot(listOf(session))))
        }
    }

    @Test fun multipleIdleSessionsAreAmbiguousEvenInSamePackage() {
        val sessions = listOf(Session(state = SessionState.PAUSED), Session(state = SessionState.STOPPED))
        assertEquals(Selection.Ambiguous, MediaSessionSelector.select(SessionSnapshot(sessions, mediaKeyPackage = "player.one")))
    }

    @Test fun emptyAndUnavailableSessionsAreNotCommanded() {
        assertEquals(Selection.None, MediaSessionSelector.select(SessionSnapshot(emptyList())))
        assertEquals(Selection.None, MediaSessionSelector.select(SessionSnapshot(listOf(Session(state = SessionState.UNAVAILABLE)))))
    }

    @Test fun deniedAccessDoesNotEvenReadSessions() {
        val gateway = Gateway(SessionSnapshot(listOf(Session())), allowed = false)
        val dispatcher = MediaActionDispatcher(gateway)
        assertEquals(ActionResult.MissingNotificationAccess, dispatcher.execute(MediaAction.NEXT))
        assertEquals(PlayerStatus.MissingNotificationAccess, dispatcher.playerStatus())
        assertEquals(0, gateway.reads)
    }

    @Test fun revocationBetweenCheckAndQueryIsHandled() {
        val dispatcher = MediaActionDispatcher(Gateway(SessionSnapshot(emptyList()), failure = SecurityException()))
        assertEquals(ActionResult.MissingNotificationAccess, dispatcher.execute(MediaAction.NEXT))
        assertEquals(PlayerStatus.MissingNotificationAccess, dispatcher.playerStatus())
    }

    @Test fun vendorLookupFailureIsHandled() {
        val dispatcher = MediaActionDispatcher(Gateway(SessionSnapshot(emptyList()), failure = IllegalStateException("private detail")))
        assertEquals(ActionResult.Failure, dispatcher.execute(MediaAction.NEXT))
        assertEquals(PlayerStatus.Failure, dispatcher.playerStatus())
    }

    @Test fun eachRequestSendsExactlyOneMatchingCommand() {
        for ((action, command) in listOf(MediaAction.NEXT to TransportCommand.NEXT,
            MediaAction.PREVIOUS to TransportCommand.PREVIOUS,
            MediaAction.PLAY_PAUSE to TransportCommand.PAUSE)) {
            val session = Session()
            val gateway = Gateway(SessionSnapshot(listOf(session)))
            assertEquals(ActionResult.CommandSent(session.packageName), MediaActionDispatcher(gateway).execute(action))
            assertEquals(listOf(command), session.sent)
            assertEquals(1, gateway.reads)
        }
    }

    @Test fun pauseAndStoppedToggleOnlyToPlay() {
        for (state in listOf(SessionState.PAUSED, SessionState.STOPPED)) {
            val session = Session(state = state)
            MediaActionDispatcher(Gateway(SessionSnapshot(listOf(session)))).execute(MediaAction.PLAY_PAUSE)
            assertEquals(listOf(TransportCommand.PLAY), session.sent)
        }
    }

    @Test fun transitionalToggleDoesNotGuessOrSend() {
        val session = Session(state = SessionState.TRANSITIONING)
        assertEquals(ActionResult.UnsupportedAction,
            MediaActionDispatcher(Gateway(SessionSnapshot(listOf(session)))).execute(MediaAction.PLAY_PAUSE))
        assertTrue(session.sent.isEmpty())
    }

    @Test fun unsupportedCommandNeverFallsBackOrTargetsAnotherPlayer() {
        val intended = Session(supportedCommands = emptySet())
        val other = Session("other")
        val dispatcher = MediaActionDispatcher(Gateway(SessionSnapshot(listOf(intended, other), intended)))
        for (action in MediaAction.entries) assertEquals(ActionResult.UnsupportedAction, dispatcher.execute(action))
        assertTrue(intended.sent.isEmpty())
        assertTrue(other.sent.isEmpty())
    }

    @Test fun commandExceptionNeverRetries() {
        val session = Session().apply { failure = IllegalStateException() }
        assertEquals(ActionResult.Failure,
            MediaActionDispatcher(Gateway(SessionSnapshot(listOf(session)))).execute(MediaAction.NEXT))
        assertTrue(session.sent.isEmpty())
    }

    @Test fun revokedAccessDuringCommandIsHandled() {
        val session = Session().apply { failure = SecurityException() }
        assertEquals(ActionResult.MissingNotificationAccess,
            MediaActionDispatcher(Gateway(SessionSnapshot(listOf(session)))).execute(MediaAction.NEXT))
    }

    @Test fun absentAndAmbiguousSessionsNeverSend() {
        assertEquals(ActionResult.NoActiveSession,
            MediaActionDispatcher(Gateway(SessionSnapshot(emptyList()))).execute(MediaAction.NEXT))
        val sessions = listOf(Session(state = SessionState.PAUSED), Session(state = SessionState.PAUSED))
        assertEquals(ActionResult.AmbiguousSession,
            MediaActionDispatcher(Gateway(SessionSnapshot(sessions))).execute(MediaAction.NEXT))
        assertTrue(sessions.all { it.sent.isEmpty() })
    }

    @Test fun repeatedPressesSendOneCommandPerInvocation() {
        val session = Session()
        val dispatcher = MediaActionDispatcher(Gateway(SessionSnapshot(listOf(session))))
        repeat(12) { dispatcher.execute(MediaAction.NEXT) }
        assertEquals(List(12) { TransportCommand.NEXT }, session.sent)
    }

    @Test fun playerStatusDoesNotControlPlayback() {
        val session = Session()
        assertEquals(PlayerStatus.Selected(session.packageName),
            MediaActionDispatcher(Gateway(SessionSnapshot(listOf(session)))).playerStatus())
        assertTrue(session.sent.isEmpty())
    }

    @Test fun shortcutAcceptsOnlyThreeExactRequests() {
        assertEquals(MediaAction.NEXT, ShortcutRequest.parse(ShortcutRequest.VIEW_ACTION, "mediaactions://run/next"))
        assertEquals(MediaAction.PREVIOUS, ShortcutRequest.parse(ShortcutRequest.VIEW_ACTION, "mediaactions://run/previous"))
        assertEquals(MediaAction.PLAY_PAUSE, ShortcutRequest.parse(ShortcutRequest.VIEW_ACTION, "mediaactions://run/toggle"))
        for (uri in listOf(null, "", "mediaactions://run/next?package=other", "mediaactions://run/next#x",
            "mediaactions://run/next/", "mediaactions://RUN/next", "https://run/next", "mediaactions://run/%6eext")) {
            assertNull(ShortcutRequest.parse(ShortcutRequest.VIEW_ACTION, uri))
        }
        assertNull(ShortcutRequest.parse(null, "mediaactions://run/next"))
        assertNull(ShortcutRequest.parse("other.action", "mediaactions://run/next"))
    }
}
