package dev.local.mediaactions

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock

/** Listener-owned presentation cache. Commands always use their own fresh SessionGateway. */
class WidgetPlaybackObserver(context: Context,
    private val progressReader: (() -> WidgetTrackProgress?)? = null,
    private val onChanged: ((WidgetPlaybackState) -> Unit)? = null,
) : AutoCloseable {
    private val app = context.applicationContext
    private val manager = app.getSystemService(MediaSessionManager::class.java)
    private val gateway = AndroidSessionGateway(app)
    private val handler = Handler(Looper.getMainLooper())
    private data class Playback(val state: Int, val position: Long, val updated: Long, val speed: Float) {
        companion object {
            fun capture(value: PlaybackState?) = value?.let { Playback(it.state, it.position,
                it.lastPositionUpdateTime, it.playbackSpeed) }
        }
    }
    private class Entry(val controller: MediaController, val callback: MediaController.Callback) {
        val packageName: String = controller.packageName
        var playback: Playback? = null
        var stateDirty = true
        var duration = 0L
        var durationDirty = true
    }
    private val watched = linkedMapOf<MediaSession.Token, Entry>()
    private val destroyed = mutableSetOf<MediaSession.Token>()
    private var order = emptyList<MediaSession.Token>()
    private var selectionDirty = true
    private var selectedEntry: Entry? = null
    private var keyToken: MediaSession.Token? = null
    private var keyPackage: String? = null
    private var pendingControllers: List<MediaController>? = null
    private var resyncNeeded = false
    private var running = false
    private var generation = 0
    private var activeListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private var keyListener: MediaSessionManager.OnMediaKeyEventSessionChangedListener? = null
    private var lastState: WidgetPlaybackState? = null
    private var refreshPending = false
    private var forceRefresh = false
    private var allTargetsPending = false
    private var deliveryPending = false
    private var receiverRegistered = false
    private var screenOff = false
    private var ids = emptyList<Int>()
    private var progressIds = emptyList<Int>()
    private val presentation = MediaWidgetProvider.PresentationCache()
    /** Optional test/debug observer; no counters, storage or logging when unset. */
    internal enum class Work { PERIODIC_TICK, EVENT_REFRESH }
    internal var observeWork: ((Work) -> Unit)? = null
    private var eventPending = false
    private val pulse = Runnable {
        observeWork?.invoke(Work.PERIODIC_TICK)
        scheduleRefresh(true, event = false)
    }
    private val visibilityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handler.removeCallbacks(pulse)
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> screenOff = true
                Intent.ACTION_SCREEN_ON -> {
                    screenOff = false
                    loadTargets()
                    resyncNeeded = true
                    scheduleRefresh(true)
                }
                SETTINGS_CHANGED -> {
                    resyncNeeded = resyncNeeded || intent.getBooleanExtra(RESYNC_SESSIONS, false)
                    allTargetsPending = true
                    loadTargets()
                    scheduleRefresh(true)
                }
            }
        }
    }

    private fun loadTargets() {
        try {
            val widgets = AppWidgetManager.getInstance(app)
            ids = WidgetRegistry.playbackIds(app).filter { id ->
                val kind = WidgetRegistry.kind(app, id) ?: return@filter false
                WidgetRenderer.hasUsablePlaybackSize(widgets.getAppWidgetOptions(id), kind)
            }
            progressIds = ids.filter { WidgetStyleStore.load(app, it).progressRing }
            presentation.prepare(app, ids)
        } catch (_: RuntimeException) { ids = emptyList(); progressIds = emptyList() }
    }

    private fun updatePulse(snapshot: WidgetUpdateSnapshot) {
        handler.removeCallbacks(pulse)
        val playback = selectedEntry?.playback
        val advances = progressReader != null || (playback?.state == PlaybackState.STATE_PLAYING &&
            playback.updated > 0 && playback.speed.isFinite() &&
            (playback.speed > 0 && (snapshot.progress?.fraction ?: 1f) < 1f ||
                playback.speed < 0 && (snapshot.progress?.fraction ?: 0f) > 0f))
        if (running && advances && snapshot.state == WidgetPlaybackState.PLAYING && progressIds.isNotEmpty() &&
            !screenOff && app.getSystemService(PowerManager::class.java).isInteractive && snapshot.progress != null)
            // Handler uptime delays do not wake a sleeping device. No alarms, jobs or wake locks.
            handler.postDelayed(pulse, WidgetProgressSettings.load(app) * 1_000L)
    }

    private val refresh = Runnable {
        refreshPending = false
        if (running) {
            val event = eventPending
            eventPending = false
            val force = forceRefresh
            forceRefresh = false
            val allTargets = allTargetsPending
            allTargetsPending = false
            val snapshot = readPresentation()
            val changed = snapshot.state != lastState
            if (changed || allTargets || deliveryPending || force && progressIds.isNotEmpty()) {
                if (event) observeWork?.invoke(Work.EVENT_REFRESH)
                emit(snapshot, progressOnly = !changed && !allTargets && !deliveryPending)
            }
            updatePulse(snapshot)
        }
    }

    fun start() {
        if (running) return
        running = true
        if (onChanged == null) WidgetWallpaperRuntime.get(app)?.observer = java.lang.ref.WeakReference(this)
        loadTargets()
        try {
            app.registerReceiver(visibilityReceiver, IntentFilter(Intent.ACTION_SCREEN_ON).apply {
                addAction(Intent.ACTION_SCREEN_OFF); addAction(SETTINGS_CHANGED)
            }, Context.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        } catch (_: RuntimeException) {
            close()
            emit(WidgetUpdateSnapshot(WidgetPlaybackState.UNKNOWN, null))
            return
        }
        resyncNeeded = true
        scheduleRefresh()
    }

    /** Only presentation uses these entries. No transport controls or persistent media data. */
    private fun readPresentation(): WidgetUpdateSnapshot {
        val unknown = WidgetUpdateSnapshot(WidgetPlaybackState.UNKNOWN, null)
        if (ids.isEmpty() && onChanged == null) {
            detachSessions()
            return unknown
        }
        return try {
            // Revocation must win even if disconnect/callback delivery is delayed.
            if (!gateway.hasNotificationAccess()) {
                detachSessions()
                return unknown
            }
            if (activeListener == null) attachSessions()
            if (resyncNeeded) {
                pendingControllers = manager.getActiveSessions(AndroidSessionGateway.listenerComponent(app))
                keyToken = try { manager.mediaKeyEventSession } catch (_: RuntimeException) { null }
                keyPackage = try { manager.mediaKeyEventSessionPackageName?.takeIf { it.isNotBlank() } }
                    catch (_: RuntimeException) { null }
                watched.values.forEach { it.stateDirty = true; it.durationDirty = true }
                selectionDirty = true
                resyncNeeded = false
            }
            pendingControllers?.let { pendingControllers = null; watch(it) }
            if (selectionDirty) {
                val players = order.mapNotNull { token -> watched[token]?.let { entry ->
                    if (entry.stateDirty) {
                        entry.playback = Playback.capture(entry.controller.playbackState)
                        entry.stateDirty = false
                    }
                    CachedPlayer(token, entry.packageName, classify(entry.playback))
                } }
                val selected = (MediaSessionSelector.select(SessionSnapshot(players,
                    players.firstOrNull { it.sessionIdentity == keyToken }, keyPackage)) as? Selection.Selected)
                    ?.session as? CachedPlayer
                selectedEntry = selected?.let { watched[it.sessionIdentity] }
                selectionDirty = false
            }
            val entry = selectedEntry ?: return unknown
            val state = WidgetPlaybackState.fromSessionState(classify(entry.playback))
            val progress = if (state == WidgetPlaybackState.PLAYING && progressIds.isNotEmpty()) {
                if (progressReader != null) progressReader.invoke() else {
                    if (entry.durationDirty) {
                        entry.duration = entry.controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0
                        entry.durationDirty = false
                    }
                    entry.playback?.let { playback -> WidgetTrackProgress.fraction(playback.position,
                        entry.duration, playback.updated, playback.speed,
                        playback.state == PlaybackState.STATE_PLAYING, SystemClock.elapsedRealtime()) }
                }
            } else null
            WidgetUpdateSnapshot(state, progress)
        } catch (_: SecurityException) {
            detachSessions()
            unknown
        } catch (_: RuntimeException) {
            // Keep event subscriptions for recovery after a transient vendor/binder failure.
            selectedEntry = null
            selectionDirty = true
            resyncNeeded = true
            watched.values.forEach { it.playback = null; it.duration = 0; it.stateDirty = true; it.durationDirty = true }
            unknown
        }
    }

    private fun attachSessions() {
        val epoch = ++generation
        val active = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            if (running && epoch == generation) {
                pendingControllers = controllers.orEmpty()
                scheduleRefresh(true)
            }
        }
        manager.addOnActiveSessionsChangedListener(active, AndroidSessionGateway.listenerComponent(app), handler)
        activeListener = active
        resyncNeeded = true
        val key = MediaSessionManager.OnMediaKeyEventSessionChangedListener { pkg, token ->
            if (running && epoch == generation) {
                // The callback itself supplies both hints; enumerate only to discover an unknown token.
                if (token != null && token !in watched && token != keyToken) resyncNeeded = true
                selectionDirty = true
                keyToken = token
                keyPackage = pkg?.takeIf { it.isNotBlank() }
                scheduleRefresh(true)
            }
        }
        try {
            manager.addOnMediaKeyEventSessionChangedListener(app.mainExecutor, key)
            keyListener = key
        } catch (_: RuntimeException) { /* Active-session and playback events still work without this hint. */ }
    }

    private fun watch(controllers: List<MediaController>) {
        selectionDirty = true
        val tokens = controllers.map { it.sessionToken }.toSet()
        destroyed.retainAll(tokens)
        for (token in watched.keys.toList()) if (token !in tokens) removeEntry(token)
        order = controllers.map { it.sessionToken }.distinct()
        for (controller in controllers) {
            val token = controller.sessionToken
            if (token in destroyed) continue
            val existing = watched[token]
            if (existing != null) { existing.stateDirty = true; existing.durationDirty = true; continue }
            lateinit var entry: Entry
            val callback = object : MediaController.Callback() {
                private fun current() = running && watched[token] === entry
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    if (current()) {
                        entry.playback = Playback.capture(state)
                        entry.stateDirty = false
                        selectionDirty = true
                        scheduleRefresh(true)
                    }
                }
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    if (current()) {
                        entry.duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0
                        entry.durationDirty = false
                        entry.stateDirty = true // A new track may also reset position before its next playback event.
                        selectionDirty = true
                        scheduleRefresh(true)
                    }
                }
                override fun onSessionDestroyed() {
                    if (current()) { destroyed += token; removeEntry(token); scheduleRefresh(true) }
                }
            }
            entry = Entry(controller, callback)
            watched[token] = entry
            try { controller.registerCallback(callback, handler) }
            catch (_: RuntimeException) { watched.remove(token) }
        }
    }

    private fun removeEntry(token: MediaSession.Token) {
        selectionDirty = true
        selectedEntry = null
        watched.remove(token)?.let {
            try { it.controller.unregisterCallback(it.callback) } catch (_: RuntimeException) { /* Already gone. */ }
        }
    }
    private fun detachSessions() {
        ++generation
        for (token in watched.keys.toList()) removeEntry(token)
        activeListener?.let {
            try { manager.removeOnActiveSessionsChangedListener(it) } catch (_: RuntimeException) { /* Grant revoked. */ }
        }
        keyListener?.let {
            try { manager.removeOnMediaKeyEventSessionChangedListener(it) } catch (_: RuntimeException) { /* Grant revoked. */ }
        }
        activeListener = null; keyListener = null
        pendingControllers = null; order = emptyList(); destroyed.clear()
        keyToken = null; keyPackage = null; resyncNeeded = true
        selectedEntry = null; selectionDirty = true
    }

    private fun scheduleRefresh(force: Boolean = false, event: Boolean = true) {
        forceRefresh = forceRefresh || force
        eventPending = eventPending || event
        if (!running || refreshPending) return
        refreshPending = true
        handler.post(refresh)
    }

    private fun emit(snapshot: WidgetUpdateSnapshot, progressOnly: Boolean = false) {
        try {
            if (onChanged != null) onChanged.invoke(snapshot.state)
            else presentation.refresh(app, if (progressOnly) progressIds else ids, snapshot)
            lastState = snapshot.state
            deliveryPending = false
        } catch (_: RuntimeException) {
            deliveryPending = true
            // A failed delivery is retried on the next event; do not mark it current.
        }
    }

    /** Wallpaper changes use listener-owned state; they never request a media-session rescan. */
    internal fun wallpaperSnapshot(): WidgetUpdateSnapshot? {
        if (!running || onChanged != null || resyncNeeded || activeListener == null) return null
        val snapshot = readPresentation()
        if (snapshot.state != lastState) scheduleRefresh()
        return snapshot
    }

    internal fun refreshWallpaper(id: Int, snapshot: WidgetUpdateSnapshot): Boolean {
        if (!running || onChanged != null || id !in ids) return false
        presentation.reload(app, id)
        presentation.refresh(app, listOf(id), snapshot)
        return true
    }

    companion object {
        private const val SETTINGS_CHANGED = "dev.local.mediaactions.WIDGET_PROGRESS_SETTINGS"
        private const val RESYNC_SESSIONS = "resync_sessions"
        fun settingsChanged(context: Context, resyncSessions: Boolean = false) {
            WidgetWallpaperRuntime.get(context)?.settingsChanged()
            context.sendBroadcast(Intent(SETTINGS_CHANGED).setPackage(context.packageName)
                .putExtra(RESYNC_SESSIONS, resyncSessions))
        }
        private fun classify(playback: Playback?): SessionState = when (playback?.state) {
            PlaybackState.STATE_PLAYING, PlaybackState.STATE_FAST_FORWARDING, PlaybackState.STATE_REWINDING -> SessionState.PLAYING
            PlaybackState.STATE_PAUSED -> SessionState.PAUSED
            PlaybackState.STATE_STOPPED -> SessionState.STOPPED
            PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING, PlaybackState.STATE_SKIPPING_TO_NEXT,
            PlaybackState.STATE_SKIPPING_TO_PREVIOUS, PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> SessionState.TRANSITIONING
            else -> SessionState.UNAVAILABLE
        }
    }
    private data class CachedPlayer(override val sessionIdentity: MediaSession.Token,
        override val packageName: String, override val state: SessionState) : PlayerSession {
        override val supportedCommands = emptySet<TransportCommand>()
        override fun send(command: TransportCommand): Unit = error("Presentation entries cannot execute commands")
    }

    override fun close() {
        WidgetWallpaperRuntime.get(app)?.let { if (it.observer?.get() === this) it.observer = null }
        running = false
        handler.removeCallbacks(refresh)
        handler.removeCallbacks(pulse)
        if (receiverRegistered) {
            try { app.unregisterReceiver(visibilityReceiver) } catch (_: RuntimeException) { /* Already gone. */ }
            receiverRegistered = false
        }
        refreshPending = false; forceRefresh = false; eventPending = false; allTargetsPending = false; deliveryPending = false
        detachSessions()
        presentation.retain(emptyList())
        lastState = null
    }
}
