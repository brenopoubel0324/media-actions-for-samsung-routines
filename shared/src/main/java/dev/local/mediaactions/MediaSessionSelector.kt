package dev.local.mediaactions

enum class SessionState { PLAYING, PAUSED, STOPPED, TRANSITIONING, UNAVAILABLE }
enum class TransportCommand { NEXT, PREVIOUS, PLAY, PAUSE }

/** No title, artist, notification data, or persistent identifiers are needed. */
interface PlayerSession {
    /** Stable only within this process; never sent over the wire. */
    val sessionIdentity: Any get() = this
    val packageName: String
    val state: SessionState
    val supportedCommands: Set<TransportCommand>
    fun send(command: TransportCommand)
}

data class SessionSnapshot(
    val sessions: List<PlayerSession>,
    val mediaKeySession: PlayerSession? = null,
    val mediaKeyPackage: String? = null,
)

sealed interface Selection {
    data class Selected(val session: PlayerSession) : Selection
    data object None : Selection
    data object Ambiguous : Selection
}

object MediaSessionSelector {
    fun select(snapshot: SessionSnapshot): Selection {
        val eligible = snapshot.sessions.filter { it.state != SessionState.UNAVAILABLE }
        if (eligible.isEmpty()) return Selection.None
        // An exact framework token wins, but a stale token must never add a controller.
        snapshot.mediaKeySession?.let { key ->
            eligible.firstOrNull { it === key }?.let { return Selection.Selected(it) }
        }
        // The package API may name a stale receiver or several sessions; trust only one match.
        snapshot.mediaKeyPackage?.let { pkg ->
            eligible.filter { it.packageName == pkg }.singleOrNull()?.let {
                return Selection.Selected(it)
            }
        }
        // getActiveSessions supplies framework priority order for simultaneous playing sessions.
        eligible.firstOrNull { it.state == SessionState.PLAYING }?.let {
            return Selection.Selected(it)
        }
        return eligible.singleOrNull()?.let(Selection::Selected) ?: Selection.Ambiguous
    }
}
