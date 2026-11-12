package dev.local.mediaactions

import android.app.WallpaperColors
import android.graphics.Color
import android.os.Bundle
import kotlin.math.pow

internal enum class WallpaperDestination { HOME, LOCK }

/** Only two contrast bits are cached. No wallpaper images, paths or actionable sessions. */
internal object WidgetWallpaperColors {
    val darkInk: Int = Color.rgb(26, 29, 34)

    fun destination(style: WidgetAppearance, location: WidgetLocation, options: Bundle): WallpaperDestination? =
        when (style.wallpaperTarget) {
            WidgetWallpaperTarget.HOME -> WallpaperDestination.HOME
            WidgetWallpaperTarget.LOCK -> WallpaperDestination.LOCK
            WidgetWallpaperTarget.FOLLOW_LOCATION -> when (
                if (location == WidgetLocation.AUTOMATIC) WidgetLocationStore.hostLocation(options) else location
            ) {
                WidgetLocation.HOME_SCREEN -> WallpaperDestination.HOME
                WidgetLocation.LOCK_SCREEN -> WallpaperDestination.LOCK
                else -> null // LockStar may omit host hints. Let the user choose; never guess.
            }
        }

    fun darkIcons(colors: WallpaperColors?): Boolean? {
        if (colors == null) return null
        if (colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0) return true
        if (colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_THEME != 0) return false
        // A live engine can provide colors without hints. Compare its primary color's contrast.
        fun luminance(color: Int): Double {
            fun channel(value: Int): Double = (value / 255.0).let { if (it <= .04045) it / 12.92 else ((it + .055) / 1.055).pow(2.4) }
            return .2126 * channel(Color.red(color)) + .7152 * channel(Color.green(color)) + .0722 * channel(Color.blue(color))
        }
        val background = luminance(colors.primaryColor.toArgb())
        val ink = luminance(darkInk)
        val darkContrast = (maxOf(background, ink) + .05) / (minOf(background, ink) + .05)
        return darkContrast > 1.05 / (background + .05)
    }

    fun resolve(style: WidgetAppearance, detectedDark: Boolean?): WidgetAppearance {
        if (style.colorMode == WidgetColorMode.FIXED || detectedDark == null) return style
        val ink = if (detectedDark) darkInk else Color.WHITE
        fun border(saved: Int) = (saved and -0x1000000) or (ink and 0x00FFFFFF)
        return style.copy(darkIcons = detectedDark,
            borderColor = if (style.autoBorderColors) border(style.borderColor) else style.borderColor,
            buttonBorderColor = if (style.autoBorderColors) border(style.buttonBorderColor) else style.buttonBorderColor)
    }
}

