package dev.local.mediaactions

import android.content.Context
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

enum class WidgetIconStyle {
    OUTLINE, ROUNDED, CLASSIC, LUCIDE, PHOSPHOR_THIN, MATERIAL_ROUNDED,
    CIRCULAR, CHEVRONS, DOUBLE_TRIANGLES, SQUARE,
}

enum class WidgetColorMode { FIXED, WALLPAPER }
enum class WidgetWallpaperTarget { FOLLOW_LOCATION, HOME, LOCK }

enum class WidgetFrameMode { FILL, COMPACT, FIT_BUTTONS }

enum class WidgetFrameCorner(val radiusDp: Int, val label: Int) {
    AUTOMATIC(-1, R.string.widget_corner_auto), SQUARE(0, R.string.widget_corner_square),
    SOFT(8, R.string.widget_corner_soft), MEDIUM(16, R.string.widget_corner_medium),
    ROUND(24, R.string.widget_corner_round), EXTRA_ROUND(32, R.string.widget_corner_extra),
}

data class WidgetAppearance(
    val iconScalePercent: Int = 100,
    val spacingDp: Int = 0,
    val darkIcons: Boolean = false,
    val iconStyle: WidgetIconStyle = WidgetIconStyle.OUTLINE,
    val fillWidth: Boolean = false,
    val iconShadow: Boolean = false,
    val touchFeedback: Boolean = true,
    val widgetBorder: Boolean = false,
    val borderWidthDp: Int = 1,
    val borderColor: Int = android.graphics.Color.WHITE,
    val buttonBorder: Boolean = false,
    val buttonBorderWidthDp: Int = 1,
    val buttonBorderColor: Int = android.graphics.Color.WHITE,
    val widgetPaddingHorizontalDp: Int = 0,
    val widgetPaddingVerticalDp: Int = 0,
    val centerBorderOnly: Boolean = false,
    val progressRing: Boolean = false,
    val frameMode: WidgetFrameMode = WidgetFrameMode.FILL,
    val frameHeightDp: Int = 64,
    val frameCorner: WidgetFrameCorner = WidgetFrameCorner.AUTOMATIC,
    val colorMode: WidgetColorMode = WidgetColorMode.FIXED,
    val wallpaperTarget: WidgetWallpaperTarget = WidgetWallpaperTarget.FOLLOW_LOCATION,
    val autoBorderColors: Boolean = false,
) {
    fun normalized() = copy(iconScalePercent = iconScalePercent.coerceIn(20, 150),
        spacingDp = spacingDp.coerceIn(0, MAX_SPACING_DP),
        borderWidthDp = borderWidthDp.coerceIn(1, MAX_BORDER_DP),
        buttonBorderWidthDp = buttonBorderWidthDp.coerceIn(1, MAX_BORDER_DP),
        widgetPaddingHorizontalDp = widgetPaddingHorizontalDp.coerceIn(0, MAX_PADDING_DP),
        widgetPaddingVerticalDp = widgetPaddingVerticalDp.coerceIn(MIN_PADDING_DP, MAX_PADDING_DP),
        frameHeightDp = frameHeightDp.coerceIn(56, 160))

    companion object {
        const val MAX_SPACING_DP = 144
        const val MAX_BORDER_DP = 8
        const val MAX_PADDING_DP = 144
        const val MIN_PADDING_DP = -24
    }
}

object WidgetStyleStore {
    private const val PREFERENCES = "widget_appearance"

    private fun name(widgetId: Int?) = if (widgetId == null) PREFERENCES else "${PREFERENCES}_$widgetId"

    fun hasOverride(context: Context, widgetId: Int): Boolean =
        context.getSharedPreferences(name(widgetId), Context.MODE_PRIVATE).getBoolean("has_override", false)

    fun load(context: Context, widgetId: Int? = null): WidgetAppearance {
        val prefs = context.getSharedPreferences(name(widgetId), Context.MODE_PRIVATE)
        if (widgetId != null && !prefs.getBoolean("has_override", false)) return load(context)
        return WidgetAppearance(prefs.getInt("scale", 100), prefs.getInt("spacing", 0),
            prefs.getBoolean("dark_icons", false),
            WidgetIconStyle.entries.firstOrNull { it.name == prefs.getString("icon_style", null) }
                ?: WidgetIconStyle.OUTLINE,
            prefs.getBoolean("fill_width", false), prefs.getBoolean("icon_shadow", false),
            prefs.getBoolean("touch_feedback", true), prefs.getBoolean("widget_border", false),
            prefs.getInt("border_width", 1), prefs.getInt("border_color", android.graphics.Color.WHITE),
            prefs.getBoolean("button_border", false), prefs.getInt("button_border_width", 1),
            prefs.getInt("button_border_color", android.graphics.Color.WHITE),
            prefs.getInt("widget_padding_horizontal", 0), prefs.getInt("widget_padding_vertical", 0), prefs.getBoolean("center_border_only", false),
            prefs.getBoolean("progress_ring", false),
            WidgetFrameMode.entries.firstOrNull { it.name == prefs.getString("frame_mode", null) } ?: WidgetFrameMode.FILL,
            prefs.getInt("frame_height", 64),
            WidgetFrameCorner.entries.firstOrNull { it.name == prefs.getString("frame_corner", null) } ?: WidgetFrameCorner.AUTOMATIC,
            WidgetColorMode.entries.firstOrNull { it.name == prefs.getString("color_mode", null) } ?: WidgetColorMode.FIXED,
            WidgetWallpaperTarget.entries.firstOrNull { it.name == prefs.getString("wallpaper_target", null) } ?: WidgetWallpaperTarget.FOLLOW_LOCATION,
            prefs.getBoolean("auto_border_colors", false)).normalized()
    }

