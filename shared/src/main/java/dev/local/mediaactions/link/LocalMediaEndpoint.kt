package dev.local.mediaactions.link

import dev.local.mediaactions.PlayerSession
import dev.local.mediaactions.Selection
import dev.local.mediaactions.SessionGateway
import dev.local.mediaactions.MediaSessionSelector
import dev.local.mediaactions.SessionState
import dev.local.mediaactions.TransportCommand

/** Process-local leases and at-most-once commands. Nothing is persisted or queued for reconnection. */
class LocalMediaEndpoint(private val gateway: SessionGateway, private val now: () -> Long) {
    private data class Lease(val peer: String, val until: Long, val identity: Any, val state: SessionState)
    private data class Completed(val request: MediaRequest, val until: Long, var result: LinkResult)
    private val leases = mutableMapOf<String, Lease>()
    private val completed = mutableMapOf<Pair<String, String>, Completed>()

    private fun cleanup() { val t = now(); leases.entries.removeAll { t >= it.value.until }; completed.entries.removeAll { t >= it.value.until } }
    private fun player(): Pair<LinkResult, PlayerSession?> = try {
        if (!gateway.hasNotificationAccess()) LinkResult.NO_ACCESS to null else when (val s = MediaSessionSelector.select(gateway.snapshot())) {
            Selection.None -> LinkResult.NO_SESSION to null
            Selection.Ambiguous -> LinkResult.AMBIGUOUS to null
            is Selection.Selected -> LinkResult.SENT to s.session
        }
    } catch (_: SecurityException) { LinkResult.NO_ACCESS to null }
      catch (_: RuntimeException) { LinkResult.FAILED to null }

    @Synchronized fun probe(peer: String): MediaOffer {
        cleanup()
        val (result, session) = player()
        if (session == null) return MediaOffer(result)
        if (leases.size >= 256) return MediaOffer(LinkResult.BUSY)
        val ticket = LinkProtocol.id()
        leases[ticket] = Lease(peer, now() + LinkProtocol.LEASE_MS, session.sessionIdentity, session.state)
        return MediaOffer(LinkResult.SENT, session.state, ticket)
    }

    @Synchronized fun execute(peer: String, request: MediaRequest, hardDeadline: Long = Long.MAX_VALUE): LinkResult {
        cleanup()
        val key = peer to request.id
        completed[key]?.let { return if (it.request == request) it.result else LinkResult.INVALID }
        val lease = leases[request.ticket] ?: return LinkResult.EXPIRED
        if (lease.peer != peer) return LinkResult.INVALID
        if (completed.size >= 256) return LinkResult.BUSY
        // Consume before any side effect, including validation failures.
        leases.remove(request.ticket)
        val record = Completed(request, now() + LinkProtocol.LEASE_MS, LinkResult.FAILED)
        completed[key] = record
        val (result, session) = player()
        record.result = when {
            now() >= minOf(lease.until, hardDeadline) -> LinkResult.EXPIRED
            session == null -> result
            session.sessionIdentity != lease.identity || session.state != lease.state -> LinkResult.CHANGED
            session.state == SessionState.TRANSITIONING || session.state == SessionState.UNAVAILABLE -> LinkResult.UNSUPPORTED
            request.command !in session.supportedCommands -> LinkResult.UNSUPPORTED
            request.command == TransportCommand.PLAY && session.state == SessionState.PLAYING -> LinkResult.CHANGED
            request.command == TransportCommand.PAUSE && session.state != SessionState.PLAYING -> LinkResult.CHANGED
            else -> try { session.send(request.command); LinkResult.SENT }
                catch (_: SecurityException) { LinkResult.NO_ACCESS }
                catch (_: RuntimeException) { LinkResult.FAILED }
        }
        return record.result
    }
}
