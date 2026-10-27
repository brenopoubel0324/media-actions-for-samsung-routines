package dev.local.mediaactions

import android.content.Context
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

enum class WidgetIconStyle {
    OUTLINE, ROUNDED, CLASSIC, LUCIDE, PHOSPHOR_THIN, MATERIAL_ROUNDED,
    CIRCULAR, CHEVRONS, DOUBLE_TRIANGLES, SQUARE,
}

data class WidgetAppearance(
    val iconScalePercent: Int = 100,
    val spacingDp: Int = 0,
    val darkIcons: Boolean = false,
    val iconStyle: WidgetIconStyle = WidgetIconStyle.OUTLINE,
    val fillWidth: Boolean = false,
    val iconShadow: Boolean = false,
    val touchFeedback: Boolean = true,
) {
    fun normalized() = copy(iconScalePercent = iconScalePercent.coerceIn(20, 150),
        spacingDp = spacingDp.coerceIn(0, MAX_SPACING_DP))

    companion object { const val MAX_SPACING_DP = 144 }
}

object WidgetStyleStore {
    private const val PREFERENCES = "widget_appearance"

    private fun name(widgetId: Int?) = if (widgetId == null) PREFERENCES else "${PREFERENCES}_$widgetId"

    fun load(context: Context, widgetId: Int? = null): WidgetAppearance {
        val prefs = context.getSharedPreferences(name(widgetId), Context.MODE_PRIVATE)
        if (widgetId != null && !prefs.getBoolean("has_override", false)) return load(context)
        return WidgetAppearance(prefs.getInt("scale", 100), prefs.getInt("spacing", 0),
            prefs.getBoolean("dark_icons", false),
            WidgetIconStyle.entries.firstOrNull { it.name == prefs.getString("icon_style", null) }
                ?: WidgetIconStyle.OUTLINE,
            prefs.getBoolean("fill_width", false), prefs.getBoolean("icon_shadow", false),
            prefs.getBoolean("touch_feedback", true)).normalized()
    }

    fun save(context: Context, appearance: WidgetAppearance, widgetId: Int? = null) {
        val style = appearance.normalized()
        context.getSharedPreferences(name(widgetId), Context.MODE_PRIVATE).edit()
            .putInt("scale", style.iconScalePercent).putInt("spacing", style.spacingDp)
            .putBoolean("dark_icons", style.darkIcons)
            .putString("icon_style", style.iconStyle.name).putBoolean("fill_width", style.fillWidth)
            .putBoolean("icon_shadow", style.iconShadow)
            .putBoolean("touch_feedback", style.touchFeedback)
            .putBoolean("has_override", widgetId != null).apply()
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
) {
    val widthDp get() = buttonDp * 3 + gapDp * 2

    companion object {
        fun calculate(widthDp: Float, heightDp: Float, appearance: WidgetAppearance): WidgetGeometry {
            val width = widthDp.takeIf { it.isFinite() && it > 0 } ?: 180f
            val height = heightDp.takeIf { it.isFinite() && it > 0 } ?: 56f
            val style = appearance.normalized()
            // Reduce requested gaps before sacrificing 48dp touch targets at minimum size.
            // Automatic spacing ignores the saved manual gap, which is disabled in that mode.
            val requestedGap = if (style.fillWidth) 0f else style.spacingDp.toFloat()
            var gap = floor(min(requestedGap, ((width - 144) / 2).coerceAtLeast(0f))).toInt()
            val side = floor(min(height, (width - gap * 2) / 3)).toInt().coerceIn(1, 128)
            if (style.fillWidth) gap = floor((width - 3 * side) / 2).toInt().coerceAtLeast(0)
            val scale = style.iconScalePercent / 100f
            fun padding(fraction: Float) = (side * (1 - min(1f, fraction * scale)) / 2)
                .roundToInt().coerceAtLeast(0)
            return WidgetGeometry(side, gap, padding(0.82f), padding(0.95f))
        }
    }
}