    fun save(context: Context, appearance: WidgetAppearance, widgetId: Int? = null) {
        editor(context, appearance, widgetId).apply()
    }

    internal fun saveDurably(context: Context, appearance: WidgetAppearance?, widgetId: Int? = null): Boolean {
        require(appearance != null || widgetId != null)
        val edit = if (appearance != null) editor(context, appearance, widgetId)
            else context.getSharedPreferences(name(widgetId), Context.MODE_PRIVATE).edit().clear()
        return edit.commit() && if (appearance != null) load(context, widgetId) == appearance.normalized()
            else !hasOverride(context, widgetId!!)
    }

    private fun editor(context: Context, appearance: WidgetAppearance, widgetId: Int?): android.content.SharedPreferences.Editor {
        val style = appearance.normalized()
        return context.getSharedPreferences(name(widgetId), Context.MODE_PRIVATE).edit()
            .putInt("scale", style.iconScalePercent).putInt("spacing", style.spacingDp)
            .putBoolean("dark_icons", style.darkIcons)
            .putString("icon_style", style.iconStyle.name).putBoolean("fill_width", style.fillWidth)
            .putBoolean("icon_shadow", style.iconShadow)
            .putBoolean("touch_feedback", style.touchFeedback)
            .putBoolean("widget_border", style.widgetBorder)
            .putInt("border_width", style.borderWidthDp).putInt("border_color", style.borderColor)
            .putBoolean("button_border", style.buttonBorder)
            .putInt("button_border_width", style.buttonBorderWidthDp).putInt("button_border_color", style.buttonBorderColor)
            .putInt("widget_padding_horizontal", style.widgetPaddingHorizontalDp)
            .putInt("widget_padding_vertical", style.widgetPaddingVerticalDp)
            .putBoolean("center_border_only", style.centerBorderOnly)
            .putBoolean("progress_ring", style.progressRing)
            .putString("frame_mode", style.frameMode.name).putInt("frame_height", style.frameHeightDp).putString("frame_corner", style.frameCorner.name)
            .putString("color_mode", style.colorMode.name).putString("wallpaper_target", style.wallpaperTarget.name)
            .putBoolean("auto_border_colors", style.autoBorderColors)
            .putBoolean("has_override", widgetId != null)
    }

    fun remove(context: Context, widgetId: Int) {
        context.deleteSharedPreferences(name(widgetId))
    }
}

/** Keep a compact centered group; grow the controls instead of stretching empty gaps. */
data class WidgetGeometry(
    val buttonDp: Int,
    val gapDp: Int,
    val sidePaddingDp: Int,
    val centerPaddingDp: Int,
    val widgetPaddingHorizontalDp: Int = 0,
    val widgetPaddingVerticalDp: Int = 0,
    val buttonCount: Int = 3,
) {
    val widthDp get() = buttonDp * buttonCount + gapDp * (buttonCount - 1)

    companion object {
        fun calculate(widthDp: Float, heightDp: Float, appearance: WidgetAppearance, buttonCount: Int = 3): WidgetGeometry {
            require(buttonCount in 2..3)
            val gaps = buttonCount - 1
            val minimumWidth = 48 * buttonCount
            val width = widthDp.takeIf { it.isFinite() && it > 0 } ?: 180f
            val height = heightDp.takeIf { it.isFinite() && it > 0 } ?: 56f
            val style = appearance.normalized()
            // Padding cannot consume the minimum 48dp targets or its 48dp height.
            val horizontalPadding = min(style.widgetPaddingHorizontalDp,
                floor(((width - minimumWidth) / 2).coerceAtLeast(0f)).toInt())
            val verticalPadding = min(style.widgetPaddingVerticalDp,
                floor(((height - 48) / 2).coerceAtLeast(0f)).toInt())
            val contentWidth = width - horizontalPadding * 2
            val contentHeight = height - verticalPadding * 2
            // Reduce requested gaps before sacrificing 48dp touch targets at minimum size.
            // Automatic spacing ignores the saved manual gap, which is disabled in that mode.
            val requestedGap = if (style.fillWidth) 0f else style.spacingDp.toFloat()
            var gap = floor(min(requestedGap, ((contentWidth - minimumWidth) / gaps).coerceAtLeast(0f))).toInt()
            val side = floor(min(contentHeight, (contentWidth - gap * gaps) / buttonCount)).toInt().coerceIn(1, 128)
            if (style.fillWidth) gap = floor((contentWidth - buttonCount * side) / gaps).toInt().coerceAtLeast(0)
            val scale = style.iconScalePercent / 100f
            fun padding(fraction: Float) = (side * (1 - min(1f, fraction * scale)) / 2)
                .roundToInt().coerceAtLeast(0)
            return WidgetGeometry(side, gap, padding(0.82f), padding(0.95f), horizontalPadding, verticalPadding, buttonCount)
        }
    }
}
