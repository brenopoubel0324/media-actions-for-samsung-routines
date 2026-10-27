package dev.local.mediaactions

import android.service.notification.NotificationListenerService

/** Watches media-session playback only. Never reads, stores, or logs notification content. */
class MediaNotificationListener : NotificationListenerService() {
    private var playbackObserver: WidgetPlaybackObserver? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        playbackObserver?.close()
        playbackObserver = WidgetPlaybackObserver(this).also { it.start() }
    }

    override fun onListenerDisconnected() {
        stopPlayback()
        try {
            if (AndroidSessionGateway(this).hasNotificationAccess()) {
                requestRebind(AndroidSessionGateway.listenerComponent(this))
            }
        } catch (_: RuntimeException) { /* A revoked grant or vendor failure must not crash teardown. */ }
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }

    private fun stopPlayback() {
        playbackObserver?.close()
        playbackObserver = null
        try {
            MediaWidgetProvider.refreshAll(this, WidgetPlaybackState.UNKNOWN)
        } catch (_: RuntimeException) { /* Host failure must not prevent listener cleanup or rebind. */ }
    }
}
