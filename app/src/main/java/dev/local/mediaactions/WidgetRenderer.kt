package dev.local.mediaactions

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.SizeF
import android.util.TypedValue
import android.widget.RemoteViews

object WidgetRenderer {
    private val buttons = listOf(R.id.widget_previous, R.id.widget_toggle, R.id.widget_next)
    private val frameBorders = intArrayOf(
        R.drawable.widget_border_1, R.drawable.widget_border_2,
        R.drawable.widget_border_3, R.drawable.widget_border_4,
        R.drawable.widget_border_5, R.drawable.widget_border_6,
        R.drawable.widget_border_7, R.drawable.widget_border_8)
    private val buttonBorders = intArrayOf(
        R.drawable.button_border_1, R.drawable.button_border_2,
        R.drawable.button_border_3, R.drawable.button_border_4,
        R.drawable.button_border_5, R.drawable.button_border_6,
        R.drawable.button_border_7, R.drawable.button_border_8)
    private val buttonTouchBorders = intArrayOf(
        R.drawable.button_border_touch_1, R.drawable.button_border_touch_2,
        R.drawable.button_border_touch_3, R.drawable.button_border_touch_4,
        R.drawable.button_border_touch_5, R.drawable.button_border_touch_6,
        R.drawable.button_border_touch_7, R.drawable.button_border_touch_8)
    private val buttonDarkTouchBorders = intArrayOf(
        R.drawable.button_border_touch_dark_1, R.drawable.button_border_touch_dark_2,
        R.drawable.button_border_touch_dark_3, R.drawable.button_border_touch_dark_4,
        R.drawable.button_border_touch_dark_5, R.drawable.button_border_touch_dark_6,
        R.drawable.button_border_touch_dark_7, R.drawable.button_border_touch_dark_8)

    fun render(context: Context, id: Int, options: Bundle,
        playback: WidgetPlaybackState = WidgetPlaybackState.read(AndroidSessionGateway(context))): RemoteViews {
        val style = WidgetStyleStore.load(context, id)
        val sizes = reportedSizes(options)
        if (sizes.isEmpty()) return build(context, id, style, null, playback = playback)
        if (sizes.size == 1) return build(context, id, style, sizes.single(), playback = playback)
        return RemoteViews(sizes.associateWith { build(context, id, style, it, playback = playback) })
    }

