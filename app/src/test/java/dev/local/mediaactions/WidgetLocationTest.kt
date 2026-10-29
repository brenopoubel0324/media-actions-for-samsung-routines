package dev.local.mediaactions

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 36])
class WidgetLocationTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val manager get() = AppWidgetManager.getInstance(app)
    private fun widget(category: Int = 0): Int {
        val id = shadowOf(manager).createWidget(MediaWidgetProvider::class.java, R.layout.widget_media_controls)
        shadowOf(manager).addBoundWidget(id, AppWidgetProviderInfo().apply { provider = ComponentName(app, MediaWidgetProvider::class.java) })
        manager.updateAppWidgetOptions(id, Bundle().apply {
            if (category != 0) putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, category)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 300)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 64)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 64)
        })
        return id
    }
    private fun request(id: Int) = Intent(app, WidgetConfigureActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
    private fun choose(activity: WidgetConfigureActivity, location: WidgetLocation) {
        activity.findViewById<Spinner>(R.id.widget_location_choice).setSelection(location.ordinal)
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test fun missingInvalidAndCombinedHintsAreUnknownRatherThanAssumedHome() {
        for (value in listOf(0, -1, 3, 8, 99)) {
            val options = Bundle().apply { putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, value) }
            assertNull(WidgetLocationStore.hostLocation(options))
        }
        assertNull(WidgetLocationStore.hostLocation(Bundle()))
        val id = widget()
        assertEquals(app.getString(R.string.widget_location_unknown), WidgetLocationStore.description(app, id))
        assertEquals(WidgetLocation.AUTOMATIC, WidgetLocationStore.load(app, id))
    }

    @Test fun automaticHintsAreReadLiveAndDistinguishedFromManualLabels() {
        val id = widget(AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD)
        assertEquals(app.getString(R.string.widget_location_reported, app.getString(R.string.widget_location_lock_screen)), WidgetLocationStore.description(app, id))
        manager.updateAppWidgetOptions(id, Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
        })
        assertEquals(app.getString(R.string.widget_location_reported, app.getString(R.string.widget_location_home_screen)), WidgetLocationStore.description(app, id))
        WidgetLocationStore.save(app, id, WidgetLocation.LOCK_SCREEN)
        assertEquals(app.getString(R.string.widget_location_manual, app.getString(R.string.widget_location_lock_screen)), WidgetLocationStore.description(app, id))
        assertEquals(WidgetLocation.OTHER, WidgetLocationStore.hostLocation(Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_SEARCHBOX)
        }))
        WidgetLocationStore.save(app, id, WidgetLocation.AUTOMATIC)
        assertTrue(WidgetLocationStore.description(app, id).contains(app.getString(R.string.widget_location_home_screen)))
    }

    @Test fun locationDraftSurvivesRecreationButPersistsOnlyWithSaveForThisInstance() {
        val id = widget(); val other = widget()
        val appearance = WidgetAppearance(progressRing = true, widgetPaddingVerticalDp = -8)
        WidgetStyleStore.save(app, appearance, id)
        Robolectric.buildActivity(WidgetConfigureActivity::class.java, request(id)).setup().use { c ->
            c.get().findViewById<View>(R.id.settings_location).performClick()
            choose(c.get(), WidgetLocation.LOCK_SCREEN)
            assertEquals(WidgetLocation.AUTOMATIC, WidgetLocationStore.load(app, id))
            c.recreate()
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(WidgetLocation.LOCK_SCREEN.ordinal, c.get().findViewById<Spinner>(R.id.widget_location_choice).selectedItemPosition)
            c.get().findViewById<View>(R.id.widget_configuration_save).performClick()
        }
        assertEquals(WidgetLocation.LOCK_SCREEN, WidgetLocationStore.load(app, id))
        assertEquals(WidgetLocation.AUTOMATIC, WidgetLocationStore.load(app, other))
        assertEquals(appearance, WidgetStyleStore.load(app, id))
        Robolectric.buildActivity(WidgetConfigureActivity::class.java, request(id)).setup().use { c ->
            choose(c.get(), WidgetLocation.HOME_SCREEN)
            c.get().findViewById<View>(R.id.widget_configuration_cancel).performClick()
        }
        assertEquals(WidgetLocation.LOCK_SCREEN, WidgetLocationStore.load(app, id))
    }

    @Test fun validationFailureAndDeletedWidgetDoNotSaveALocationDraft() {
        val id = widget()
        Robolectric.buildActivity(WidgetConfigureActivity::class.java, request(id)).setup().use { c ->
            choose(c.get(), WidgetLocation.LOCK_SCREEN)
            c.get().findViewById<EditText>(R.id.widget_padding_vertical_value).setText("-25")
            c.get().findViewById<View>(R.id.widget_configuration_save).performClick()
            assertFalse(c.get().isFinishing)
            assertEquals(WidgetLocation.AUTOMATIC, WidgetLocationStore.load(app, id))
            assertTrue(c.get().findViewById<TextView>(R.id.widget_configuration_identity).text.contains(WidgetLocationStore.instanceLabel(app, id)))
            c.get().findViewById<EditText>(R.id.widget_padding_vertical_value).setText("0")
            shadowOf(manager).addBoundWidget(id, AppWidgetProviderInfo().apply { provider = ComponentName("foreign.app", "Widget") })
            c.get().findViewById<View>(R.id.widget_configuration_save).performClick()
            assertFalse(c.get().isFinishing)
            assertEquals(WidgetLocation.AUTOMATIC, WidgetLocationStore.load(app, id))
        }
    }

    @Test fun chooserShowsLocationNumberAndDimensionsAndOpensTheSelectedInstance() {
        val lock = widget(AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD)
        val home = widget(AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
        val unknown = widget()
        val ids = listOf(lock, home, unknown).sorted()
        Robolectric.buildActivity(MainActivity::class.java).setup().use { c ->
            c.get().findViewById<View>(R.id.widget_edit_existing).performClick()
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            for ((position, id) in ids.withIndex()) {
                val text = dialog.listView.adapter.getItem(position).toString()
                assertTrue(text.contains(WidgetLocationStore.description(app, id)))
                assertTrue(text.contains(WidgetLocationStore.instanceLabel(app, id)))
            }
            val position = ids.indexOf(home)
            dialog.listView.performItemClick(null, position, position.toLong())
            assertEquals(home, shadowOf(c.get()).nextStartedActivity.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0))
        }
        Robolectric.buildActivity(WidgetConfigureActivity::class.java, request(lock)).setup().use { c ->
            assertTrue(c.get().findViewById<TextView>(R.id.widget_configuration_identity).text.contains(WidgetLocationStore.instanceLabel(app, lock)))
        }
    }

    @Test fun defaultsCannotLabelAnInstanceAndDeletionClearsOnlyTheRemovedLabel() {
        val id = widget(); val other = widget()
        WidgetLocationStore.save(app, id, WidgetLocation.LOCK_SCREEN)
        WidgetLocationStore.save(app, other, WidgetLocation.OTHER)
        Robolectric.buildActivity(WidgetDefaultsActivity::class.java).setup().use { c ->
            assertNull(c.get().findViewById<View>(R.id.settings_location))
            c.get().findViewById<View>(R.id.widget_configuration_save).performClick()
        }
        assertEquals(WidgetLocation.LOCK_SCREEN, WidgetLocationStore.load(app, id))
        MediaWidgetProvider().onDeleted(app, intArrayOf(id))
        assertEquals(WidgetLocation.AUTOMATIC, WidgetLocationStore.load(app, id))
        assertEquals(WidgetLocation.OTHER, WidgetLocationStore.load(app, other))
    }

    @Test fun labellingAnInheritedWidgetKeepsFollowingDefaultsWhileCustomWidgetsKeepTheirStyle() {
        val inherited = widget(); val custom = widget()
        WidgetStyleStore.save(app, WidgetAppearance(iconScalePercent = 40), custom)
        for (id in listOf(inherited, custom)) {
            Robolectric.buildActivity(WidgetConfigureActivity::class.java, request(id)).setup().use { c ->
                choose(c.get(), WidgetLocation.LOCK_SCREEN)
                c.get().findViewById<View>(R.id.widget_configuration_save).performClick()
            }
        }
        WidgetStyleStore.save(app, WidgetAppearance(iconScalePercent = 70))
        assertEquals(70, WidgetStyleStore.load(app, inherited).iconScalePercent)
        assertEquals(40, WidgetStyleStore.load(app, custom).iconScalePercent)
        assertEquals(WidgetLocation.LOCK_SCREEN, WidgetLocationStore.load(app, inherited))
        assertEquals(WidgetLocation.LOCK_SCREEN, WidgetLocationStore.load(app, custom))
    }
}
