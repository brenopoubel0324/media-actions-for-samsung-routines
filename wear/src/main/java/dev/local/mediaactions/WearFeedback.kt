package dev.local.mediaactions

import android.content.Context
import dev.local.mediaactions.link.DeviceTarget
import dev.local.mediaactions.link.LinkResult
import dev.local.mediaactions.link.MediaOffer

/** Presentation only: no probes, retry or destination changes. Null target never invents an appliance. */
internal data class WearFeedback(val reason: Int, val target: DeviceTarget? = null) {
    fun text(context: Context): String {
        val message = context.getString(reason)
        return target?.let { context.getString(R.string.target_feedback,
            context.getString(if (it == DeviceTarget.PHONE) R.string.target_phone else R.string.target_watch), message) } ?: message
    }
    companion object {
        fun result(result: LinkResult, target: DeviceTarget? = null): WearFeedback? {
            val reason = when (result) {
                LinkResult.SENT -> return null
                LinkResult.NO_ACCESS -> R.string.no_access
                LinkResult.NO_SESSION -> R.string.no_session
                LinkResult.AMBIGUOUS -> R.string.ambiguous
                LinkResult.CHANGED -> R.string.changed
                LinkResult.UNSUPPORTED -> R.string.unsupported
                LinkResult.INVALID, LinkResult.FAILED -> R.string.failed
                LinkResult.EXPIRED -> R.string.expired
                LinkResult.BUSY -> R.string.busy
                LinkResult.UNCONFIRMED -> R.string.unconfirmed
            }
            return WearFeedback(reason, target)
        }
        fun noTarget(phone: MediaOffer, watch: MediaOffer): WearFeedback {
            val results = listOf(phone.result, watch.result)
            val reason = when {
                LinkResult.UNCONFIRMED in results -> R.string.connection_unavailable
                LinkResult.NO_ACCESS in results -> R.string.no_access
                LinkResult.BUSY in results -> R.string.busy
                results.all { it == LinkResult.NO_SESSION } -> R.string.no_session
                else -> R.string.no_destination
            }
            return WearFeedback(reason)
        }
    }
}
