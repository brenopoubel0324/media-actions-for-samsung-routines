package dev.local.mediaactions

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Insets
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 36])
class WidgetPrecisionTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val manager get() = AppWidgetManager.getInstance(app)

    private fun widget(width: Float = 360f, height: Float = 56f, reportSize: Boolean = true): Int {
        val id = shadowOf(manager).createWidget(MediaWidgetProvider::class.java, R.layout.widget_media_controls)
        shadowOf(manager).addBoundWidget(id, AppWidgetProviderInfo().apply {
            provider = ComponentName(app, MediaWidgetProvider::class.java)
            initialLayout = R.layout.widget_media_controls
        })
        if (reportSize) manager.updateAppWidgetOptions(id, Bundle().apply {
            putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(width, height)))
        })
        return id
    }

    @Test fun exactNumbersAndOneDpStepsStayInSyncAndSaveOnlyOnApply() {
        val editor = WidgetAppearanceSection(app)
        val view = editor.create()
        val input = view.findViewById<EditText>(R.id.widget_spacing_value)
        input.setText("37")
        assertEquals(37, editor.currentAppearance().spacingDp)
        assertEquals(37, view.findViewById<SeekBar>(R.id.widget_spacing_slider).progress)
        view.findViewById<Button>(R.id.widget_spacing_increase).performClick()
        assertEquals("38", input.text.toString())
        view.findViewById<Button>(R.id.widget_spacing_decrease).performClick()
        assertEquals("37", input.text.toString())
        assertEquals(0, WidgetStyleStore.load(app).spacingDp)
        assertTrue(editor.applyChanges())
        assertEquals(37, WidgetStyleStore.load(app).spacingDp)
    }

    @Test fun scaleCanAlsoBeEnteredExactlyWithoutChangingSpacing() {
        val editor = WidgetAppearanceSection(app)
        val view = editor.create()
        view.findViewById<EditText>(R.id.widget_spacing_value).setText("19")
        view.findViewById<EditText>(R.id.widget_scale_value).setText("33")
        view.findViewById<Button>(R.id.widget_scale_increase).performClick()
        assertEquals(34, editor.currentAppearance().iconScalePercent)
        assertEquals(19, editor.currentAppearance().spacingDp)
    }

    @Test fun invalidOrBlankNumbersBlockSaveInsteadOfSilentlySavingAnOldValue() {
        val editor = WidgetAppearanceSection(app)
        val view = editor.create()
        val input = view.findViewById<EditText>(R.id.widget_spacing_value)
        for (text in listOf("", "145", "99999999999999999999")) {
            input.setText(text)
            assertFalse(editor.applyChanges())
            assertNotNull(input.error)
            assertEquals(0, WidgetStyleStore.load(app).spacingDp)
        }
        input.setText("144")
        assertTrue(editor.applyChanges())
        assertEquals(144, WidgetStyleStore.load(app).spacingDp)
        view.findViewById<EditText>(R.id.widget_scale_value).setText("19")
        assertFalse(editor.applyChanges())
        assertEquals(100, WidgetStyleStore.load(app).iconScalePercent)
    }

    @Test fun automaticModeDisablesEveryManualControlAndPreservesItsValue() {
        val editor = WidgetAppearanceSection(app)
        val view = editor.create()
        val input = view.findViewById<EditText>(R.id.widget_spacing_value)
        input.setText("37")
        val automatic = view.findViewById<CompoundButton>(R.id.widget_fill_width)
        automatic.performClick()
        for (id in listOf(R.id.widget_spacing_value, R.id.widget_spacing_slider,
            R.id.widget_spacing_decrease, R.id.widget_spacing_increase)) {
            assertFalse(view.findViewById<android.view.View>(id).isEnabled)
        }
        assertEquals(37, editor.currentAppearance().spacingDp)
        automatic.performClick()
        assertTrue(input.isEnabled)
        assertEquals("37", input.text.toString())
    }

    @Test fun stepsCannotEscapeTheRange() {
        val editor = WidgetAppearanceSection(app)
        val view = editor.create()
        val input = view.findViewById<EditText>(R.id.widget_spacing_value)
        assertFalse(view.findViewById<Button>(R.id.widget_spacing_decrease).isEnabled)
        input.setText("143")
        view.findViewById<Button>(R.id.widget_spacing_increase).performClick()
        assertEquals("144", input.text.toString())
        assertFalse(view.findViewById<Button>(R.id.widget_spacing_increase).isEnabled)
    }

    @Test fun previewUsesTheSelectedWidgetsHostDimensionsAndExposesLimitedGaps() {
        val id = widget(180f, 56f)
        val editor = WidgetAppearanceSection(app, id)
        val view = editor.create()
        view.findViewById<EditText>(R.id.widget_spacing_value).setText("37")
        val label = view.findViewById<TextView>(R.id.widget_effective_spacing).text.toString()
        assertTrue(label.contains("180 × 56"))
        assertTrue(label.contains("Requested 37 dp; only 18 dp fits"))
        val previewGap = view.findViewById<android.view.View>(R.id.widget_gap_left)
        assertEquals((18 * app.resources.displayMetrics.density).toInt(), previewGap.layoutParams.width)
        assertTrue(editor.applyChanges())
        assertEquals(37, WidgetStyleStore.load(app, id).spacingDp)
    }

    @Test fun hostWithoutDimensionsIsClearlyIdentifiedAsASample() {
        val id = widget(reportSize = false)
        val view = WidgetAppearanceSection(app, id).create()
        assertTrue(view.findViewById<TextView>(R.id.widget_effective_spacing).text
            .contains(app.getString(R.string.widget_size_unavailable)))
    }

    @Test fun shadowIsOptionalAndSavedPerWidget() {
        val id = widget()
        val editor = WidgetAppearanceSection(app, id)
        val view = editor.create()
        assertFalse(WidgetStyleStore.load(app, id).iconShadow)
        view.findViewById<CompoundButton>(R.id.widget_icon_shadow).performClick()
        assertFalse(WidgetStyleStore.load(app, id).iconShadow)
        assertTrue(editor.applyChanges())
        assertTrue(WidgetStyleStore.load(app, id).iconShadow)
        assertFalse(WidgetStyleStore.load(app).iconShadow)
    }

    @Test fun shadowsPreserveTheAspectRatioOfNonSquareIcons() {
        val drawable = app.getDrawable(R.drawable.ic_lucide_toggle)!!
        val bitmap = WidgetIconArtwork.shadowed(app, R.drawable.ic_lucide_toggle, android.graphics.Color.WHITE)
        assertEquals(drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight,
            bitmap.width.toFloat() / bitmap.height, 0.01f)
    }

    @Test fun configurationCanBeResolvedFromOutsideTheAppWithoutAnExplicitComponent() {
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setPackage(app.packageName)
        val info = app.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        assertEquals(WidgetConfigureActivity::class.java.name, info?.activityInfo?.name)
        assertTrue(info!!.activityInfo.exported)
    }

    @Test fun appEditButtonTargetsTheInstalledWidgetRatherThanGlobalDefaults() {
        val id = widget()
        Robolectric.buildActivity(MainActivity::class.java).use {
            val activity = it.setup().get()
            activity.findViewById<Button>(R.id.widget_edit_existing).performClick()
            val intent = shadowOf(activity).nextStartedActivity
            assertEquals(ComponentName(app, WidgetConfigureActivity::class.java), intent.component)
            assertEquals(id, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0))
        }
    }

    @Test fun appEditButtonExplainsAnEmptyListWithoutOpeningAnInvalidEditor() {
        Robolectric.buildActivity(MainActivity::class.java).use {
            val activity = it.setup().get()
            activity.findViewById<Button>(R.id.widget_edit_existing).performClick()
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            assertTrue(dialog.isShowing)
            assertEquals(app.getString(R.string.widget_none_installed), shadowOf(dialog).message.toString())
            assertNull(shadowOf(activity).nextStartedActivity)
        }
    }

    @Test fun appLetsTheUserChooseWhichInstalledWidgetToEdit() {
        val first = widget()
        val second = widget(240f)
        Robolectric.buildActivity(MainActivity::class.java).use {
            val activity = it.setup().get()
            activity.findViewById<Button>(R.id.widget_edit_existing).performClick()
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            assertEquals(2, dialog.listView.count)
            shadowOf(dialog).clickOnItem(1)
            val intent = shadowOf(activity).nextStartedActivity
            assertEquals(second, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0))
            assertNotEquals(first, second)
        }
    }

    @Test
    @Config(qualifiers = "w420dp-h800dp-mdpi")
    fun keyboardInsetsKeepSaveAboveTheKeyboardAndHideThePreviewOnShortWindows() {
        val id = widget()
        val intent = Intent(app, WidgetConfigureActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        Robolectric.buildActivity(WidgetConfigureActivity::class.java, intent).use {
            val activity = it.setup().get()
            val root = (activity.findViewById<ViewGroup>(android.R.id.content)).getChildAt(0)
            root.dispatchApplyWindowInsets(WindowInsets.Builder()
                .setInsets(WindowInsets.Type.systemBars(), Insets.of(0, 24, 0, 24))
                .setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, 420))
                .setVisible(WindowInsets.Type.ime(), true).build())
            assertEquals(432, root.paddingBottom)
            assertEquals(View.GONE, (root as ViewGroup).getChildAt(2).visibility)
            assertEquals(View.VISIBLE, activity.findViewById<Button>(R.id.widget_configuration_save).visibility)
        }
    }

    @Test fun invalidNumberInConfigurationDoesNotFinishOrReportADeletedWidget() {
        val id = widget()
        val intent = Intent(app, WidgetConfigureActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        Robolectric.buildActivity(WidgetConfigureActivity::class.java, intent).use {
            val activity = it.setup().get()
            activity.findViewById<EditText>(R.id.widget_spacing_value).setText("")
            activity.findViewById<Button>(R.id.widget_configuration_save).performClick()
            assertFalse(activity.isFinishing)
            assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
            assertEquals(0, WidgetStyleStore.load(app, id).spacingDp)
        }
    }
}
