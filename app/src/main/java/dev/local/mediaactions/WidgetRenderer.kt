package dev.local.mediaactions

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
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
    private val compactBorders = intArrayOf(R.drawable.widget_compact_border_1,R.drawable.widget_compact_border_2,R.drawable.widget_compact_border_3,R.drawable.widget_compact_border_4,R.drawable.widget_compact_border_5,R.drawable.widget_compact_border_6,R.drawable.widget_compact_border_7,R.drawable.widget_compact_border_8)
    private val cornerBorders = mapOf(
        0 to intArrayOf(R.drawable.widget_frame_radius_0_1,R.drawable.widget_frame_radius_0_2,R.drawable.widget_frame_radius_0_3,R.drawable.widget_frame_radius_0_4,R.drawable.widget_frame_radius_0_5,R.drawable.widget_frame_radius_0_6,R.drawable.widget_frame_radius_0_7,R.drawable.widget_frame_radius_0_8),
        8 to intArrayOf(R.drawable.widget_frame_radius_8_1,R.drawable.widget_frame_radius_8_2,R.drawable.widget_frame_radius_8_3,R.drawable.widget_frame_radius_8_4,R.drawable.widget_frame_radius_8_5,R.drawable.widget_frame_radius_8_6,R.drawable.widget_frame_radius_8_7,R.drawable.widget_frame_radius_8_8),
        24 to intArrayOf(R.drawable.widget_frame_radius_24_1,R.drawable.widget_frame_radius_24_2,R.drawable.widget_frame_radius_24_3,R.drawable.widget_frame_radius_24_4,R.drawable.widget_frame_radius_24_5,R.drawable.widget_frame_radius_24_6,R.drawable.widget_frame_radius_24_7,R.drawable.widget_frame_radius_24_8),
        32 to intArrayOf(R.drawable.widget_frame_radius_32_1,R.drawable.widget_frame_radius_32_2,R.drawable.widget_frame_radius_32_3,R.drawable.widget_frame_radius_32_4,R.drawable.widget_frame_radius_32_5,R.drawable.widget_frame_radius_32_6,R.drawable.widget_frame_radius_32_7,R.drawable.widget_frame_radius_32_8)
    )
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
        snapshot: WidgetUpdateSnapshot? = null,
        appearance: WidgetAppearance? = null): RemoteViews {
        val requested = appearance ?: WidgetStyleStore.load(context, id)
        val style = WidgetWallpaperRuntime.get(context)?.resolve(id, requested, options) ?: requested
        val data = snapshot ?: WidgetUpdateSnapshot.capture(AndroidSessionGateway(context), style.progressRing)
        val state = data.state
        val showProgress = shouldShowProgress(style, state)
        val progress = if (showProgress) data.progress else null
        val progressBitmap = if (showProgress) WidgetProgressArtwork.draw(context,
            WidgetPlaybackIcons.center(style.iconStyle, state),
            if (style.darkIcons) Color.rgb(26, 29, 34) else Color.WHITE, style, progress) else null
        val sizes = reportedSizes(options)
        if (sizes.isEmpty()) return build(context, id, style, null, playback = state, progress = progress, progressBitmap = progressBitmap)
        if (sizes.size == 1) return build(context, id, style, sizes.single(), playback = state, progress = progress, progressBitmap = progressBitmap)
        return RemoteViews(sizes.associateWith { build(context, id, style, it, playback = state, progress = progress, progressBitmap = progressBitmap) })
    }

    private fun shouldShowProgress(style: WidgetAppearance, playback: WidgetPlaybackState) =
        style.progressRing && playback == WidgetPlaybackState.PLAYING

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
        bindControls: Boolean = true, playback: WidgetPlaybackState = WidgetPlaybackState.PAUSED,
        progress: WidgetTrackProgress? = null, progressBitmap: Bitmap? = null, volume: Boolean = false): RemoteViews =
        RemoteViews(context.packageName, if(volume) {if(VolumeWidgetLayout.isVertical(size)) R.layout.widget_volume_vertical else R.layout.widget_volume_controls} else R.layout.widget_media_controls).apply {
            val style = (if(volume) appearance.copy(progressRing=false,centerBorderOnly=false) else appearance).normalized()
            val buttons = if(volume) listOf(R.id.widget_previous,R.id.widget_next) else buttons
            val gaps = if(volume) listOf(R.id.widget_gap_left) else listOf(R.id.widget_gap_left,R.id.widget_gap_right)
            val showProgress = shouldShowProgress(style, playback)
            val content = WidgetContentLayout.calculate(size, style, volume)
            val geometry = content.geometry
            val compact = style.widgetBorder && style.frameMode == WidgetFrameMode.COMPACT
            val border = if (style.frameCorner == WidgetFrameCorner.AUTOMATIC)
                (if (compact) compactBorders else frameBorders)[style.borderWidthDp - 1]
                else (cornerBorders[style.frameCorner.radiusDp] ?: frameBorders)[style.borderWidthDp - 1]
            setInt(R.id.widget_controls, "setBackgroundResource", if (style.widgetBorder && !compact)
                border else android.R.color.transparent)
            setColorStateList(R.id.widget_controls, "setBackgroundTintList",
                if (style.widgetBorder && !compact) ColorStateList.valueOf(style.borderColor) else null)
            setInt(R.id.widget_frame, "setBackgroundResource", if (compact) border else android.R.color.transparent)
            setColorStateList(R.id.widget_frame, "setBackgroundTintList", if (compact) ColorStateList.valueOf(style.borderColor) else null)
            setViewLayoutHeight(R.id.widget_frame, if (compact) -2f else -1f, TypedValue.COMPLEX_UNIT_PX)
            setInt(R.id.widget_frame, "setMinimumHeight", if (compact)
                ((if (content.vertical) maxOf(style.frameHeightDp, content.rowHeight + style.borderWidthDp * 2) else style.frameHeightDp)
                    .coerceAtMost(size?.height?.toInt()?.coerceAtLeast(1) ?: Int.MAX_VALUE) * context.resources.displayMetrics.density).toInt() else 0)
            setViewLayoutWidth(R.id.widget_frame, -1f, TypedValue.COMPLEX_UNIT_PX)
            setInt(R.id.widget_frame, "setMinimumWidth", 0)
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
            val icons = if(volume) listOf(R.drawable.ic_volume_minus,R.drawable.ic_volume_plus) else listOf(sides.first, WidgetPlaybackIcons.center(style.iconStyle, playback), sides.second)
            if(!volume) {
            setContentDescription(R.id.widget_toggle, context.getString(when (playback) {
                WidgetPlaybackState.PLAYING -> R.string.widget_pause_action
                WidgetPlaybackState.PAUSED -> R.string.widget_play_action
                WidgetPlaybackState.TRANSITIONING -> R.string.widget_transitioning
                WidgetPlaybackState.UNKNOWN -> R.string.widget_state_unknown
            }))
            setInt(R.id.widget_toggle, "setImageAlpha", if (playback == WidgetPlaybackState.TRANSITIONING) 150 else 255)
            } else {
                setContentDescription(R.id.widget_previous,context.getString(R.string.volume_lower))
                setContentDescription(R.id.widget_next,context.getString(R.string.volume_raise))
            }
            val color = if (style.darkIcons) Color.rgb(26, 29, 34) else Color.WHITE
            for (index in buttons.indices) {
                val hasBorder = style.buttonBorder && playback != WidgetPlaybackState.UNKNOWN &&
                    (!style.centerBorderOnly || index == 1) &&
                    !(index == 1 && showProgress)
                val background = when {
                    hasBorder && style.touchFeedback -> (if (style.darkIcons) buttonDarkTouchBorders else buttonTouchBorders)[style.buttonBorderWidthDp - 1]
                    hasBorder -> buttonBorders[style.buttonBorderWidthDp - 1]
                    !style.touchFeedback -> android.R.color.transparent
                    style.darkIcons -> R.drawable.widget_button_background_dark
                    else -> R.drawable.widget_button_background
                }
                setInt(buttons[index], "setBackgroundResource", background)
                setColorStateList(buttons[index], "setBackgroundTintList",
                    if (hasBorder) ColorStateList.valueOf(style.buttonBorderColor) else null)
                if (index == 1 && showProgress) {
                    setImageViewBitmap(buttons[index], progressBitmap ?: WidgetProgressArtwork.draw(context, icons[index], color, style, progress))
                    setInt(buttons[index], "setColorFilter", Color.TRANSPARENT)
                } else if (style.iconShadow) {
                    setImageViewBitmap(buttons[index], WidgetIconArtwork.shadowed(context, icons[index], color))
                    // Transparent SRC_ATOP preserves the shadow bitmap's original colors.
                    setInt(buttons[index], "setColorFilter", Color.TRANSPARENT)
                } else {
                    setImageViewResource(buttons[index], icons[index])
                    setInt(buttons[index], "setColorFilter", color)
                }
            }
            if (size != null) {
                setViewLayoutWidth(R.id.widget_controls_row, content.rowWidth.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
                setViewLayoutHeight(R.id.widget_controls_row, content.rowHeight.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
                for (gap in gaps) {
                    if(content.vertical) setViewLayoutHeight(gap,geometry.gapDp.toFloat(),TypedValue.COMPLEX_UNIT_DIP)
                    else setViewLayoutWidth(gap, geometry.gapDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
                }
                // Reapply geometry padding after any decorative background inset.
                fun pad(id: Int, dp: Int) {
                    val px = (dp * context.resources.displayMetrics.density).toInt()
                    setViewPadding(id, px, px, px, px)
                }
                pad(R.id.widget_previous, geometry.sidePaddingDp)
                if(!volume) pad(R.id.widget_toggle, geometry.centerPaddingDp)
                pad(R.id.widget_next, geometry.sidePaddingDp)
            } else {
                setViewLayoutHeight(R.id.widget_controls_row, if (compact) content.rowHeight.toFloat() else -1f,
                    if (compact) TypedValue.COMPLEX_UNIT_DIP else TypedValue.COMPLEX_UNIT_PX)
                setViewLayoutWidth(R.id.widget_controls_row, -1f, TypedValue.COMPLEX_UNIT_PX)
                // A host that reports no dimensions still gets freely scaling transparent controls.
                // Keep gaps collapsed to protect minimum touch targets; icon tuning still applies.
                for (view in gaps) {
                    setViewLayoutWidth(view, 0f, TypedValue.COMPLEX_UNIT_DIP)
                }
                for (button in buttons) {
                    val dp = if (!volume && button == R.id.widget_toggle) geometry.centerPaddingDp else geometry.sidePaddingDp
                    val px = (dp * context.resources.displayMetrics.density).toInt()
                    setViewPadding(button, px, px, px, px)
                }
            }
            if (showProgress) setViewPadding(R.id.widget_toggle, 0, 0, 0, 0)
            if(bindControls && volume) {
                setOnClickPendingIntent(R.id.widget_previous,VolumeWidgetProvider.controlIntent(context,id,false))
                setOnClickPendingIntent(R.id.widget_next,VolumeWidgetProvider.controlIntent(context,id,true))
            }
            if (bindControls && !volume) {
                setOnClickPendingIntent(R.id.widget_previous, MediaWidgetProvider.controlIntent(context, id, MediaAction.PREVIOUS))
                setOnClickPendingIntent(R.id.widget_toggle, MediaWidgetProvider.controlIntent(context, id, MediaAction.PLAY_PAUSE))
                setOnClickPendingIntent(R.id.widget_next, MediaWidgetProvider.controlIntent(context, id, MediaAction.NEXT))
            }
        }
}
