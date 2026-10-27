package dev.local.mediaactions

interface SessionGateway {
    fun hasNotificationAccess(): Boolean
    /** May throw SecurityException if access is revoked between the check and binder call. */
    fun snapshot(): SessionSnapshot
}

/** One selection and at most one transport call. No retries or global media-key broadcasts. */
class MediaActionDispatcher(private val gateway: SessionGateway) {
    fun execute(action: MediaAction): ActionResult = try {
        if (!gateway.hasNotificationAccess()) {
            ActionResult.MissingNotificationAccess
        } else {
            when (val selection = MediaSessionSelector.select(gateway.snapshot())) {
                Selection.None -> ActionResult.NoActiveSession
                Selection.Ambiguous -> ActionResult.AmbiguousSession
                is Selection.Selected -> dispatch(selection.session, action)
            }
        }
    } catch (_: SecurityException) {
        ActionResult.MissingNotificationAccess
    } catch (_: RuntimeException) {
        // Do not expose vendor exception messages that could contain media information.
        ActionResult.Failure
    }

    fun playerStatus(): PlayerStatus = try {
        if (!gateway.hasNotificationAccess()) {
            PlayerStatus.MissingNotificationAccess
        } else {
            when (val selection = MediaSessionSelector.select(gateway.snapshot())) {
                Selection.None -> PlayerStatus.NoActiveSession
                Selection.Ambiguous -> PlayerStatus.AmbiguousSession
                is Selection.Selected -> PlayerStatus.Selected(selection.session.packageName)
            }
        }
    } catch (_: SecurityException) {
        PlayerStatus.MissingNotificationAccess
    } catch (_: RuntimeException) {
        PlayerStatus.Failure
    }

    private fun dispatch(session: PlayerSession, action: MediaAction): ActionResult {
        val command = when (action) {
            MediaAction.NEXT -> TransportCommand.NEXT
            MediaAction.PREVIOUS -> TransportCommand.PREVIOUS
            MediaAction.PLAY_PAUSE -> when (session.state) {
                SessionState.PLAYING -> TransportCommand.PAUSE
                SessionState.PAUSED, SessionState.STOPPED -> TransportCommand.PLAY
                // Do not guess whether buffering/connecting means play or pause.
                else -> return ActionResult.UnsupportedAction
            }
        }
        if (command !in session.supportedCommands) return ActionResult.UnsupportedAction
        session.send(command)
        return ActionResult.CommandSent(session.packageName)
    }
}
