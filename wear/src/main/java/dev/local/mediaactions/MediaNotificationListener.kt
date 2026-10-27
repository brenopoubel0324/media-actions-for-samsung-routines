package dev.local.mediaactions

import android.service.notification.NotificationListenerService

/** Authorization for local media sessions only; no notification content is inspected. */
class MediaNotificationListener : NotificationListenerService() {
    override fun onListenerDisconnected() {
        try { if (AndroidSessionGateway(this).hasNotificationAccess()) requestRebind(AndroidSessionGateway.listenerComponent(this)) }
        catch (_: RuntimeException) { /* Permission may have been revoked. */ }
        super.onListenerDisconnected()
    }
}
