package dev.local.mediaactions

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.SizeF
import android.widget.EditText
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 36])
class WidgetPaddingEditorTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private fun widget(): Int {
        val manager = AppWidgetManager.getInstance(app)
        val id = shadowOf(manager).createWidget(MediaWidgetProvider::class.java, R.layout.widget_media_controls)
        shadowOf(manager).addBoundWidget(id, AppWidgetProviderInfo().apply { provider = ComponentName(app, MediaWidgetProvider::class.java) })
        return id
    }
    @Test fun oldProfilesStartAtZeroAndNewPaddingOnlySavesToTheSelectedWidget() {
        val id = widget(); val other = widget()
        val original = WidgetAppearance(widgetBorder = true, borderWidthDp = 3, borderColor = Color.CYAN,
            buttonBorder = true, buttonBorderWidthDp = 2, buttonBorderColor = Color.YELLOW)
        WidgetStyleStore.save(app, original, id)
        val editor = WidgetAppearanceSection(app, id); val view = editor.create()
        view.findViewById<EditText>(R.id.widget_padding_horizontal_value).setText("24")
        view.findViewById<EditText>(R.id.widget_padding_vertical_value).setText("12")
        assertEquals(original, WidgetStyleStore.load(app, id))
        assertTrue(editor.applyChanges())
        assertEquals(original.copy(widgetPaddingHorizontalDp = 24, widgetPaddingVerticalDp = 12), WidgetStyleStore.load(app, id))
        assertEquals(0, WidgetStyleStore.load(app, other).widgetPaddingHorizontalDp)
        assertEquals(0, WidgetStyleStore.load(app).widgetPaddingVerticalDp)
    }
    @Test fun invalidPaddingDoesNotPartiallySaveTheProfile() {
        val id = widget(); val editor = WidgetAppearanceSection(app, id); val view = editor.create()
        view.findViewById<EditText>(R.id.widget_padding_horizontal_value).setText("24")
        view.findViewById<EditText>(R.id.widget_padding_vertical_value).setText("999")
        assertFalse(editor.applyChanges())
        assertEquals(0, WidgetStyleStore.load(app, id).widgetPaddingHorizontalDp)
        view.findViewById<EditText>(R.id.widget_padding_vertical_value).setText("6")
        assertTrue(editor.applyChanges())
        assertEquals(24, WidgetStyleStore.load(app, id).widgetPaddingHorizontalDp)
        assertEquals(6, WidgetStyleStore.load(app, id).widgetPaddingVerticalDp)
    }
    @Test fun recreationPreservesUnappliedPaddingDraft() {
        val id = widget()
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        Robolectric.buildActivity(WidgetConfigureActivity::class.java, intent).setup().use { c ->
            c.get().findViewById<EditText>(R.id.widget_padding_horizontal_value).setText("24")
            c.get().findViewById<EditText>(R.id.widget_padding_vertical_value).setText("12")
            c.recreate()
            assertEquals("24", c.get().findViewById<EditText>(R.id.widget_padding_horizontal_value).text.toString())
            assertEquals("12", c.get().findViewById<EditText>(R.id.widget_padding_vertical_value).text.toString())
            assertEquals(0, WidgetStyleStore.load(app, id).widgetPaddingVerticalDp)
            c.get().findViewById<android.view.View>(R.id.widget_configuration_save).performClick()
            assertEquals(24, WidgetStyleStore.load(app, id).widgetPaddingHorizontalDp)
            assertEquals(12, WidgetStyleStore.load(app, id).widgetPaddingVerticalDp)
        }
    }
    @Test fun paddingCanBeUsedWithoutEnablingEitherBorder() {
        val id = widget(); val editor = WidgetAppearanceSection(app, id); val view = editor.create()
        val horizontal = view.findViewById<EditText>(R.id.widget_padding_horizontal_value)
        val vertical = view.findViewById<EditText>(R.id.widget_padding_vertical_value)
        assertTrue(horizontal.isEnabled && vertical.isEnabled)
        horizontal.setText("6"); vertical.setText("4"); assertTrue(editor.applyChanges())
        val actual = WidgetStyleStore.load(app, id)
        assertFalse(actual.widgetBorder || actual.buttonBorder)
        assertEquals(6, actual.widgetPaddingHorizontalDp); assertEquals(4, actual.widgetPaddingVerticalDp)
    }
    @Test fun previewReportsTheEffectivePaddingWhenTheWidgetCannotFitTheRequest() {
        val id = widget()
        AppWidgetManager.getInstance(app).updateAppWidgetOptions(id, Bundle().apply {
            putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(360f, 64f)))
        })
        val view = WidgetAppearanceSection(app, id, WidgetAppearance(widgetPaddingHorizontalDp = 24, widgetPaddingVerticalDp = 99)).create()
        val text = view.findViewById<TextView>(R.id.widget_effective_spacing).text.toString()
        assertTrue(text.contains(app.getString(R.string.widget_effective_padding, 24, 8)))
        assertTrue(text.contains(app.getString(R.string.widget_padding_limited, 24, 99)))
    }
}
