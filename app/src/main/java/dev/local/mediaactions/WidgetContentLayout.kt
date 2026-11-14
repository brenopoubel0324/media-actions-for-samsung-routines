package dev.local.mediaactions

import android.util.SizeF
import kotlin.math.min

/** Compact frames keep touch geometry independent of their desired height and of excess host height. */
internal data class WidgetContentLayout(val geometry: WidgetGeometry, val vertical: Boolean) {
    val rowWidth get() = if (vertical) geometry.buttonDp else geometry.widthDp
    val rowHeight get() = if (vertical) geometry.widthDp else geometry.buttonDp
    companion object {
        fun calculate(size: SizeF?, appearance: WidgetAppearance, volume: Boolean): WidgetContentLayout {
            return calculate(size, appearance, if (volume) WidgetKind.VOLUME else WidgetKind.MEDIA)
        }
        fun calculate(size: SizeF?, appearance: WidgetAppearance, kind: WidgetKind): WidgetContentLayout {
            val volume = kind == WidgetKind.VOLUME
            val count = if (volume) 2 else 3
            val style = appearance.normalized()
            val width = size?.width ?: if (volume) 96f else 144f
            val height = size?.height ?: 48f
            val input = if (size == null) style.copy(widgetPaddingHorizontalDp = 0, widgetPaddingVerticalDp = 0) else style
            val original = if (kind.hasVolume && size != null) VolumeWidgetLayout.calculate(width, height, input, count)
                .let { WidgetContentLayout(it.geometry, it.vertical) }
                else WidgetContentLayout(WidgetGeometry.calculate(width,height,input,count),false)
            val result = if (!style.widgetBorder || style.frameMode != WidgetFrameMode.COMPACT) original else {
                val outer = original.geometry
                val availableWidth = width - outer.widgetPaddingHorizontalDp * 2
                val availableHeight = min(height, height - outer.widgetPaddingVerticalDp * 2)
                val axis = if (original.vertical) availableHeight else availableWidth
                val cross = if (original.vertical) availableWidth else availableHeight
                val compact = WidgetGeometry.calculate(axis, min(48f,cross),
                    style.copy(widgetPaddingHorizontalDp=0,widgetPaddingVerticalDp=0),count)
                WidgetContentLayout(compact.copy(widgetPaddingHorizontalDp=outer.widgetPaddingHorizontalDp,
                    widgetPaddingVerticalDp=outer.widgetPaddingVerticalDp),original.vertical)
            }
            // Negative padding may enlarge the requested controls beyond the physical host.
            // Only the combined widget requires every 48dp target to stay fully inside it.
            if (kind == WidgetKind.PLAYBACK_VOLUME && style.widgetPaddingVerticalDp < 0 && size != null &&
                (result.rowWidth > width || result.rowHeight > height)) {
                return calculate(size, style.copy(widgetPaddingVerticalDp=0), kind)
            }
            return result
        }
    }
}
