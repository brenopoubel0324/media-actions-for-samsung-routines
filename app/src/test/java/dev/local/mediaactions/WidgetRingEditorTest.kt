package dev.local.mediaactions
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.text.InputType
import android.widget.CompoundButton
import android.widget.EditText
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 36])
class WidgetRingEditorTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private fun widget(): Int {
        val manager=AppWidgetManager.getInstance(app)
        val id=shadowOf(manager).createWidget(MediaWidgetProvider::class.java,R.layout.widget_media_controls)
        shadowOf(manager).addBoundWidget(id,AppWidgetProviderInfo().apply { provider=ComponentName(app,MediaWidgetProvider::class.java) })
        return id
    }
    @Test fun signedVerticalInputAndCenterOptionsSurviveRecreationAndSaveOnlyToOneWidget() {
        val id=widget(); val other=widget()
        val intent=Intent(app,WidgetConfigureActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id)
        Robolectric.buildActivity(WidgetConfigureActivity::class.java,intent).setup().use { c ->
            c.get().findViewById<CompoundButton>(R.id.widget_center_border_only).performClick()
            c.get().findViewById<CompoundButton>(R.id.widget_progress_ring).performClick()
            val vertical=c.get().findViewById<EditText>(R.id.widget_padding_vertical_value)
            assertTrue(vertical.inputType and InputType.TYPE_NUMBER_FLAG_SIGNED != 0)
            vertical.setText("-12"); c.recreate()
            assertEquals("-12",c.get().findViewById<EditText>(R.id.widget_padding_vertical_value).text.toString())
            assertTrue(c.get().findViewById<CompoundButton>(R.id.widget_progress_ring).isChecked)
            assertTrue(c.get().findViewById<CompoundButton>(R.id.widget_center_border_only).isChecked)
            assertFalse(WidgetStyleStore.load(app,id).progressRing)
            c.get().findViewById<android.view.View>(R.id.widget_configuration_save).performClick()
            val style=WidgetStyleStore.load(app,id)
            assertTrue(style.centerBorderOnly && style.progressRing); assertEquals(-12,style.widgetPaddingVerticalDp)
            assertFalse(WidgetStyleStore.load(app,other).progressRing)
        }
    }
    @Test fun negativeHorizontalOrOutOfBoundsVerticalCannotPartiallySave() {
        val id=widget(); val editor=WidgetAppearanceSection(app,id); val view=editor.create()
        view.findViewById<CompoundButton>(R.id.widget_progress_ring).performClick()
        val h=view.findViewById<EditText>(R.id.widget_padding_horizontal_value)
        val v=view.findViewById<EditText>(R.id.widget_padding_vertical_value)
        h.setText("-1"); v.setText("-12"); assertFalse(editor.applyChanges()); assertFalse(WidgetStyleStore.load(app,id).progressRing)
        h.setText("0"); v.setText("-25"); assertFalse(editor.applyChanges())
        v.setText("-24"); assertTrue(editor.applyChanges())
    }
}
