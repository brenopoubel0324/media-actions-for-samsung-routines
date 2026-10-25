package dev.local.mediaactions

enum class MediaAction { NEXT, PREVIOUS, PLAY_PAUSE }

/** Exact-match entry point: no arbitrary package names, extras, queries or alternate actions. */
object ShortcutRequest {
    const val VIEW_ACTION = "android.intent.action.VIEW"

    fun parse(action: String?, uri: String?): MediaAction? {
        if (action != VIEW_ACTION) return null
        return when (uri) {
            "mediaactions://run/next" -> MediaAction.NEXT
            "mediaactions://run/previous" -> MediaAction.PREVIOUS
            "mediaactions://run/toggle" -> MediaAction.PLAY_PAUSE
            else -> null
        }
    }
}
