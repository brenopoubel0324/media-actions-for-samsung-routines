package dev.local.mediaactions

sealed interface ActionResult {
    /** A transport command was sent; this is not confirmation that playback changed. */
    data class CommandSent(val packageName: String) : ActionResult
    data object MissingNotificationAccess : ActionResult
    data object NoActiveSession : ActionResult
    data object AmbiguousSession : ActionResult
    data object UnsupportedAction : ActionResult
    data object Failure : ActionResult
}

sealed interface PlayerStatus {
    data class Selected(val packageName: String) : PlayerStatus
    data object MissingNotificationAccess : PlayerStatus
    data object NoActiveSession : PlayerStatus
    data object AmbiguousSession : PlayerStatus
    data object Failure : PlayerStatus
}
