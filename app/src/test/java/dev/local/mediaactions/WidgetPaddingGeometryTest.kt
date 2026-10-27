package dev.local.mediaactions

import org.junit.Assert.*
import org.junit.Test

class WidgetPaddingGeometryTest {
    @Test fun horizontalAndVerticalPaddingReduceTheInnerAreaSeparately() {
        val result = WidgetGeometry.calculate(360f, 96f, WidgetAppearance(fillWidth = true,
            widgetPaddingHorizontalDp = 24, widgetPaddingVerticalDp = 12))
        assertEquals(24, result.widgetPaddingHorizontalDp)
        assertEquals(12, result.widgetPaddingVerticalDp)
        assertEquals(312, result.widthDp)
        assertEquals(72, result.buttonDp)
        assertEquals(48, result.gapDp)
        val horizontalOnly = WidgetGeometry.calculate(360f, 96f, WidgetAppearance(fillWidth = true, widgetPaddingHorizontalDp = 24))
        assertEquals(96, horizontalOnly.buttonDp)
        assertEquals(312, horizontalOnly.widthDp)
    }
    @Test fun zeroPaddingKeepsThePreviousGeometry() {
        val result = WidgetGeometry.calculate(360f, 96f, WidgetAppearance(fillWidth = true))
        assertEquals(360, result.widthDp); assertEquals(96, result.buttonDp); assertEquals(36, result.gapDp)
        assertEquals(0, result.widgetPaddingHorizontalDp); assertEquals(0, result.widgetPaddingVerticalDp)
    }
    @Test fun requestedPaddingCannotConsumeMinimumTouchAreas() {
        val style = WidgetAppearance(widgetPaddingHorizontalDp = 144, widgetPaddingVerticalDp = 144)
        val small = WidgetGeometry.calculate(144f, 48f, style)
        assertEquals(0, small.widgetPaddingHorizontalDp); assertEquals(0, small.widgetPaddingVerticalDp)
        assertEquals(48, small.buttonDp)
        val larger = WidgetGeometry.calculate(360f, 96f, style)
        assertEquals(108, larger.widgetPaddingHorizontalDp); assertEquals(24, larger.widgetPaddingVerticalDp)
        assertEquals(48, larger.buttonDp); assertEquals(144, larger.widthDp)
    }
    @Test fun paddingIsBoundedIndependentlyFromBordersAndSpacing() {
        val style = WidgetAppearance(widgetBorder = true, borderWidthDp = 3, buttonBorder = true,
            buttonBorderWidthDp = 2, widgetPaddingHorizontalDp = -99, widgetPaddingVerticalDp = 999).normalized()
        assertEquals(0, style.widgetPaddingHorizontalDp); assertEquals(144, style.widgetPaddingVerticalDp)
        assertEquals(3, style.borderWidthDp); assertEquals(2, style.buttonBorderWidthDp)
    }
    @Test fun contentAndPaddingFitAcrossHostSizesAndSpacingModes() {
        for (w in listOf(144, 240, 360, 600)) for (h in listOf(48, 64, 96, 144))
            for (ph in listOf(0, 12, 144)) for (pv in listOf(0, 12, 144))
                for (gap in listOf(0, 37, 144)) for (auto in listOf(false, true)) {
                    val g = WidgetGeometry.calculate(w.toFloat(), h.toFloat(), WidgetAppearance(spacingDp = gap,
                        fillWidth = auto, widgetPaddingHorizontalDp = ph, widgetPaddingVerticalDp = pv))
                    assertTrue(g.widthDp + 2 * g.widgetPaddingHorizontalDp <= w)
                    assertTrue(g.buttonDp + 2 * g.widgetPaddingVerticalDp <= h)
                    assertTrue(g.buttonDp >= 48); assertTrue(g.gapDp >= 0)
                }
    }
    @Test fun invalidDimensionsUseSafeFallbackAndStillBoundPadding() {
        val g = WidgetGeometry.calculate(Float.NaN, Float.POSITIVE_INFINITY,
            WidgetAppearance(widgetPaddingHorizontalDp = 144, widgetPaddingVerticalDp = 144))
        assertEquals(18, g.widgetPaddingHorizontalDp); assertEquals(4, g.widgetPaddingVerticalDp)
        assertEquals(48, g.buttonDp)
    }
}
