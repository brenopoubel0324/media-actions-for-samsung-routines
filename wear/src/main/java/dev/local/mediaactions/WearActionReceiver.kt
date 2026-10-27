package dev.local.mediaactions

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import dev.local.mediaactions.link.*
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Private receiver retains process lifetime via goAsync; no queued commands or permanent service. */
class WearActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val action = intent.getStringExtra(EXTRA_ACTION)?.let { name -> MediaAction.entries.firstOrNull { it.name == name } } ?: return
        val pending = goAsync()
        val deadline = SystemClock.elapsedRealtime() + 6_500
        val finished = AtomicBoolean(false)
        fun finish() { if (finished.compareAndSet(false, true)) pending.finish() }
        val watchdog = Runnable { finish() }
        main.postDelayed(watchdog, 8_000)
        try {
            workers.execute {
                try {
                    val result = runAction(context.applicationContext, action, deadline)
                    if (result != LinkResult.SENT) message(context, if (result == LinkResult.UNCONFIRMED) R.string.unconfirmed else R.string.unavailable)
                } catch (_: Exception) { message(context, R.string.unavailable) }
                finally { main.removeCallbacks(watchdog); finish() }
            }
        } catch (_: RejectedExecutionException) { main.removeCallbacks(watchdog); finish(); message(context, R.string.busy) }
    }
    companion object {
        const val ACTION = "dev.local.mediaactions.RUN_WEAR_ACTION"
        const val EXTRA_ACTION = "media_action"
        private val workers = ThreadPoolExecutor(0, 2, 10, TimeUnit.SECONDS, SynchronousQueue<Runnable>())
        private val main = Handler(Looper.getMainLooper())
        private var local: LocalMediaEndpoint? = null
        private fun message(context: Context, text: Int) { main.post { Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show() } }
        private fun endpoint(context: Context): LocalMediaEndpoint = synchronized(WearActionReceiver::class.java) {
            local ?: LocalMediaEndpoint(AndroidSessionGateway(context), SystemClock::elapsedRealtime).also { local = it }
        }
        private fun <T> await(deadline: Long, maxMs: Long, task: () -> Task<T>): T {
            val remaining = minOf(maxMs, deadline - SystemClock.elapsedRealtime())
            check(remaining > 0)
            return Tasks.await(task(), remaining, TimeUnit.MILLISECONDS)
        }
        private fun runAction(context: Context, action: MediaAction, deadline: Long): LinkResult {
            val client = Wearable.getMessageClient(context)
            val peer = try {
                await(deadline, 1_000) { Wearable.getCapabilityClient(context).getCapability(LinkProtocol.PHONE_CAPABILITY,
                    CapabilityClient.FILTER_REACHABLE) }.nodes.singleOrNull()?.id
            } catch (_: Exception) { null }
            val phone = if (peer == null) MediaOffer(LinkResult.UNCONFIRMED) else try {
                LinkProtocol.offer(await(deadline, 1_500) { client.sendRequest(peer, LinkProtocol.PROBE, byteArrayOf(1)) })
            } catch (_: Exception) { MediaOffer(LinkResult.UNCONFIRMED) }
            val local = endpoint(context)
            val watch = local.probe("local")
            if (SystemClock.elapsedRealtime() >= deadline) return LinkResult.EXPIRED
            val prefs = context.getSharedPreferences("wear_target", Context.MODE_PRIVATE)
            val last = if (prefs.getString("last", null) == "WATCH") DeviceTarget.WATCH else DeviceTarget.PHONE
            val target = TargetPolicy.choose(phone, watch, last) ?: return LinkResult.NO_SESSION
            val offer = if (target == DeviceTarget.PHONE) phone else watch
            val command = offer.command(action) ?: return LinkResult.UNSUPPORTED
            val ticket = offer.ticket ?: return LinkResult.NO_SESSION
            val request = MediaRequest(LinkProtocol.id(), ticket, command)
            val result = if (target == DeviceTarget.WATCH) local.execute("local", request, deadline) else try {
                // After sendRequest is called, failure/timeout must never cause fallback or retry.
                LinkProtocol.result(await(deadline, 1_500) { client.sendRequest(peer!!, LinkProtocol.COMMAND, LinkProtocol.encode(request)) })
            } catch (_: Exception) { LinkResult.UNCONFIRMED }
            if (result == LinkResult.SENT) prefs.edit().putString("last", target.name).apply()
            return result
        }
    }
}
