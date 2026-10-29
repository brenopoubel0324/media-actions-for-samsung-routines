package dev.local.mediaactions

import android.graphics.Color
import android.os.Bundle

/** Temporary Activity state only. Keeps the existing draft keys; preferences use WidgetStyleStore. */
internal object WidgetAppearanceState {
    fun write(style: WidgetAppearance) = Bundle().apply {
        putInt("scale", style.iconScalePercent); putInt("gap", style.spacingDp)
        putBoolean("dark", style.darkIcons); putString("style", style.iconStyle.name)
        putBoolean("width", style.fillWidth); putBoolean("shadow", style.iconShadow)
        putBoolean("feedback", style.touchFeedback)
        putBoolean("border", style.widgetBorder); putInt("border_width", style.borderWidthDp)
        putInt("border_color", style.borderColor)
        putBoolean("button_border", style.buttonBorder); putInt("button_border_width", style.buttonBorderWidthDp)
        putInt("button_border_color", style.buttonBorderColor)
        putInt("widget_padding_horizontal", style.widgetPaddingHorizontalDp)
        putInt("widget_padding_vertical", style.widgetPaddingVerticalDp)
        putBoolean("center_border_only", style.centerBorderOnly); putBoolean("progress_ring", style.progressRing)
    }

    fun read(state: Bundle?): WidgetAppearance? = state?.let {
        WidgetAppearance(
            iconScalePercent = it.getInt("scale", 100), spacingDp = it.getInt("gap", 0),
            darkIcons = it.getBoolean("dark", false), iconStyle = WidgetIconStyle.entries.firstOrNull { style ->
                style.name == it.getString("style")
            } ?: WidgetIconStyle.OUTLINE,
            fillWidth = it.getBoolean("width", false), iconShadow = it.getBoolean("shadow", false),
            touchFeedback = it.getBoolean("feedback", true), widgetBorder = it.getBoolean("border", false),
            borderWidthDp = it.getInt("border_width", 1), borderColor = it.getInt("border_color", Color.WHITE),
            buttonBorder = it.getBoolean("button_border", false), buttonBorderWidthDp = it.getInt("button_border_width", 1),
            buttonBorderColor = it.getInt("button_border_color", Color.WHITE),
            widgetPaddingHorizontalDp = it.getInt("widget_padding_horizontal", 0),
            widgetPaddingVerticalDp = it.getInt("widget_padding_vertical", 0),
            centerBorderOnly = it.getBoolean("center_border_only", false), progressRing = it.getBoolean("progress_ring", false),
        ).normalized()
    }
}
