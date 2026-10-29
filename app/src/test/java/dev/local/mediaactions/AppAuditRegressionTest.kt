package dev.local.mediaactions

import android.content.res.Configuration
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.TextView
import android.widget.Spinner
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
class AppAuditRegressionTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Test fun locationOnlySaveCannotOverwriteANewDefaultWhileTheEditorWasOpen() {
        val manager = AppWidgetManager.getInstance(app)
        val id = shadowOf(manager).createWidget(MediaWidgetProvider::class.java, R.layout.widget_media_controls)
        shadowOf(manager).addBoundWidget(id, AppWidgetProviderInfo().apply { provider = ComponentName(app, MediaWidgetProvider::class.java) })
        val intent = Intent(app, WidgetConfigureActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        Robolectric.buildActivity(WidgetConfigureActivity::class.java, intent).setup().use { c ->
            WidgetStyleStore.save(app, WidgetAppearance(iconScalePercent = 60))
            c.get().findViewById<Spinner>(R.id.widget_location_choice).setSelection(WidgetLocation.LOCK_SCREEN.ordinal)
            shadowOf(android.os.Looper.getMainLooper()).idle()
            c.get().findViewById<View>(R.id.widget_configuration_save).performClick()
        }
        assertEquals(60, WidgetStyleStore.load(app, id).iconScalePercent)
        WidgetStyleStore.save(app, WidgetAppearance(iconScalePercent = 80))
        assertEquals(80, WidgetStyleStore.load(app, id).iconScalePercent)
        assertEquals(WidgetLocation.LOCK_SCREEN, WidgetLocationStore.load(app, id))
    }

    @Test
    @Config(shadows = [ResumeBrowserShadow::class])
    fun lateSpotifyCompletionDoesNotReplaceTheResultOfAMoreRecentTap() {
        ResumeBrowserShadow.instances.clear()
        val spotify = SpotifyPlaybackResumer.SPOTIFY
        shadowOf(app.packageManager).addResolveInfoForIntent(
            Intent(android.service.media.MediaBrowserService.SERVICE_INTERFACE).setPackage(spotify), ResolveInfo().apply {
                serviceInfo = ServiceInfo().apply {
                    name = "spotify.PublicBrowser"; packageName = spotify; exported = true; enabled = true
                    applicationInfo = ApplicationInfo().apply { packageName = spotify; enabled = true }
                }
            })
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationListenerAccessGranted(
            AndroidSessionGateway.listenerComponent(app), true)
        Robolectric.buildActivity(MainActivity::class.java).setup().use { c ->
            c.get().findViewById<View>(R.id.control_toggle).performClick()
            val browser = ResumeBrowserShadow.instances.single()
            try {
                c.get().findViewById<View>(R.id.control_next).performClick()
                fun texts(view: View): List<TextView> = (if (view is TextView) listOf(view) else emptyList()) +
                    (if (view is ViewGroup) (0 until view.childCount).flatMap { texts(view.getChildAt(it)) } else emptyList())
                val result = texts(c.get().findViewById(android.R.id.content)).single { it.text == app.getString(R.string.no_session) }
                browser.callback.onConnectionFailed()
                assertEquals(app.getString(R.string.no_session), result.text.toString())
                assertEquals(SpotifyPlaybackResumer.Status.REJECTED, SpotifyPlaybackResumer.lastStatus(app))
            } finally { browser.callback.onConnectionFailed() }
        }
    }

    @Test fun invalidNumericDraftIsNotSilentlyReplacedByALastValidValueOnRecreation() {
        Robolectric.buildActivity(WidgetDefaultsActivity::class.java).setup().use { c ->
            c.get().findViewById<View>(R.id.settings_spacing).performClick()
            c.get().findViewById<EditText>(R.id.widget_padding_vertical_value).setText("-25")
            c.recreate()
            val input = c.get().findViewById<EditText>(R.id.widget_padding_vertical_value)
            assertEquals("-25", input.text.toString())
            c.get().findViewById<View>(R.id.widget_configuration_save).performClick()
            assertFalse(c.get().isFinishing)
            assertNotNull(input.error)
            assertEquals(0, WidgetStyleStore.load(app).widgetPaddingVerticalDp)
        }
    }

