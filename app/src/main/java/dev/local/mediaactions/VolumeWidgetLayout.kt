package dev.local.mediaactions

import android.util.SizeF
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/** The host's available rectangle selects the axis; saved padding keeps its physical directions. */
internal data class VolumeWidgetLayout(val vertical: Boolean, val geometry: WidgetGeometry) {
    val rowWidth get() = if (vertical) geometry.buttonDp else geometry.widthDp
    val rowHeight get() = if (vertical) geometry.widthDp else geometry.buttonDp

    companion object {
        private fun isVertical(width: Float, height: Float) = width.isFinite() && height.isFinite() && width > 0 && height > width
        fun isVertical(size: SizeF?): Boolean = size != null && isVertical(size.width,size.height)

        fun calculate(width: Float, height: Float, appearance: WidgetAppearance, buttonCount: Int = 2): VolumeWidgetLayout {
            require(buttonCount in 2..3)
            val minimumHeight = buttonCount * 48
            val gaps = buttonCount - 1
            if (!isVertical(width,height))
                return VolumeWidgetLayout(false, WidgetGeometry.calculate(width,height,appearance,buttonCount))
            val style = appearance.normalized()
            val horizontal = min(style.widgetPaddingHorizontalDp, floor(((width-48)/2).coerceAtLeast(0f)).toInt())
            val vertical = min(style.widgetPaddingVerticalDp, floor(((height-minimumHeight)/2).coerceAtLeast(0f)).toInt())
            val availableWidth = width-horizontal*2
            val availableHeight = height-vertical*2
            var gap = floor(min(if(style.fillWidth) 0f else style.spacingDp.toFloat(),
                ((availableHeight-minimumHeight)/gaps).coerceAtLeast(0f))).toInt()
            val button = floor(min(availableWidth,(availableHeight-gap*gaps)/buttonCount)).toInt().coerceIn(1,128)
            if(style.fillWidth) gap = floor((availableHeight-button*buttonCount)/gaps).toInt().coerceAtLeast(0)
            val iconPadding = (button*(1-min(1f,0.82f*style.iconScalePercent/100f))/2).roundToInt().coerceAtLeast(0)
            val centerPadding = if (buttonCount == 3)
                (button*(1-min(1f,0.95f*style.iconScalePercent/100f))/2).roundToInt().coerceAtLeast(0) else iconPadding
            return VolumeWidgetLayout(true, WidgetGeometry(button,gap,iconPadding,centerPadding,horizontal,vertical,buttonCount))
        }
    }
}
