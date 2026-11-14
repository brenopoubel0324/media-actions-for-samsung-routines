package dev.local.mediaactions

/** Installed widget identity. Appearance never changes the commands of an existing instance. */
enum class WidgetKind(val hasPlayback: Boolean, val hasVolume: Boolean, val backupValue: String) {
    MEDIA(true, false, "media"),
    VOLUME(false, true, "volume"),
    PLAYBACK_VOLUME(true, true, "playback_volume"),
}