    @Test fun invalidColorDraftStillRequiresCorrectionAfterRecreation() {
        Robolectric.buildActivity(WidgetDefaultsActivity::class.java).setup().use { c ->
            c.get().findViewById<View>(R.id.settings_borders).performClick()
            c.get().findViewById<CompoundButton>(R.id.widget_button_border).performClick()
            c.get().findViewById<EditText>(R.id.widget_button_border_color_value).setText("#123")
            c.recreate()
            val color = c.get().findViewById<EditText>(R.id.widget_button_border_color_value)
            assertEquals("#123", color.text.toString())
            c.get().findViewById<View>(R.id.widget_configuration_save).performClick()
            assertFalse(c.get().isFinishing)
            assertNotNull(color.error)
            assertFalse(WidgetStyleStore.load(app).buttonBorder)
        }
    }

    @Test fun emptyDisabledSpacingDraftSurvivesRecreationAndMustBeCorrectedWhenReenabled() {
        Robolectric.buildActivity(WidgetDefaultsActivity::class.java).setup().use { c ->
            c.get().findViewById<EditText>(R.id.widget_spacing_value).setText("")
            c.get().findViewById<CompoundButton>(R.id.widget_fill_width).performClick()
            c.recreate()
            val field = c.get().findViewById<EditText>(R.id.widget_spacing_value)
            assertEquals("", field.text.toString()); assertFalse(field.isEnabled)
            c.get().findViewById<CompoundButton>(R.id.widget_fill_width).performClick()
            c.get().findViewById<View>(R.id.widget_configuration_save).performClick()
            assertFalse(c.get().isFinishing); assertNotNull(field.error)
            field.setText("28")
            c.get().findViewById<View>(R.id.widget_configuration_save).performClick()
            assertEquals(28, WidgetStyleStore.load(app).spacingDp)
        }
    }

    @Test fun validTextCursorAndCancelSurviveRecreationWithoutSavingEarly() {
        Robolectric.buildActivity(WidgetDefaultsActivity::class.java).setup().use { c ->
            c.get().findViewById<EditText>(R.id.widget_scale_value).apply { setText("75"); setSelection(1) }
            c.recreate()
            val field = c.get().findViewById<EditText>(R.id.widget_scale_value)
            assertEquals("75", field.text.toString()); assertEquals(1, field.selectionStart)
            assertEquals(100, WidgetStyleStore.load(app).iconScalePercent)
            c.get().findViewById<View>(R.id.widget_configuration_cancel).performClick()
            assertEquals(100, WidgetStyleStore.load(app).iconScalePercent)
        }
    }

