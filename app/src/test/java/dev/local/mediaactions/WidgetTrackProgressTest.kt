package dev.local.mediaactions
import org.junit.Assert.*
import org.junit.Test
class WidgetTrackProgressTest {
    @Test fun playbackProjectsPositionByMonotonicTimeAndSpeed() {
        assertEquals(0.25f, WidgetTrackProgress.fraction(1000, 20000, 100, 2f, true, 2100)!!.fraction, 0.001f)
    }
    @Test fun pauseDoesNotProjectAndASeekReplacesThePosition() {
        assertEquals(0.1f, WidgetTrackProgress.fraction(1000, 10000, 100, 1f, false, 5100)!!.fraction, 0.001f)
        assertEquals(0.7f, WidgetTrackProgress.fraction(7000, 10000, 5100, 1f, false, 5100)!!.fraction, 0.001f)
    }
    @Test fun invalidDurationAndUnknownPositionHaveNoFabricatedProgress() {
        assertNull(WidgetTrackProgress.fraction(0, 0, 0, 1f, true, 100))
        assertNull(WidgetTrackProgress.fraction(-1, 1000, 0, 1f, true, 100))
    }
    @Test fun projectionClampsAtTheEndsAndHandlesNonfiniteSpeedAndFutureTimestamps() {
        assertEquals(1f, WidgetTrackProgress.fraction(Long.MAX_VALUE, 1000, 1, 2f, true, 10000)!!.fraction)
        assertEquals(0f, WidgetTrackProgress.fraction(10, 1000, 1, -2f, true, 10000)!!.fraction)
        assertEquals(0.1f, WidgetTrackProgress.fraction(100, 1000, 1, Float.NaN, true, 1000)!!.fraction)
        assertEquals(0.1f, WidgetTrackProgress.fraction(100, 1000, 2000, 1f, true, 1000)!!.fraction)
    }
    @Test fun onlyVerticalPaddingCanBeNegativeAndDefaultsRemainCompatible() {
        val style = WidgetAppearance(widgetPaddingHorizontalDp = -12, widgetPaddingVerticalDp = -99).normalized()
        assertEquals(0, style.widgetPaddingHorizontalDp); assertEquals(-24, style.widgetPaddingVerticalDp)
        assertFalse(WidgetAppearance().centerBorderOnly); assertFalse(WidgetAppearance().progressRing)
        val g = WidgetGeometry.calculate(360f, 64f, style)
        assertEquals(-24, g.widgetPaddingVerticalDp); assertTrue(g.buttonDp >= 48)
    }
}
