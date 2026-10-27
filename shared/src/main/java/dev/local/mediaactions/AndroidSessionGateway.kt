package dev.local.mediaactions

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState

class AndroidSessionGateway(context: Context) : SessionGateway {
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val listener = listenerComponent(context)

    override fun hasNotificationAccess(): Boolean =
        notifications.isNotificationListenerAccessGranted(listener)

    override fun snapshot(): SessionSnapshot {
        val controllers = sessions.getActiveSessions(listener)
        val players = controllers.map(::AndroidPlayerSession)
        // A hint failure must not prevent the documented playing/unique-session fallback.
        val keyToken = try { sessions.mediaKeyEventSession } catch (_: RuntimeException) { null }
        val keyPackage = try {
            sessions.mediaKeyEventSessionPackageName.takeIf { it.isNotBlank() }
        } catch (_: RuntimeException) { null }
        val keyIndex = controllers.indexOfFirst { it.sessionToken == keyToken }
        return SessionSnapshot(players, players.getOrNull(keyIndex), keyPackage)
    }

    companion object {
        fun listenerComponent(context: Context) =
            ComponentName(context.packageName, "${context.packageName}.MediaNotificationListener")
    }
}

private class AndroidPlayerSession(private val controller: MediaController) : PlayerSession {
    // Snapshot state once; do not choose a command from two inconsistent state reads.
    private val playback = controller.playbackState
    override val sessionIdentity: Any = controller.sessionToken
    override val packageName: String = controller.packageName
    override val state = when (playback?.state) {
        PlaybackState.STATE_PLAYING,
        PlaybackState.STATE_FAST_FORWARDING,
        PlaybackState.STATE_REWINDING -> SessionState.PLAYING
        PlaybackState.STATE_PAUSED -> SessionState.PAUSED
        PlaybackState.STATE_STOPPED -> SessionState.STOPPED
        PlaybackState.STATE_BUFFERING,
        PlaybackState.STATE_CONNECTING,
        PlaybackState.STATE_SKIPPING_TO_NEXT,
        PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
        PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> SessionState.TRANSITIONING
        else -> SessionState.UNAVAILABLE
    }
    override val supportedCommands: Set<TransportCommand> = buildSet {
        val actions = playback?.actions ?: 0L
        if (actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L) add(TransportCommand.NEXT)
        if (actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L) add(TransportCommand.PREVIOUS)
        if (actions and PlaybackState.ACTION_PLAY != 0L) add(TransportCommand.PLAY)
        if (actions and PlaybackState.ACTION_PAUSE != 0L) add(TransportCommand.PAUSE)
    }

    override fun send(command: TransportCommand) {
        val controls = controller.transportControls
        when (command) {
            TransportCommand.NEXT -> controls.skipToNext()
            TransportCommand.PREVIOUS -> controls.skipToPrevious()
            TransportCommand.PLAY -> controls.play()
            TransportCommand.PAUSE -> controls.pause()
        }
    }
}
