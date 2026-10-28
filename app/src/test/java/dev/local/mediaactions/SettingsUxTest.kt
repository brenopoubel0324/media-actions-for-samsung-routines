package dev.local.mediaactions

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.view.View
import android.view.ViewGroup
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
class SettingsUxTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private fun widget(): Int {
        val manager = AppWidgetManager.getInstance(app)
        val id = shadowOf(manager).createWidget(MediaWidgetProvider::class.java, R.layout.widget_media_controls)
        shadowOf(manager).addBoundWidget(id, AppWidgetProviderInfo().apply { provider = ComponentName(app, MediaWidgetProvider::class.java) })
        return id
    }
    private fun intent(id: Int) = Intent(app, WidgetConfigureActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
    private fun content(header: View) = (header.parent as ViewGroup).getChildAt(1)
    @Test fun homeOpensAPrivateDefaultsEditorAndDoesNotEmbedTheWholeEditor() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { activity ->
            assertNull(activity.get().findViewById<View>(R.id.widget_progress_ring))
            activity.get().findViewById<View>(R.id.widget_defaults).performClick()
            assertEquals(ComponentName(app, WidgetDefaultsActivity::class.java), shadowOf(activity.get()).nextStartedActivity.component)
        }
        assertFalse(app.packageManager.getActivityInfo(ComponentName(app, WidgetDefaultsActivity::class.java), 0).exported)
    }
    @Test fun progressCanBeEnabledAndDisabledWithoutBordersAndDraftSurvivesRecreation() {
        val id = widget(); val other = widget()
        Robolectric.buildActivity(WidgetConfigureActivity::class.java, intent(id)).setup().use { c ->
            val header = c.get().findViewById<View>(R.id.settings_playback)
            header.performClick()
            val toggle = c.get().findViewById<CompoundButton>(R.id.widget_progress_ring)
            assertTrue(toggle.isShown); assertFalse(toggle.isChecked)
            toggle.performClick()
            assertFalse(WidgetStyleStore.load(app, id).progressRing)
            c.recreate()
            assertTrue(c.get().findViewById<CompoundButton>(R.id.widget_progress_ring).isChecked)
            assertEquals(View.VISIBLE, content(c.get().findViewById(R.id.settings_playback)).visibility)
            c.get().findViewById<View>(R.id.widget_configuration_save).performClick()
        }
        assertTrue(WidgetStyleStore.load(app, id).progressRing)
        assertFalse(WidgetStyleStore.load(app, id).buttonBorder)
        assertFalse(WidgetStyleStore.load(app, other).progressRing)
        Robolectric.buildActivity(WidgetConfigureActivity::class.java, intent(id)).setup().use { c ->
            c.get().findViewById<View>(R.id.settings_playback).performClick()
            c.get().findViewById<CompoundButton>(R.id.widget_progress_ring).performClick()
            c.get().findViewById<View>(R.id.widget_configuration_save).performClick()
        }
        assertFalse(WidgetStyleStore.load(app, id).progressRing)
    }
    @Test fun defaultsSaveUpdatesInheritedWidgetsButPreservesIndividualOverridesAndCancelDiscards() {
        val inherited = widget(); val customized = widget()
        WidgetStyleStore.save(app, WidgetAppearance(progressRing = false), customized)
        Robolectric.buildActivity(WidgetDefaultsActivity::class.java).setup().use { c ->
            assertFalse(c.get().isFinishing)
            c.get().findViewById<View>(R.id.settings_playback).performClick()
            c.get().findViewById<CompoundButton>(R.id.widget_progress_ring).performClick()
            c.get().findViewById<View>(R.id.widget_configuration_save).performClick()
        }
        assertTrue(WidgetStyleStore.load(app).progressRing)
        assertTrue(WidgetStyleStore.load(app, inherited).progressRing)
        assertFalse(WidgetStyleStore.load(app, customized).progressRing)
        Robolectric.buildActivity(WidgetDefaultsActivity::class.java).setup().use { c ->
            c.get().findViewById<CompoundButton>(R.id.widget_progress_ring).performClick()
            c.get().findViewById<View>(R.id.widget_configuration_cancel).performClick()
        }
        assertTrue(WidgetStyleStore.load(app).progressRing)
    }
    @Test fun invalidValueInACollapsedSectionReopensTheRelevantGroupWithoutSaving() {
        val id = widget()
        Robolectric.buildActivity(WidgetConfigureActivity::class.java, intent(id)).setup().use { c ->
            val spacing = c.get().findViewById<View>(R.id.settings_spacing)
            assertEquals(View.GONE, content(spacing).visibility)
            c.get().findViewById<EditText>(R.id.widget_padding_vertical_value).setText("-25")
            c.get().findViewById<View>(R.id.widget_configuration_save).performClick()
            assertFalse(c.get().isFinishing)
            assertEquals(View.VISIBLE, content(spacing).visibility)
            assertEquals(0, WidgetStyleStore.load(app, id).widgetPaddingVerticalDp)
            assertNotNull(c.get().findViewById<EditText>(R.id.widget_padding_vertical_value).error)
        }
    }
    @Test fun onlyOneGroupIsExpandedAndPreviewNeverSendsAMediaCommand() {
        val editor = WidgetAppearanceSection(app); val view = editor.create()
        val icons = view.findViewById<View>(R.id.settings_icons)
        val playback = view.findViewById<View>(R.id.settings_playback)
        icons.performClick(); assertEquals(View.VISIBLE, content(icons).visibility)
        playback.performClick(); assertEquals(View.GONE, content(icons).visibility)
        assertEquals(View.VISIBLE, content(playback).visibility)
        view.findViewById<View>(R.id.widget_preview_playing).performClick()
        view.findViewById<View>(R.id.widget_preview_unknown).performClick()
        assertTrue(shadowOf(app).broadcastIntents.none { it.action == "dev.local.mediaactions.WIDGET_CONTROL" })
        assertNull(shadowOf(app).nextStartedActivity)
        assertFalse(WidgetStyleStore.load(app).progressRing)
    }
    @Test
    @Config(qualifiers = "pt-rBR-w320dp-h640dp-mdpi")
    fun saveRemainsReachableWithLargeTextAndKeyboardOnASmallWindow() {
        val configuration = android.content.res.Configuration(app.resources.configuration).apply { fontScale = 1.3f }
        app.resources.updateConfiguration(configuration, app.resources.displayMetrics)
        Robolectric.buildActivity(WidgetDefaultsActivity::class.java).setup().use { c ->
            val root = (c.get().findViewById<ViewGroup>(android.R.id.content)).getChildAt(0)
            root.dispatchApplyWindowInsets(android.view.WindowInsets.Builder()
                .setInsets(android.view.WindowInsets.Type.ime(), android.graphics.Insets.of(0, 0, 0, 280))
                .setVisible(android.view.WindowInsets.Type.ime(), true).build())
            root.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 320, 640)
            val save = c.get().findViewById<View>(R.id.widget_configuration_save)
            val visible = android.graphics.Rect()
            assertTrue(save.getGlobalVisibleRect(visible))
            assertTrue(visible.bottom <= 360)
            assertTrue(save.height >= 48)
            assertEquals(View.GONE, (root as ViewGroup).getChildAt(2).visibility)
        }
    }

}
