package dev.local.mediaactions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.media.MediaBrowserService
import android.util.Log

/** Bounded browser connection, kept alive until playback confirmation or deadline. No UI or retries. */
class SpotifyPlaybackResumer(
    private val controllerFactory: (Context, MediaSession.Token) -> MediaController = { context, token -> MediaController(context, token) },
) {
    enum class Status(val message: Int) {
        IDLE(R.string.spotify_resume_idle), CONNECTING(R.string.spotify_resume_connecting),
        PLAYING(R.string.spotify_resume_playing), NOT_AVAILABLE(R.string.spotify_resume_unavailable),
        REJECTED(R.string.spotify_resume_rejected), TIMED_OUT(R.string.spotify_resume_timeout),
        NOT_PLAYING(R.string.spotify_resume_unconfirmed), SESSION_CHANGED(R.string.spotify_resume_changed),
        NO_ACCESS(R.string.spotify_resume_no_access), FAILED(R.string.spotify_resume_failed), BUSY(R.string.spotify_resume_busy),
    }
    private val handler = Handler(Looper.getMainLooper())
    private var active: Attempt? = null

    fun resume(context: Context, currentPlayer: () -> PlayerStatus, completed: (Status) -> Unit) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { resume(context.applicationContext, currentPlayer, completed) }; return
        }
        val app = context.applicationContext
        if (active != null) { completed(Status.BUSY); return }
        val attempt = Attempt(app, currentPlayer, completed)
        active = attempt
        attempt.start()
    }

    private inner class Attempt(val app: Context, val currentPlayer: () -> PlayerStatus, val completed: (Status) -> Unit) {
        var browser: MediaBrowser? = null
        var controller: MediaController? = null
        var finished = false
        var commandSent = false
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS
        fun expired() = SystemClock.elapsedRealtime() >= deadline
        val timeout = Runnable { finish(if (commandSent) Status.NOT_PLAYING else Status.TIMED_OUT) }
        val playbackCallback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                if (finished || !commandSent) return
                if (expired()) { finish(Status.NOT_PLAYING); return }
                when (state?.state) {
                    PlaybackState.STATE_PLAYING -> finish(Status.PLAYING)
                    PlaybackState.STATE_ERROR -> finish(Status.NOT_PLAYING)
                    else -> Unit // Paused/transitional/unknown states do not confirm playback.
                }
            }
            override fun onSessionDestroyed() { finish(Status.NOT_PLAYING) }
        }
        val connectionCallback = object : MediaBrowser.ConnectionCallback() {
            override fun onConnected() {
                if (finished || commandSent) return
                if (expired()) { finish(Status.TIMED_OUT); return }
                try {
                    val player = controllerFactory(app, requireNotNull(browser).sessionToken)
                    if (player.packageName != SPOTIFY) { finish(Status.REJECTED); return }
                    controller = player
                    val status = currentPlayer()
                    if (status == PlayerStatus.MissingNotificationAccess) { finish(Status.NO_ACCESS); return }
                    if (status == PlayerStatus.Failure) { finish(Status.FAILED); return }
                    if (status != PlayerStatus.NoActiveSession && status != PlayerStatus.Selected(SPOTIFY)) {
                        finish(Status.SESSION_CHANGED); return
                    }
                    if (expired()) { finish(Status.TIMED_OUT); return }
                    player.registerCallback(playbackCallback, handler)
                    if (player.playbackState?.state == PlaybackState.STATE_PLAYING) { finish(Status.PLAYING); return }
                    commandSent = true // Consume before side effects; late/duplicate callbacks must not repeat Play.
                    player.transportControls.prepare()
                    if (expired()) { finish(Status.NOT_PLAYING); return }
                    player.transportControls.play()
                    if (player.playbackState?.state == PlaybackState.STATE_PLAYING) finish(Status.PLAYING)
                } catch (_: SecurityException) { finish(Status.REJECTED) }
                  catch (_: RuntimeException) { finish(Status.FAILED) }
            }
            override fun onConnectionFailed() { finish(Status.REJECTED) }
            override fun onConnectionSuspended() { finish(Status.REJECTED) }
        }
        fun start() {
            try {
                val query = Intent(MediaBrowserService.SERVICE_INTERFACE).setPackage(SPOTIFY)
                val services = app.packageManager.queryIntentServices(query, PackageManager.ResolveInfoFlags.of(0))
                    .mapNotNull { it.serviceInfo }.filter {
                        it.packageName == SPOTIFY && it.exported && it.enabled && it.applicationInfo.enabled &&
                            (it.permission.isNullOrEmpty() || app.checkSelfPermission(it.permission) == PackageManager.PERMISSION_GRANTED)
                    }.distinctBy { it.name }
                val service = services.singleOrNull() ?: run { finish(Status.NOT_AVAILABLE); return }
                writeStatus(app, Status.CONNECTING)
                browser = MediaBrowser(app, ComponentName(SPOTIFY, service.name), connectionCallback, Bundle().apply {
                    putBoolean(MediaBrowserService.BrowserRoot.EXTRA_RECENT, true)
                })
                handler.postDelayed(timeout, (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0))
                browser!!.connect()
            } catch (_: SecurityException) { finish(Status.REJECTED) }
              catch (_: RuntimeException) { finish(Status.FAILED) }
        }
        fun finish(status: Status) {
            if (finished) return
            finished = true
            handler.removeCallbacks(timeout)
            try { controller?.unregisterCallback(playbackCallback) } catch (_: RuntimeException) { /* Already disconnected. */ }
            try { browser?.disconnect() } catch (_: RuntimeException) { /* Already disconnected. */ }
            controller = null; browser = null
            if (active === this) active = null
            writeStatus(app, status)
            try { completed(status) } catch (_: RuntimeException) {
                Log.w(ActionActivity.LOG_TAG, "Spotify result callback failed")
            }
        }
    }

    companion object {
        const val SPOTIFY = "com.spotify.music"
        const val TIMEOUT_MS = 6_000L
        val shared by lazy { SpotifyPlaybackResumer() }
        data class Completion(val status: Status, val completedAtEpochMs: Long?)
        fun lastCompletion(context: Context): Completion {
            val values = context.getSharedPreferences("spotify_resume", Context.MODE_PRIVATE).all
            val status = Status.entries.firstOrNull { it.name == values["status"] }
                ?.takeUnless { it == Status.CONNECTING || it == Status.BUSY } ?: Status.IDLE
            val time = (values["completed_at_epoch_ms"] as? Long)?.takeIf { it > 0 && status != Status.IDLE }
            return Completion(status, time)
        }
        fun lastStatus(context: Context): Status = lastCompletion(context).status
        private fun writeStatus(context: Context, status: Status) {
            if (status != Status.CONNECTING && status != Status.BUSY) context.getSharedPreferences("spotify_resume", Context.MODE_PRIVATE)
                .edit().putString("status", status.name).putLong("completed_at_epoch_ms", System.currentTimeMillis()).apply()
            Log.i(ActionActivity.LOG_TAG, "Spotify resume: ${status.name}")
        }
    }
}
