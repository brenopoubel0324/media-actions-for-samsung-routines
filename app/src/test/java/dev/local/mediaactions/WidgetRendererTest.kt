package dev.local.mediaactions

import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import android.widget.Button
import android.widget.RadioButton
import android.view.ViewGroup
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 36])
class WidgetRendererTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Test fun exactHostSizesWinOverLegacyRangesAndInvalidSizesAreIgnored() {
        val options = Bundle().apply {
            putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,
                arrayListOf(SizeF(180f, 56f), SizeF(360f, 112f), SizeF(-1f, 0f), SizeF(180f, 56f)))
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 240)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 80)
        }
        assertEquals(listOf(SizeF(180f, 56f), SizeF(360f, 112f)), WidgetRenderer.reportedSizes(options))
        assertNotNull(WidgetRenderer.render(app, 42, options))
    }

    @Test fun legacyHostRangesProduceOrientationSpecificSizes() {
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 56)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 360)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 112)
        }
        assertEquals(listOf(SizeF(180f, 112f), SizeF(360f, 56f)), WidgetRenderer.reportedSizes(options))
        assertTrue(WidgetRenderer.reportedSizes(Bundle()).isEmpty())
    }

    @Test fun exactSizeRenderingKeepsTouchAreasInsideTheHostAndScalesGlyphs() {
        val density = app.resources.displayMetrics.density
        var previousIconSpace = 0
        for ((width, height) in listOf(144 to 48, 240 to 64, 360 to 112)) {
            val root = WidgetRenderer.build(app, 42, WidgetAppearance(), SizeF(width.toFloat(), height.toFloat()))
                .apply(app, FrameLayout(app))
            val w = (width * density).toInt()
            val h = (height * density).toInt()
            root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, w, h)
            val row = root.findViewById<View>(R.id.widget_controls_row)
            assertTrue(row.left >= 0 && row.right <= w && row.top >= 0 && row.bottom <= h)
            for (id in listOf(R.id.widget_previous, R.id.widget_toggle, R.id.widget_next)) {
                val button = root.findViewById<View>(id)
                assertTrue(button.width >= 48 * density - 1 && button.height >= 48 * density - 1)
            }
            val next = root.findViewById<View>(R.id.widget_next)
            val iconSpace = next.height - next.paddingTop - next.paddingBottom
            assertTrue(iconSpace > previousIconSpace)
            previousIconSpace = iconSpace
        }
    }

    @Test fun appearancePreferencesPersistAndAreClamped() {
        WidgetStyleStore.save(app, WidgetAppearance(999, -5, true))
        assertEquals(WidgetAppearance(135, 0, true), WidgetStyleStore.load(app))
    }

    @Test fun appearanceEditorPreviewsChangesAndSavesOnlyWhenApplied() {
        val section = WidgetAppearanceSection(app).create()
        fun children(view: View): List<View> = listOf(view) +
            if (view is ViewGroup) (0 until view.childCount).flatMap { children(view.getChildAt(it)) } else emptyList()
        val views = children(section)
        views.filterIsInstance<RadioButton>().first { it.text == app.getString(R.string.widget_icons_dark) }
            .isChecked = true
        assertFalse(WidgetStyleStore.load(app).darkIcons)
        views.filterIsInstance<Button>().first { it.text == app.getString(R.string.widget_apply_style) }.performClick()
        assertTrue(WidgetStyleStore.load(app).darkIcons)
        for (id in listOf(R.id.widget_previous, R.id.widget_toggle, R.id.widget_next)) {
            assertFalse(section.findViewById<View>(id).isClickable)
        }
    }

    @Test fun nonReportingHostsStillApplyIconScaleWithoutAddingUnsafeGaps() {
        val small = WidgetRenderer.build(app, 42, WidgetAppearance(75, 24), null).apply(app, FrameLayout(app))
        val large = WidgetRenderer.build(app, 42, WidgetAppearance(135, 24), null).apply(app, FrameLayout(app))
        assertTrue(small.findViewById<View>(R.id.widget_next).paddingTop >
            large.findViewById<View>(R.id.widget_next).paddingTop)
        assertEquals(0, small.findViewById<View>(R.id.widget_gap_left).layoutParams.width)
    }
}
