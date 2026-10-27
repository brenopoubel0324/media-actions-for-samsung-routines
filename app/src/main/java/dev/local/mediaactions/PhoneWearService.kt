package dev.local.mediaactions

import android.os.SystemClock
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.WearableListenerService
import dev.local.mediaactions.link.LinkProtocol
import dev.local.mediaactions.link.LinkResult
import dev.local.mediaactions.link.LocalMediaEndpoint

/** Data Layer authenticates matching package/signature; no exported raw broadcast command receiver. */
class PhoneWearService : WearableListenerService() {
    override fun onRequest(nodeId: String, path: String, request: ByteArray): Task<ByteArray> {
        val endpoint = endpoint()
        val response = try {
            when (path) {
                LinkProtocol.PROBE -> if (request.contentEquals(byteArrayOf(1))) LinkProtocol.encode(endpoint.probe(nodeId))
                    else LinkProtocol.encode(LinkResult.INVALID)
                LinkProtocol.COMMAND -> {
                    val result = endpoint.execute(nodeId, LinkProtocol.request(request))
                    if (result == LinkResult.SENT) {
                        try { MediaWidgetProvider.refreshAll(this) } catch (_: RuntimeException) { /* Host failure must not resend a command. */ }
                    }
                    LinkProtocol.encode(result)
                }
                else -> LinkProtocol.encode(LinkResult.INVALID)
            }
        } catch (_: IllegalArgumentException) { LinkProtocol.encode(LinkResult.INVALID) }
        return Tasks.forResult(response)
    }
    private fun endpoint(): LocalMediaEndpoint = synchronized(PhoneWearService::class.java) {
        current ?: LocalMediaEndpoint(AndroidSessionGateway(applicationContext), SystemClock::elapsedRealtime).also { current = it }
    }
    companion object { private var current: LocalMediaEndpoint? = null }
}
