package dev.local.mediaactions

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Looper
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
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
class IndependentBordersTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private fun widget(): Int {
        val manager = AppWidgetManager.getInstance(app)
        val id = shadowOf(manager).createWidget(MediaWidgetProvider::class.java, R.layout.widget_media_controls)
        shadowOf(manager).addBoundWidget(id, AppWidgetProviderInfo().apply { provider = ComponentName(app, MediaWidgetProvider::class.java) })
        return id
    }
    @Test fun oldWidgetFrameSettingsArePreservedAndNewButtonBorderStartsOff() {
        val id = widget()
        app.getSharedPreferences("widget_appearance_$id", 0).edit().putBoolean("has_override", true)
            .putBoolean("widget_border", true).putInt("border_width", 4).putInt("border_color", Color.CYAN).apply()
        val stored = WidgetStyleStore.load(app, id)
        assertTrue(stored.widgetBorder); assertEquals(4, stored.borderWidthDp); assertEquals(Color.CYAN, stored.borderColor)
        assertFalse(stored.buttonBorder)
        assertEquals(1, stored.copy(buttonBorderWidthDp = -20).normalized().buttonBorderWidthDp)
        assertEquals(8, stored.copy(buttonBorderWidthDp = 99).normalized().buttonBorderWidthDp)
    }
    @Test fun eachSectionSavesItsOwnThicknessAndColorWithoutChangingOtherWidgets() {
        val id = widget(); val other = widget()
        val editor = WidgetAppearanceSection(app, id); val view = editor.create()
        view.findViewById<CheckBox>(R.id.widget_button_border).performClick()
        view.findViewById<EditText>(R.id.widget_button_border_width_value).setText("2")
        view.findViewById<EditText>(R.id.widget_button_border_color_value).setText("#80FFD166")
        view.findViewById<CheckBox>(R.id.widget_border).performClick()
        view.findViewById<EditText>(R.id.widget_border_width_value).setText("5")
        view.findViewById<EditText>(R.id.widget_border_color_value).setText("#FF62CFF4")
        assertFalse(WidgetStyleStore.load(app, id).buttonBorder)
        assertTrue(editor.applyChanges())
        val actual = WidgetStyleStore.load(app, id)
        assertTrue(actual.buttonBorder && actual.widgetBorder)
        assertEquals(2, actual.buttonBorderWidthDp); assertEquals(0x80FFD166.toInt(), actual.buttonBorderColor)
        assertEquals(5, actual.borderWidthDp); assertEquals(0xFF62CFF4.toInt(), actual.borderColor)
        assertFalse(WidgetStyleStore.load(app, other).buttonBorder)
        assertFalse(WidgetStyleStore.load(app, other).widgetBorder)
    }
    @Test fun disablingOneBorderRetainsBothSetsOfValuesAndDoesNotDisableTheOther() {
        val original = WidgetAppearance(widgetBorder = true, borderWidthDp = 4, borderColor = Color.CYAN,
            buttonBorder = true, buttonBorderWidthDp = 2, buttonBorderColor = Color.YELLOW)
        val id = widget(); WidgetStyleStore.save(app, original, id)
        var editor = WidgetAppearanceSection(app, id); var view = editor.create()
        view.findViewById<CheckBox>(R.id.widget_button_border).performClick()
        assertTrue(view.findViewById<EditText>(R.id.widget_border_color_value).isEnabled)
        assertFalse(view.findViewById<EditText>(R.id.widget_button_border_color_value).isEnabled)
        assertTrue(editor.applyChanges())
        assertEquals(original.copy(buttonBorder = false), WidgetStyleStore.load(app, id))
        editor = WidgetAppearanceSection(app, id); view = editor.create()
        view.findViewById<CheckBox>(R.id.widget_button_border).performClick()
        view.findViewById<CheckBox>(R.id.widget_border).performClick()
        assertTrue(editor.applyChanges())
        assertEquals(original.copy(widgetBorder = false), WidgetStyleStore.load(app, id))
    }
    @Test fun invalidValuesInEitherEnabledSectionPreventPartialSaves() {
        for ((toggleId, valueId) in listOf(R.id.widget_button_border to R.id.widget_button_border_color_value,
            R.id.widget_border to R.id.widget_border_color_value)) {
            val id = widget(); val editor = WidgetAppearanceSection(app, id); val view = editor.create()
            view.findViewById<CheckBox>(R.id.widget_button_border).performClick()
            view.findViewById<CheckBox>(R.id.widget_border).performClick()
            view.findViewById<EditText>(valueId).setText("#NOPE")
            assertFalse(editor.applyChanges())
            assertFalse(WidgetStyleStore.load(app, id).buttonBorder || WidgetStyleStore.load(app, id).widgetBorder)
            view.findViewById<CheckBox>(toggleId).performClick()
            assertTrue(editor.applyChanges())
        }
    }
    @Test fun recreationKeepsBothUnappliedDraftsAndSavingRestoresEachOne() {
        val id = widget()
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        Robolectric.buildActivity(WidgetConfigureActivity::class.java, intent).setup().use { c ->
            c.get().findViewById<CheckBox>(R.id.widget_button_border).performClick()
            c.get().findViewById<EditText>(R.id.widget_button_border_width_value).setText("2")
            c.get().findViewById<EditText>(R.id.widget_button_border_color_value).setText("#FFFFD166")
            c.get().findViewById<CheckBox>(R.id.widget_border).performClick()
            c.get().findViewById<EditText>(R.id.widget_border_width_value).setText("5")
            c.get().findViewById<EditText>(R.id.widget_border_color_value).setText("#FF62CFF4")
            c.recreate()
            assertEquals("2", c.get().findViewById<EditText>(R.id.widget_button_border_width_value).text.toString())
            assertEquals("5", c.get().findViewById<EditText>(R.id.widget_border_width_value).text.toString())
            assertFalse(WidgetStyleStore.load(app, id).buttonBorder)
            c.get().findViewById<View>(R.id.widget_configuration_save).performClick()
            val actual = WidgetStyleStore.load(app, id)
            assertTrue(actual.buttonBorder && actual.widgetBorder)
            assertEquals(0xFFFFD166.toInt(), actual.buttonBorderColor)
            assertEquals(0xFF62CFF4.toInt(), actual.borderColor)
        }
    }
    @Test fun buttonColorPresetRecoversFromInvalidTextWithoutChangingWidgetColor() {
        val id = widget(); val editor = WidgetAppearanceSection(app, id); val view = editor.create()
        fun layout() {
            view.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            view.layout(0, 0, 360, view.measuredHeight); shadowOf(Looper.getMainLooper()).idle()
        }
        view.findViewById<CheckBox>(R.id.widget_button_border).performClick()
        view.findViewById<CheckBox>(R.id.widget_border).performClick()
        view.findViewById<EditText>(R.id.widget_border_color_value).setText("#FF62CFF4")
        layout()
        view.findViewById<EditText>(R.id.widget_button_border_color_value).setText("#NOPE"); layout()
        assertFalse(editor.applyChanges())
        view.findViewById<Spinner>(R.id.widget_button_border_color_preset).setSelection(0); layout()
        assertTrue(editor.applyChanges())
        val actual = WidgetStyleStore.load(app, id)
        assertEquals(Color.WHITE, actual.buttonBorderColor); assertEquals(0xFF62CFF4.toInt(), actual.borderColor)
    }
}
