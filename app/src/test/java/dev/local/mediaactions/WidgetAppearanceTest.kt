package dev.local.mediaactions

import org.junit.Assert.*
import org.junit.Test

class WidgetAppearanceTest {
    @Test fun compactControlsDoNotSpreadAcrossAWideShortWidget() {
        val size = WidgetGeometry.calculate(360f, 56f, WidgetAppearance())
        assertEquals(168, size.widthDp)
        assertEquals(56, size.buttonDp)
        assertEquals(0, size.gapDp)
    }

    @Test fun increasingHeightAllowsControlsToGrow() {
        val small = WidgetGeometry.calculate(360f, 56f, WidgetAppearance())
        val large = WidgetGeometry.calculate(360f, 112f, WidgetAppearance())
        assertTrue(large.buttonDp > small.buttonDp)
        assertTrue(large.buttonDp - 2 * large.sidePaddingDp > small.buttonDp - 2 * small.sidePaddingDp)
    }

    @Test fun userScaleChangesIconsWithoutChangingTouchTargetsOrSpacing() {
        val small = WidgetGeometry.calculate(240f, 80f, WidgetAppearance(iconScalePercent = 75, spacingDp = 8))
        val large = WidgetGeometry.calculate(240f, 80f, WidgetAppearance(iconScalePercent = 135, spacingDp = 8))
        assertEquals(small.buttonDp, large.buttonDp)
        assertEquals(small.gapDp, large.gapDp)
        assertTrue(large.sidePaddingDp < small.sidePaddingDp)
    }

    @Test fun gapsShrinkToPreserveTouchTargetsAtMinimumSize() {
        val size = WidgetGeometry.calculate(144f, 48f, WidgetAppearance(spacingDp = 24))
        assertEquals(48, size.buttonDp)
        assertEquals(0, size.gapDp)
        assertEquals(144, size.widthDp)
    }

    @Test fun controlsFitAcrossSupportedSizesAndStyleExtremes() {
        for (width in listOf(144, 156, 180, 240, 360, 432, 600)) {
            for (height in listOf(48, 56, 64, 96, 112, 144, 300)) {
                for (spacing in listOf(0, 12, 24)) for (scale in listOf(75, 100, 135)) {
                    val size = WidgetGeometry.calculate(width.toFloat(), height.toFloat(), WidgetAppearance(scale, spacing))
                    assertTrue(size.widthDp <= width)
                    assertTrue(size.buttonDp in 48..minOf(height, 128))
                    assertTrue(size.sidePaddingDp >= 0 && size.centerPaddingDp >= 0)
                }
            }
        }
    }

    @Test fun invalidDimensionsAndPreferenceValuesAreBounded() {
        val size = WidgetGeometry.calculate(Float.NaN, Float.POSITIVE_INFINITY, WidgetAppearance(-1, 999))
        assertTrue(size.widthDp <= 180)
        assertEquals(WidgetAppearance(75, 24), WidgetAppearance(-1, 999).normalized())
    }
}