    internal fun reportedSizes(options: Bundle): List<SizeF> {
        val exact = options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
            .orEmpty().filter { it.width.isFinite() && it.height.isFinite() && it.width > 0 && it.height > 0 }
            .distinct().take(16)
        if (exact.isNotEmpty()) return exact
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minWidth)
        val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHeight)
        if (minWidth <= 0 || minHeight <= 0) return emptyList()
        return listOf(SizeF(minWidth.toFloat(), maxHeight.coerceAtLeast(minHeight).toFloat()),
            SizeF(maxWidth.coerceAtLeast(minWidth).toFloat(), minHeight.toFloat())).distinct()
    }

    internal fun build(context: Context, id: Int, appearance: WidgetAppearance, size: SizeF?,
        bindControls: Boolean = true, playback: WidgetPlaybackState = WidgetPlaybackState.PAUSED): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_media_controls).apply {
            val style = appearance.normalized()
            val geometry = WidgetGeometry.calculate(size?.width ?: 144f, size?.height ?: 48f, style)
            setInt(R.id.widget_controls, "setBackgroundResource", if (style.widgetBorder)
                frameBorders[style.borderWidthDp - 1] else android.R.color.transparent)
            setColorStateList(R.id.widget_controls, "setBackgroundTintList",
                if (style.widgetBorder) ColorStateList.valueOf(style.borderColor) else null)
            val density = context.resources.displayMetrics.density
            val horizontalPadding = (geometry.widgetPaddingHorizontalDp * density).toInt()
            val verticalPadding = (geometry.widgetPaddingVerticalDp * density).toInt()
            setViewPadding(R.id.widget_controls, horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
            val sides = when (style.iconStyle) {
                WidgetIconStyle.OUTLINE -> R.drawable.ic_widget_outline_previous to R.drawable.ic_widget_outline_next
                WidgetIconStyle.ROUNDED -> R.drawable.ic_widget_rounded_previous to R.drawable.ic_widget_rounded_next
                WidgetIconStyle.CLASSIC -> R.drawable.ic_widget_previous to R.drawable.ic_widget_next
                WidgetIconStyle.LUCIDE -> R.drawable.ic_lucide_previous to R.drawable.ic_lucide_next
                WidgetIconStyle.PHOSPHOR_THIN -> R.drawable.ic_phosphor_previous to R.drawable.ic_phosphor_next
                WidgetIconStyle.MATERIAL_ROUNDED -> R.drawable.ic_material_previous to R.drawable.ic_material_next
                WidgetIconStyle.CIRCULAR -> R.drawable.ic_circle_previous to R.drawable.ic_circle_next
                WidgetIconStyle.CHEVRONS -> R.drawable.ic_chevron_previous to R.drawable.ic_chevron_next
                WidgetIconStyle.DOUBLE_TRIANGLES -> R.drawable.ic_double_previous to R.drawable.ic_double_next
                WidgetIconStyle.SQUARE -> R.drawable.ic_square_previous to R.drawable.ic_square_next
            }
            val icons = listOf(sides.first, WidgetPlaybackIcons.center(style.iconStyle, playback), sides.second)
            setContentDescription(R.id.widget_toggle, context.getString(when (playback) {
                WidgetPlaybackState.PLAYING -> R.string.widget_pause_action
                WidgetPlaybackState.PAUSED -> R.string.widget_play_action
                WidgetPlaybackState.TRANSITIONING -> R.string.widget_transitioning
                WidgetPlaybackState.UNKNOWN -> R.string.widget_state_unknown
            }))
            setInt(R.id.widget_toggle, "setImageAlpha", if (playback == WidgetPlaybackState.TRANSITIONING) 150 else 255)
            val color = if (style.darkIcons) Color.rgb(26, 29, 34) else Color.WHITE
            for (index in buttons.indices) {
                val background = when {
                    style.buttonBorder && style.touchFeedback -> (if (style.darkIcons) buttonDarkTouchBorders else buttonTouchBorders)[style.buttonBorderWidthDp - 1]
                    style.buttonBorder -> buttonBorders[style.buttonBorderWidthDp - 1]
                    !style.touchFeedback -> android.R.color.transparent
                    style.darkIcons -> R.drawable.widget_button_background_dark
                    else -> R.drawable.widget_button_background
                }
                setInt(buttons[index], "setBackgroundResource", background)
                setColorStateList(buttons[index], "setBackgroundTintList",
                    if (style.buttonBorder) ColorStateList.valueOf(style.buttonBorderColor) else null)
                if (style.iconShadow) {
                    setImageViewBitmap(buttons[index], WidgetIconArtwork.shadowed(context, icons[index], color))
                    // Transparent SRC_ATOP preserves the shadow bitmap's original colors.
                    setInt(buttons[index], "setColorFilter", Color.TRANSPARENT)
                } else {
                    setImageViewResource(buttons[index], icons[index])
                    setInt(buttons[index], "setColorFilter", color)
                }
            }
            if (size != null) {
                setViewLayoutWidth(R.id.widget_controls_row, geometry.widthDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
                setViewLayoutHeight(R.id.widget_controls_row, geometry.buttonDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
                for (gap in listOf(R.id.widget_gap_left, R.id.widget_gap_right)) {
                    setViewLayoutWidth(gap, geometry.gapDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
                }
                // Reapply geometry padding after any decorative background inset.
                fun pad(id: Int, dp: Int) {
                    val px = (dp * context.resources.displayMetrics.density).toInt()
                    setViewPadding(id, px, px, px, px)
                }
                pad(R.id.widget_previous, geometry.sidePaddingDp)
                pad(R.id.widget_toggle, geometry.centerPaddingDp)
                pad(R.id.widget_next, geometry.sidePaddingDp)
            } else {
                // A host that reports no dimensions still gets freely scaling transparent controls.
                // Keep gaps collapsed to protect minimum touch targets; icon tuning still applies.
                for (view in listOf(R.id.widget_gap_left, R.id.widget_gap_right)) {
                    setViewLayoutWidth(view, 0f, TypedValue.COMPLEX_UNIT_DIP)
                }
                for (button in buttons) {
                    val dp = if (button == R.id.widget_toggle) geometry.centerPaddingDp else geometry.sidePaddingDp
                    val px = (dp * context.resources.displayMetrics.density).toInt()
                    setViewPadding(button, px, px, px, px)
                }
            }
            if (bindControls) {
                setOnClickPendingIntent(R.id.widget_previous, MediaWidgetProvider.controlIntent(context, id, MediaAction.PREVIOUS))
                setOnClickPendingIntent(R.id.widget_toggle, MediaWidgetProvider.controlIntent(context, id, MediaAction.PLAY_PAUSE))
                setOnClickPendingIntent(R.id.widget_next, MediaWidgetProvider.controlIntent(context, id, MediaAction.NEXT))
            }
        }
}
