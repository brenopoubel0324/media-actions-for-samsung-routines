package dev.local.mediaactions

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.Spinner
import android.view.View
import android.os.Looper
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
class WidgetBorderTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private fun widget(): Int {
        val manager = AppWidgetManager.getInstance(app)
        val id = shadowOf(manager).createWidget(MediaWidgetProvider::class.java, R.layout.widget_media_controls)
        shadowOf(manager).addBoundWidget(id, AppWidgetProviderInfo().apply { provider = ComponentName(app, MediaWidgetProvider::class.java) })
        return id
    }
    @Test fun existingPreferencesKeepBorderOffAndValuesAreBounded() {
        app.getSharedPreferences("widget_appearance", 0).edit().putBoolean("icon_shadow", true).apply()
        val old = WidgetStyleStore.load(app)
        assertTrue(old.iconShadow)
        assertFalse(old.widgetBorder)
        assertEquals(1, old.borderWidthDp)
        assertEquals(Color.WHITE, old.borderColor)
        assertEquals(1, old.copy(borderWidthDp = -100).normalized().borderWidthDp)
        assertEquals(8, old.copy(borderWidthDp = 100).normalized().borderWidthDp)
    }
    @Test fun editorSavesWidthAndCustomColorOnlyOnApplyAndOnlyForThisWidget() {
        val first = widget(); val other = widget()
        val editor = WidgetAppearanceSection(app, first)
        val view = editor.create()
        view.findViewById<CompoundButton>(R.id.widget_border).performClick()
        view.findViewById<EditText>(R.id.widget_border_width_value).setText("3")
        view.findViewById<EditText>(R.id.widget_border_color_value).setText("#8062CFF4")
        assertFalse(WidgetStyleStore.load(app, first).widgetBorder)
        assertTrue(editor.applyChanges())
        val stored = WidgetStyleStore.load(app, first)
        assertTrue(stored.widgetBorder)
        assertEquals(3, stored.borderWidthDp)
        assertEquals(0x8062CFF4.toInt(), stored.borderColor)
        assertFalse(WidgetStyleStore.load(app, other).widgetBorder)
        assertFalse(WidgetStyleStore.load(app).widgetBorder)
    }
    @Test fun invalidColorOrWidthBlocksSavingInsteadOfReplacingWithADefault() {
        val id = widget(); val editor = WidgetAppearanceSection(app, id); val view = editor.create()
        view.findViewById<CompoundButton>(R.id.widget_border).performClick()
        val color = view.findViewById<EditText>(R.id.widget_border_color_value)
        color.setText("#NOPE")
        assertFalse(editor.applyChanges()); assertNotNull(color.error)
        assertFalse(WidgetStyleStore.load(app, id).widgetBorder)
        color.setText("#12AB34")
        view.findViewById<EditText>(R.id.widget_border_width_value).setText("0")
        assertFalse(editor.applyChanges())
        view.findViewById<EditText>(R.id.widget_border_width_value).setText("2")
        assertTrue(editor.applyChanges())
        assertEquals(0xFF12AB34.toInt(), WidgetStyleStore.load(app, id).borderColor)
    }
    @Test fun selectingTheLastValidPresetRepairsInvalidCustomText() {
        val id = widget(); val editor = WidgetAppearanceSection(app, id); val view = editor.create()
        fun layout() {
            view.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            view.layout(0, 0, 360, view.measuredHeight)
            shadowOf(Looper.getMainLooper()).idle()
        }
        layout()
        view.findViewById<CompoundButton>(R.id.widget_border).performClick()
        val color = view.findViewById<EditText>(R.id.widget_border_color_value)
        val preset = view.findViewById<Spinner>(R.id.widget_border_color_preset)
        color.setText("#NOPE"); layout()
        assertEquals(5, preset.selectedItemPosition)
        assertFalse(editor.applyChanges())
        preset.setSelection(0); layout()
        assertEquals("#FFFFFFFF", color.text.toString())
        assertTrue(editor.applyChanges())
        assertEquals(Color.WHITE, WidgetStyleStore.load(app, id).borderColor)
    }
    @Test fun disabledBorderPreservesColorAndWidthForLaterReuse() {
        val id = widget()
        WidgetStyleStore.save(app, WidgetAppearance(widgetBorder = true, borderWidthDp = 4, borderColor = Color.CYAN), id)
        val editor = WidgetAppearanceSection(app, id); val view = editor.create()
        view.findViewById<CompoundButton>(R.id.widget_border).performClick()
        assertFalse(view.findViewById<EditText>(R.id.widget_border_color_value).isEnabled)
        assertTrue(editor.applyChanges())
        assertEquals(WidgetAppearance(widgetBorder = false, borderWidthDp = 4, borderColor = Color.CYAN), WidgetStyleStore.load(app, id))
    }
    @Test fun externalConfigurationRecreationKeepsTheUnappliedBorderDraft() {
        val id = widget()
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        Robolectric.buildActivity(WidgetConfigureActivity::class.java, intent).setup().use { controller ->
            val activity = controller.get()
            activity.findViewById<CompoundButton>(R.id.widget_border).performClick()
            activity.findViewById<EditText>(R.id.widget_border_width_value).setText("5")
            activity.findViewById<EditText>(R.id.widget_border_color_value).setText("#FF12AB34")
            controller.recreate()
            val recreated = controller.get()
            assertTrue(recreated.findViewById<CompoundButton>(R.id.widget_border).isChecked)
            assertEquals("5", recreated.findViewById<EditText>(R.id.widget_border_width_value).text.toString())
            assertEquals("#FF12AB34", recreated.findViewById<EditText>(R.id.widget_border_color_value).text.toString())
            assertFalse(WidgetStyleStore.load(app, id).widgetBorder)
        }
    }
}
