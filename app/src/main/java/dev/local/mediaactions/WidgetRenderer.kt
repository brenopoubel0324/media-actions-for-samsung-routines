package dev.local.mediaactions

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.SizeF
import android.util.TypedValue
import android.widget.RemoteViews

object WidgetRenderer {
    private val buttons = listOf(R.id.widget_previous, R.id.widget_toggle, R.id.widget_next)

    fun render(context: Context, id: Int, options: Bundle): RemoteViews {
        val style = WidgetStyleStore.load(context)
        val sizes = reportedSizes(options)
        if (sizes.isEmpty()) return build(context, id, style, null)
        if (sizes.size == 1) return build(context, id, style, sizes.single())
        return RemoteViews(sizes.associateWith { build(context, id, style, it) })
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
        bindControls: Boolean = true): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_media_controls).apply {
            val style = appearance.normalized()
            val color = if (style.darkIcons) Color.rgb(26, 29, 34) else Color.WHITE
            for (button in buttons) setInt(button, "setColorFilter", color)
            if (size != null) {
                val geometry = WidgetGeometry.calculate(size.width, size.height, style)
                setViewLayoutWidth(R.id.widget_controls_row, geometry.widthDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
                setViewLayoutHeight(R.id.widget_controls_row, geometry.buttonDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
                for (gap in listOf(R.id.widget_gap_left, R.id.widget_gap_right)) {
                    setViewLayoutWidth(gap, geometry.gapDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
                }
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
                val fallback = WidgetGeometry.calculate(144f, 48f, style)
                for (view in listOf(R.id.widget_gap_left, R.id.widget_gap_right)) {
                    setViewLayoutWidth(view, 0f, TypedValue.COMPLEX_UNIT_DIP)
                }
                for (button in buttons) {
                    val dp = if (button == R.id.widget_toggle) fallback.centerPaddingDp else fallback.sidePaddingDp
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