/** Main-thread state machine. The runtime supplies off-thread reads and coalesced delivery. */
internal class WallpaperColorCoordinator(
    private val subscribe: ((WallpaperDestination, Boolean?) -> Unit) -> Boolean,
    private val unsubscribe: () -> Unit,
    private val read: (WallpaperDestination, (Boolean?) -> Unit) -> Unit,
    private val enqueue: (() -> Unit) -> Unit,
    private val render: (List<Pair<Target, WidgetAppearance>>) -> Set<Int>,
) {
    data class Target(val id: Int, val volume: Boolean, val style: WidgetAppearance, val destination: WallpaperDestination?)
    private data class Preview(val style: WidgetAppearance, val destination: WallpaperDestination?, val changed: () -> Unit)
    private val targets = linkedMapOf<Int, Target>()
    private val previews = linkedMapOf<Any, Preview>()
    private val cache = mutableMapOf<WallpaperDestination, Boolean?>()
    private val revisions = mutableMapOf<WallpaperDestination, Long>()
    private val pending = mutableSetOf<WallpaperDestination>()
    private val previewDelivered = mutableMapOf<Any, Pair<WidgetAppearance, Boolean>>()
    private val delivered = mutableMapOf<Int, WidgetAppearance>()
    private var needed = emptySet<WallpaperDestination>()
    private var generation = 0
    private var subscriptionEpoch = 0
    private var subscribed = false
    private var deliveryFailed = false
    private var flushPending = false
    private var interactive = true

    fun resolve(style: WidgetAppearance, destination: WallpaperDestination?) = WidgetWallpaperColors.resolve(style, cache[destination])
    fun detected(destination: WallpaperDestination?): Boolean? = cache[destination]

    fun reconcile(values: List<Target>) {
        val next = values.filter { it.style.colorMode == WidgetColorMode.WALLPAPER }.associateBy { it.id }
        if (next != targets) {
            generation++; pending.clear()
            delivered.keys.removeAll { targets[it] != next[it] }
            targets.clear(); targets.putAll(next)
        }
        updateConsumers()
    }

    fun preview(token: Any, style: WidgetAppearance?, destination: WallpaperDestination?, changed: () -> Unit = {}) {
        if (style == null || style.colorMode != WidgetColorMode.WALLPAPER) { previews.remove(token); previewDelivered.remove(token) }
        else previews[token] = Preview(style, destination, changed)
        updateConsumers()
    }

    private fun destinations() = (targets.values.mapNotNull { it.destination } + previews.values.mapNotNull { it.destination }).toSet()
    private fun updateConsumers(forceRead: Boolean = false) {
        val wanted = destinations()
        if (wanted != needed) {
            generation++; pending.clear()
            cache.keys.retainAll(wanted); revisions.keys.retainAll(wanted)
            needed = wanted
        }
        if (targets.isEmpty() && previews.isEmpty()) {
            close(); return
        }
        if (wanted.isEmpty() && subscribed) {
            generation++; subscriptionEpoch++; pending.clear(); cache.clear(); revisions.clear()
            unsubscribe(); subscribed = false
        }
        var initialRead = forceRead
        if (wanted.isNotEmpty() && !subscribed) {
            val epoch = ++subscriptionEpoch
            subscribed = subscribe { destination, value ->
                if (subscribed && epoch == subscriptionEpoch && destination in needed) {
                    val changed = !cache.containsKey(destination) || cache[destination] != value
                    revisions[destination] = (revisions[destination] ?: 0L) + 1
                    cache[destination] = value
                    if (changed || deliveryFailed) scheduleFlush()
                }
            }
            initialRead = initialRead || subscribed
        }
        if (interactive) for (destination in wanted) {
            if ((!cache.containsKey(destination) || initialRead) && pending.add(destination)) {
                val epoch = generation
                val revision = revisions[destination] ?: 0L
                read(destination) { value ->
                    if (epoch == generation) {
                        pending.remove(destination)
                        if (revision == (revisions[destination] ?: 0L) && destination in destinations()) cache[destination] = value
                        scheduleFlush()
                    }
                }
            }
        }
        scheduleFlush()
    }

    fun screen(on: Boolean) {
        interactive = on
        if (on) { generation++; pending.clear(); updateConsumers(forceRead = true) }
    }

    private fun scheduleFlush() {
        if (!interactive || flushPending) return
        flushPending = true
        val epoch = generation
        enqueue {
            flushPending = false
            if (interactive && epoch == generation) {
                val changes = targets.values.mapNotNull { target ->
                    if (target.destination != null && !cache.containsKey(target.destination)) null
                    else resolve(target.style, target.destination).let { style ->
                        if (delivered[target.id] == style) null else target to style
                    }
                }
                if (changes.isNotEmpty()) {
                    val successful = render(changes)
                    deliveryFailed = changes.any { it.first.id !in successful }
                    changes.forEach { (target, style) -> if (target.id in successful) delivered[target.id] = style }
                } else deliveryFailed = false
                previews.toMap().forEach { (token, preview) ->
                    val key = resolve(preview.style, preview.destination) to (cache[preview.destination] != null)
                    if (previewDelivered[token] != key) {
                        previewDelivered[token] = key
                        preview.changed()
                    }
                }
            } else if (interactive && (targets.isNotEmpty() || previews.isNotEmpty())) scheduleFlush()
        }
    }

    fun close() {
        generation++; subscriptionEpoch++
        if (subscribed) unsubscribe()
        subscribed = false; deliveryFailed = false; pending.clear(); cache.clear(); revisions.clear(); delivered.clear()
        targets.clear(); previews.clear(); previewDelivered.clear(); needed = emptySet()
    }
}
