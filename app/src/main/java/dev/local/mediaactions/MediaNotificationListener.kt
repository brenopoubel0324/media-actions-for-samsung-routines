package dev.local.mediaactions

import android.service.notification.NotificationListenerService

/** Authorization only. Never reads, stores, or logs notification content. */
class MediaNotificationListener : NotificationListenerService()
