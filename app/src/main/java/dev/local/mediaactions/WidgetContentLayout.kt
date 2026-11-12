package dev.local.mediaactions

import android.util.SizeF
import kotlin.math.min

/** Compact frames keep touch geometry independent of their desired height and of excess host height. */
internal data class WidgetContentLayout(val geometry: WidgetGeometry, val vertical: Boolean) {
    val rowWidth get() = if (vertical) geometry.buttonDp else geometry.widthDp
    val rowHeight get() = if (vertical) geometry.widthDp else geometry.buttonDp
    companion object {
        fun calculate(size: SizeF?, appearance: WidgetAppearance, volume: Boolean): WidgetContentLayout {
            val style = appearance.normalized()
            val width = size?.width ?: if (volume) 96f else 144f
            val height = size?.height ?: 48f
            val input = if (size == null) style.copy(widgetPaddingHorizontalDp = 0, widgetPaddingVerticalDp = 0) else style
            val original = if (volume && size != null) VolumeWidgetLayout.calculate(width, height, input)
                .let { WidgetContentLayout(it.geometry, it.vertical) }
                else WidgetContentLayout(WidgetGeometry.calculate(width,height,input,if(volume)2 else 3),false)
            if (!style.widgetBorder || style.frameMode != WidgetFrameMode.COMPACT) return original
            val outer = original.geometry
            val availableWidth = width - outer.widgetPaddingHorizontalDp * 2
            val availableHeight = min(height, height - outer.widgetPaddingVerticalDp * 2)
            val axis = if (original.vertical) availableHeight else availableWidth
            val cross = if (original.vertical) availableWidth else availableHeight
            val compact = WidgetGeometry.calculate(axis, min(48f,cross),
                style.copy(widgetPaddingHorizontalDp=0,widgetPaddingVerticalDp=0),if(volume)2 else 3)
            return WidgetContentLayout(compact.copy(widgetPaddingHorizontalDp=outer.widgetPaddingHorizontalDp,
                widgetPaddingVerticalDp=outer.widgetPaddingVerticalDp),original.vertical)
        }
    }
}
