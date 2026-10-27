package dev.local.mediaactions

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.RadioButton
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
class WidgetConfigurationTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val manager get() = AppWidgetManager.getInstance(app)

    private fun widget(): Int {
        val id = shadowOf(manager).createWidget(MediaWidgetProvider::class.java, R.layout.widget_media_controls)
        shadowOf(manager).addBoundWidget(id, AppWidgetProviderInfo().apply {
            provider = ComponentName(app, MediaWidgetProvider::class.java)
            initialLayout = R.layout.widget_media_controls
        })
        return id
    }

    private fun request(id: Int) = Intent(app, WidgetConfigureActivity::class.java)
        .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)

    private fun children(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { children(view.getChildAt(it)) } else emptyList()

    private fun chooseDark(activity: Activity) {
        children(activity.findViewById(android.R.id.content)).filterIsInstance<RadioButton>()
            .first { it.text == app.getString(R.string.widget_icons_dark) }.isChecked = true
    }

    @Test fun providerMetadataEnablesTheHostEditEntry() {
        val receiver = app.packageManager.getReceiverInfo(ComponentName(app, MediaWidgetProvider::class.java),
            PackageManager.GET_META_DATA)
        receiver.loadXmlMetaData(app.packageManager, AppWidgetManager.META_DATA_APPWIDGET_PROVIDER)!!.use { xml ->
            while (xml.next() != org.xmlpull.v1.XmlPullParser.START_TAG) { /* Root. */ }
            val ns = "http://schemas.android.com/apk/res/android"
            assertEquals(WidgetConfigureActivity::class.java.name, xml.getAttributeValue(ns, "configure"))
            assertEquals(AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE or
                AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL,
                xml.getAttributeIntValue(ns, "widgetFeatures", 0))
        }
        val info = app.packageManager.getActivityInfo(ComponentName(app, WidgetConfigureActivity::class.java), 0)
        assertTrue(info.exported)
        assertNotNull(app.packageManager.resolveActivity(request(42), PackageManager.MATCH_DEFAULT_ONLY))
    }

    @Test fun validConfigurationOpensDirectlyWithACanceledDefaultResult() {
        val id = widget()
        Robolectric.buildActivity(WidgetConfigureActivity::class.java, request(id)).use {
            val activity = it.setup().get()
            assertFalse(activity.isFinishing)
            assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
            assertEquals(id, shadowOf(activity).resultIntent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0))
            assertNull(shadowOf(activity).nextStartedActivity)
            assertNotNull(activity.findViewById<View>(R.id.widget_configuration_save))
        }
    }

    @Test fun savingUpdatesOnlyTheRequestedWidgetAndReturnsItsId() {
        val first = widget()
        val second = widget()
        Robolectric.buildActivity(WidgetConfigureActivity::class.java, request(first)).use {
            val activity = it.setup().get()
            chooseDark(activity)
            activity.findViewById<Button>(R.id.widget_configuration_save).performClick()
            assertTrue(activity.isFinishing)
            assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
            assertEquals(first, shadowOf(activity).resultIntent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0))
            assertTrue(WidgetStyleStore.load(app, first).darkIcons)
            assertFalse(WidgetStyleStore.load(app, second).darkIcons)
            assertFalse(WidgetStyleStore.load(app).darkIcons)
            val image = shadowOf(manager).getViewFor(first).findViewById<ImageView>(R.id.widget_next)
            assertEquals(PorterDuffColorFilter(Color.rgb(26, 29, 34), PorterDuff.Mode.SRC_ATOP), image.colorFilter)
            assertNull(shadowOf(activity).nextStartedActivity)
        }
    }

    @Test fun cancelDoesNotPersistDraftEdits() {
        val id = widget()
        Robolectric.buildActivity(WidgetConfigureActivity::class.java, request(id)).use {
            val activity = it.setup().get()
            chooseDark(activity)
            activity.findViewById<Button>(R.id.widget_configuration_cancel).performClick()
            assertTrue(activity.isFinishing)
            assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
            assertFalse(WidgetStyleStore.load(app, id).darkIcons)
        }
    }

    @Test fun invalidMissingAndForeignIdsCancelImmediately() {
        val foreign = widget()
        shadowOf(manager).addBoundWidget(foreign, AppWidgetProviderInfo().apply {
            provider = ComponentName("another.app", "another.app.Widget")
        })
        val requests = listOf(Intent(app, WidgetConfigureActivity::class.java), request(0), request(404), request(foreign))
        for (intent in requests) Robolectric.buildActivity(WidgetConfigureActivity::class.java, intent).use {
            val activity = it.create().get()
            assertTrue(activity.isFinishing)
            assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
        }
    }

    @Test fun recreationKeepsTheUnsavedDraftWithoutSavingItEarly() {
        val id = widget()
        val first = Robolectric.buildActivity(WidgetConfigureActivity::class.java, request(id)).setup()
        chooseDark(first.get())
        val state = Bundle()
        first.pause().saveInstanceState(state).stop().destroy()
        assertFalse(WidgetStyleStore.load(app, id).darkIcons)
        Robolectric.buildActivity(WidgetConfigureActivity::class.java, request(id)).use {
            val activity = it.create(state).start().resume().visible().get()
            val dark = children(activity.findViewById(android.R.id.content)).filterIsInstance<RadioButton>()
                .first { view -> view.text == app.getString(R.string.widget_icons_dark) }
            assertTrue(dark.isChecked)
            assertFalse(WidgetStyleStore.load(app, id).darkIcons)
            activity.findViewById<Button>(R.id.widget_configuration_save).performClick()
            assertTrue(WidgetStyleStore.load(app, id).darkIcons)
        }
    }

    @Test fun aWidgetRemovedWhileEditingCannotBeSaved() {
        val id = widget()
        Robolectric.buildActivity(WidgetConfigureActivity::class.java, request(id)).use {
            val activity = it.setup().get()
            chooseDark(activity)
            // The shadow has no public delete-bound-widget helper; remove its test record.
            org.robolectric.util.ReflectionHelpers.getField<MutableMap<Int, *>>(
                shadowOf(manager), "widgetInfos").remove(id)
            activity.findViewById<Button>(R.id.widget_configuration_save).performClick()
            assertFalse(activity.isFinishing)
            assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
            assertFalse(WidgetStyleStore.load(app, id).darkIcons)
        }
    }

    @Test fun deletionClearsOnlyTheDeletedWidgetsOverride() {
        val first = widget()
        val second = widget()
        WidgetStyleStore.save(app, WidgetAppearance(darkIcons = true), first)
        WidgetStyleStore.save(app, WidgetAppearance(iconScalePercent = 35), second)
        MediaWidgetProvider().onDeleted(app, intArrayOf(first))
        assertEquals(WidgetStyleStore.load(app), WidgetStyleStore.load(app, first))
        assertEquals(35, WidgetStyleStore.load(app, second).iconScalePercent)
    }

    @Test fun changingGlobalDefaultsPreservesIndividuallyEditedWidgets() {
        val first = widget()
        val second = widget()
        val custom = WidgetAppearance(iconScalePercent = 35, darkIcons = true, iconStyle = WidgetIconStyle.CHEVRONS)
        WidgetStyleStore.save(app, custom, first)
        val defaults = WidgetAppearance(iconScalePercent = 70, spacingDp = 32)
        WidgetStyleStore.save(app, defaults)
        MediaWidgetProvider.refreshAll(app)
        assertEquals(custom, WidgetStyleStore.load(app, first))
        assertEquals(defaults, WidgetStyleStore.load(app, second))
    }
}
