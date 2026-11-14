package dev.local.mediaactions

import android.app.WallpaperColors
import android.graphics.Color
import android.os.Bundle
import kotlin.math.pow

internal enum class WallpaperDestination { HOME, LOCK }

/** Bounded color metadata only. No wallpaper images, paths or actionable sessions. */
internal data class WallpaperColorReading(val darkIcons: Boolean?, val primaryColor: Int? = null,
    val hints: Int? = null, val errorClass: String? = null) {
    companion object {
        fun fromColors(colors: WallpaperColors?) = WallpaperColorReading(WidgetWallpaperColors.darkIcons(colors),
            colors?.primaryColor?.toArgb(), colors?.colorHints)
    }
}

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
        if (style.colorMode == WidgetColorMode.FIXED) return style
        val effectiveDark = detectedDark ?: style.darkIcons
        val ink = if (effectiveDark) darkInk else Color.WHITE
        fun border(saved: Int) = (saved and -0x1000000) or (ink and 0x00FFFFFF)
        return style.copy(darkIcons = effectiveDark,
            borderColor = if (style.autoBorderColors) border(style.borderColor) else style.borderColor,
            buttonBorderColor = if (style.autoBorderColors) border(style.buttonBorderColor) else style.buttonBorderColor)
    }
}

/** Main-thread state machine. The runtime supplies off-thread reads and coalesced delivery. */
internal class WallpaperColorCoordinator(
    private val subscribe: ((WallpaperDestination, WallpaperColorReading) -> Unit) -> Boolean,
    private val unsubscribe: () -> Unit,
    private val read: (WallpaperDestination, (WallpaperColorReading) -> Unit) -> Unit,
    private val enqueue: (() -> Unit) -> Unit,
    private val render: (List<Pair<Target, WidgetAppearance>>) -> Set<Int>,
) {
    data class Target(val id: Int, val kind: WidgetKind, val style: WidgetAppearance, val destination: WallpaperDestination?)
    private data class Preview(val style: WidgetAppearance, val destination: WallpaperDestination?, val changed: () -> Unit)
    private val targets = linkedMapOf<Int, Target>()
    private val previews = linkedMapOf<Any, Preview>()
    private val cache = mutableMapOf<WallpaperDestination, WallpaperColorReading>()
    private val revisions = mutableMapOf<WallpaperDestination, Long>()
    private val pending = mutableSetOf<WallpaperDestination>()
    private data class PreviewKey(val style: WidgetAppearance, val reading: WallpaperColorReading?, val pending: Boolean)
    private val previewDelivered = mutableMapOf<Any, PreviewKey>()
    private val delivered = mutableMapOf<Int, WidgetAppearance>()
    private var needed = emptySet<WallpaperDestination>()
    private var generation = 0
    private var subscriptionEpoch = 0
    private var subscribed = false
    private var deliveryFailed = false
    private var flushPending = false
    private var interactive = true

    fun resolve(style: WidgetAppearance, destination: WallpaperDestination?) = WidgetWallpaperColors.resolve(style, cache[destination]?.darkIcons)
    fun detected(destination: WallpaperDestination?): Boolean? = cache[destination]?.darkIcons
    fun reading(destination: WallpaperDestination?): WallpaperColorReading? = cache[destination]
    fun isReading(destination: WallpaperDestination?): Boolean = destination in pending
    fun isObserving(): Boolean = subscribed
    fun recheck(destination: WallpaperDestination? = null) {
        if (destination == null) {
            if (pending.isEmpty()) updateConsumers(forceRead = true)
        } else if (destination in destinations() && destination !in pending) updateConsumers(reread = destination)
    }

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
    private fun updateConsumers(forceRead: Boolean = false, reread: WallpaperDestination? = null) {
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
                    val changed = !cache.containsKey(destination) || cache[destination]?.darkIcons != value.darkIcons
                    val previewChanged = cache[destination] != value && previews.values.any { it.destination == destination }
                    revisions[destination] = (revisions[destination] ?: 0L) + 1
                    cache[destination] = value
                    if (changed || previewChanged || deliveryFailed) scheduleFlush()
                }
            }
            initialRead = initialRead || subscribed
        }
        if (interactive) for (destination in wanted) {
            if ((!cache.containsKey(destination) || initialRead || destination == reread) && pending.add(destination)) {
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
                    val key = PreviewKey(resolve(preview.style, preview.destination), cache[preview.destination], preview.destination in pending)
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
