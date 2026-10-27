package dev.local.mediaactions

import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper

/** Bound to the notification-listener lifecycle. No polling or media metadata callbacks. */
class WidgetPlaybackObserver(context: Context,
    private val onChanged: (WidgetPlaybackState) -> Unit = { MediaWidgetProvider.refreshAll(context.applicationContext, it) },
) : AutoCloseable {
    private val app = context.applicationContext
    private val manager = app.getSystemService(MediaSessionManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val watched = mutableMapOf<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    private var running = false
    private var activeRegistered = false
    private var keyRegistered = false
    private var lastState: WidgetPlaybackState? = null
    private var refreshPending = false
    private val refresh = Runnable {
        refreshPending = false
        if (running) {
            val state = WidgetPlaybackState.read(AndroidSessionGateway(app))
            if (state != lastState) emit(state)
        }
    }
    private val activeListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        if (running) {
            watch(controllers.orEmpty())
            scheduleRefresh()
        }
    }
    private val keyListener = MediaSessionManager.OnMediaKeyEventSessionChangedListener { _, _ ->
        scheduleRefresh()
    }

    fun start() {
        if (running) return
        running = true
        try {
            manager.addOnActiveSessionsChangedListener(activeListener,
                AndroidSessionGateway.listenerComponent(app), handler)
            activeRegistered = true
            watch(manager.getActiveSessions(AndroidSessionGateway.listenerComponent(app)))
        } catch (_: RuntimeException) {
            // A revoked grant or a vendor failure must not crash the system-bound service.
            close()
            emit(WidgetPlaybackState.UNKNOWN)
            return
        }
        try {
            manager.addOnMediaKeyEventSessionChangedListener(app.mainExecutor, keyListener)
            keyRegistered = true
        } catch (_: RuntimeException) {
            // Playback and active-session callbacks remain useful if this hint is unavailable.
        }
        scheduleRefresh()
    }

    private fun watch(controllers: List<MediaController>) {
        val tokens = controllers.map { it.sessionToken }.toSet()
        for (token in watched.keys.toList()) if (token !in tokens) {
            watched.remove(token)?.let { (controller, callback) ->
                try { controller.unregisterCallback(callback) } catch (_: RuntimeException) { /* Already gone. */ }
            }
        }
        for (controller in controllers) {
            val token = controller.sessionToken
            if (token in watched) continue
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = scheduleRefresh()
                override fun onSessionDestroyed() {
                    watched.remove(token)?.let { (old, registered) ->
                        try { old.unregisterCallback(registered) } catch (_: RuntimeException) { /* Already gone. */ }
                    }
                    scheduleRefresh()
                }
            }
            try {
                controller.registerCallback(callback, handler)
                watched[token] = controller to callback
            } catch (_: RuntimeException) { /* A session may disappear while the list is being read. */ }
        }
    }

    private fun scheduleRefresh() {
        if (!running || refreshPending) return
        refreshPending = true
        handler.post(refresh)
    }

    private fun emit(state: WidgetPlaybackState) {
        try {
            onChanged(state)
            lastState = state
        } catch (_: RuntimeException) {
            // A host failure must not kill the listener or mark an undelivered state as current.
        }
    }

    override fun close() {
        running = false
        handler.removeCallbacks(refresh)
        refreshPending = false
        for ((controller, callback) in watched.values) {
            try { controller.unregisterCallback(callback) } catch (_: RuntimeException) { /* Already gone. */ }
        }
        watched.clear()
        if (activeRegistered) try {
            manager.removeOnActiveSessionsChangedListener(activeListener)
        } catch (_: RuntimeException) { /* Grant may already be revoked. */ }
        if (keyRegistered) try {
            manager.removeOnMediaKeyEventSessionChangedListener(keyListener)
        } catch (_: RuntimeException) { /* Grant may already be revoked. */ }
        activeRegistered = false
        keyRegistered = false
        lastState = null
    }
}
