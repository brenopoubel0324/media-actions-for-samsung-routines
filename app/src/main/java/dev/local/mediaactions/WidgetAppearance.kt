package dev.local.mediaactions

import android.content.Context
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

data class WidgetAppearance(
    val iconScalePercent: Int = 100,
    val spacingDp: Int = 0,
    val darkIcons: Boolean = false,
) {
    fun normalized() = copy(iconScalePercent = iconScalePercent.coerceIn(75, 135),
        spacingDp = spacingDp.coerceIn(0, 24))
}

object WidgetStyleStore {
    private const val PREFERENCES = "widget_appearance"

    fun load(context: Context): WidgetAppearance {
        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        return WidgetAppearance(prefs.getInt("scale", 100), prefs.getInt("spacing", 0),
            prefs.getBoolean("dark_icons", false)).normalized()
    }

    fun save(context: Context, appearance: WidgetAppearance) {
        val style = appearance.normalized()
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putInt("scale", style.iconScalePercent).putInt("spacing", style.spacingDp)
            .putBoolean("dark_icons", style.darkIcons).apply()
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
            val gap = floor(min(style.spacingDp.toFloat(), ((width - 144) / 2).coerceAtLeast(0f))).toInt()
            val side = floor(min(height, (width - gap * 2) / 3)).toInt().coerceIn(1, 128)
            val scale = style.iconScalePercent / 100f
            fun padding(fraction: Float) = (side * (1 - min(1f, fraction * scale)) / 2)
                .roundToInt().coerceAtLeast(0)
            return WidgetGeometry(side, gap, padding(0.82f), padding(0.95f))
        }
    }
}