    @Test fun originalVisualSurvivesRecreationAndPreservesNewExternalSettingsUnlessTheDraftChanged() {
        for (mode in listOf("inherited", "custom", "reverted", "edited")) {
            WidgetStyleStore.save(app, WidgetAppearance())
            val manager = AppWidgetManager.getInstance(app)
            val id = shadowOf(manager).createWidget(MediaWidgetProvider::class.java, R.layout.widget_media_controls)
            shadowOf(manager).addBoundWidget(id, AppWidgetProviderInfo().apply { provider = ComponentName(app, MediaWidgetProvider::class.java) })
            if (mode == "custom") WidgetStyleStore.save(app, WidgetAppearance(iconScalePercent = 40), id)
            val intent = Intent(app, WidgetConfigureActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            Robolectric.buildActivity(WidgetConfigureActivity::class.java, intent).setup().use { c ->
                val scale = c.get().findViewById<EditText>(R.id.widget_scale_value)
                if (mode in listOf("reverted", "edited")) scale.setText("70")
                if (mode == "reverted") scale.setText("100")
                WidgetStyleStore.save(app, WidgetAppearance(iconScalePercent = 60), if (mode == "custom") id else null)
                c.recreate()
                c.get().findViewById<View>(R.id.widget_configuration_save).performClick()
            }
            assertEquals(mode, if (mode == "edited") 70 else 60, WidgetStyleStore.load(app, id).iconScalePercent)
            WidgetStyleStore.save(app, WidgetAppearance(iconScalePercent = 80))
            assertEquals(mode, when (mode) { "edited" -> 70; "custom" -> 60; else -> 80 }, WidgetStyleStore.load(app, id).iconScalePercent)
        }
    }

    @Test fun allAppearanceFieldsRemainIndependentAcrossDraftRecreation() {
        val manager = AppWidgetManager.getInstance(app)
        val id = shadowOf(manager).createWidget(MediaWidgetProvider::class.java, R.layout.widget_media_controls)
        shadowOf(manager).addBoundWidget(id, AppWidgetProviderInfo().apply { provider = ComponentName(app, MediaWidgetProvider::class.java) })
        val style = WidgetAppearance(iconScalePercent = 60, spacingDp = 27, darkIcons = true,
            iconStyle = WidgetIconStyle.PHOSPHOR_THIN, fillWidth = true, iconShadow = true, touchFeedback = false,
            widgetBorder = true, borderWidthDp = 3, borderColor = 0x88445566.toInt(), buttonBorder = true,
            buttonBorderWidthDp = 7, buttonBorderColor = 0xFFAABBCC.toInt(), widgetPaddingHorizontalDp = 18,
            widgetPaddingVerticalDp = -12, centerBorderOnly = true, progressRing = true)
        WidgetStyleStore.save(app, style, id)
        val intent = Intent(app, WidgetConfigureActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        Robolectric.buildActivity(WidgetConfigureActivity::class.java, intent).setup().use { c ->
            c.get().findViewById<EditText>(R.id.widget_scale_value).setText("61")
            c.recreate()
            c.get().findViewById<View>(R.id.widget_configuration_save).performClick()
        }
        assertEquals(style.copy(iconScalePercent = 61), WidgetStyleStore.load(app, id))
    }

    @Test fun reusableInstanceEditorCanSaveAndThenReturnToItsOriginalValue() {
        val manager = AppWidgetManager.getInstance(app)
        val id = shadowOf(manager).createWidget(MediaWidgetProvider::class.java, R.layout.widget_media_controls)
        shadowOf(manager).addBoundWidget(id, AppWidgetProviderInfo().apply { provider = ComponentName(app, MediaWidgetProvider::class.java) })
        val editor = WidgetAppearanceSection(app, id)
        val view = editor.create()
        view.findViewById<EditText>(R.id.widget_scale_value).setText("65")
        assertTrue(editor.applyChanges()); assertEquals(65, WidgetStyleStore.load(app, id).iconScalePercent)
        view.findViewById<EditText>(R.id.widget_scale_value).setText("100")
        assertTrue(editor.applyChanges()); assertEquals(100, WidgetStyleStore.load(app, id).iconScalePercent)
    }

    @Test
    @Config(qualifiers = "pt-rBR-w320dp-h640dp-mdpi")
    fun saveAndCancelRemainFullyVisibleWithMeasurementsAndTwoHundredPercentFonts() {
        app.resources.updateConfiguration(Configuration(app.resources.configuration).apply { fontScale = 2f }, app.resources.displayMetrics)
        Robolectric.buildActivity(WidgetDefaultsActivity::class.java).setup().use { c ->
            c.get().findViewById<View>(R.id.widget_preview_details).performClick()
            val root = (c.get().findViewById<ViewGroup>(android.R.id.content)).getChildAt(0)
            root.dispatchApplyWindowInsets(android.view.WindowInsets.Builder().build())
            root.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 320, 640)
            for (id in listOf(R.id.widget_configuration_save, R.id.widget_configuration_cancel)) {
                val button = c.get().findViewById<View>(id)
                val visible = Rect()
                assertTrue(button.getGlobalVisibleRect(visible))
                assertEquals("The whole action must remain reachable", button.height, visible.height())
                assertTrue(visible.bottom <= 640)
            }
        }
    }
}
