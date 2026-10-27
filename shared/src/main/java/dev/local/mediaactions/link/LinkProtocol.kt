package dev.local.mediaactions.link

import dev.local.mediaactions.MediaAction
import dev.local.mediaactions.SessionState
import dev.local.mediaactions.TransportCommand
import java.util.UUID

enum class LinkResult { SENT, NO_ACCESS, NO_SESSION, AMBIGUOUS, CHANGED, UNSUPPORTED, INVALID, EXPIRED, BUSY, FAILED, UNCONFIRMED }
data class MediaOffer(val result: LinkResult, val state: SessionState? = null, val ticket: String? = null) {
    val playing get() = result == LinkResult.SENT && state == SessionState.PLAYING
    val idle get() = result == LinkResult.SENT && state in setOf(SessionState.PAUSED, SessionState.STOPPED)
    fun command(action: MediaAction): TransportCommand? = when (action) {
        MediaAction.NEXT -> TransportCommand.NEXT
        MediaAction.PREVIOUS -> TransportCommand.PREVIOUS
        MediaAction.PLAY_PAUSE -> when { playing -> TransportCommand.PAUSE; idle -> TransportCommand.PLAY; else -> null }
    }
}
data class MediaRequest(val id: String, val ticket: String, val command: TransportCommand)
enum class DeviceTarget { PHONE, WATCH }
object TargetPolicy {
    fun choose(phone: MediaOffer, watch: MediaOffer, last: DeviceTarget): DeviceTarget? = when {
        phone.playing -> DeviceTarget.PHONE
        watch.playing -> DeviceTarget.WATCH
        last == DeviceTarget.PHONE && phone.idle -> DeviceTarget.PHONE
        last == DeviceTarget.WATCH && watch.idle -> DeviceTarget.WATCH
        else -> null
    }
}
object LinkProtocol {
    const val PROBE = "/mediaactions/v1/probe"
    const val COMMAND = "/mediaactions/v1/command"
    const val PHONE_CAPABILITY = "media_actions_phone_v1"
    const val LEASE_MS = 5_000L
    private val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    fun id(): String = UUID.randomUUID().toString()
    private fun fields(data: ByteArray): List<String> {
        require(data.size in 1..4096 && data.all { it.toInt() in 32..126 })
        return data.toString(Charsets.US_ASCII).split('|').also { require(it.first() == "1") }
    }
    fun encode(offer: MediaOffer) = "1|${offer.result}|${offer.state ?: "-"}|${offer.ticket ?: "-"}".toByteArray(Charsets.US_ASCII)
    fun offer(data: ByteArray): MediaOffer {
        val p = fields(data); require(p.size == 4)
        val result = LinkResult.valueOf(p[1])
        val state = p[2].takeUnless { it == "-" }?.let(SessionState::valueOf)
        val ticket = p[3].takeUnless { it == "-" }
        require(if (result == LinkResult.SENT) state != null && ticket != null && uuid.matches(ticket) else state == null && ticket == null)
        return MediaOffer(result, state, ticket)
    }
    fun encode(request: MediaRequest) = "1|${request.id}|${request.ticket}|${request.command}".toByteArray(Charsets.US_ASCII)
    fun request(data: ByteArray): MediaRequest {
        val p = fields(data); require(p.size == 4 && uuid.matches(p[1]) && uuid.matches(p[2]))
        return MediaRequest(p[1], p[2], TransportCommand.valueOf(p[3]))
    }
    fun encode(result: LinkResult) = "1|$result".toByteArray(Charsets.US_ASCII)
    fun result(data: ByteArray): LinkResult {
        val p = fields(data); require(p.size == 2); return LinkResult.valueOf(p[1])
    }
}
