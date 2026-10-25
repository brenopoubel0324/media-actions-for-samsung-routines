package dev.local.mediaactions

import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Simulates standard Android widgets. LockStar's host and keyguard policy still need a phone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 36])
class MediaWidgetTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val manager get() = AppWidgetManager.getInstance(app)
    private val providerComponent get() = ComponentName(app, MediaWidgetProvider::class.java)

    @Before fun registerProviderForSimulatedBroadcastDelivery() {
        // Robolectric matches static-receiver filters even for explicit broadcasts.
        // Register the same private provider in this test process so PendingIntent taps
        // can reach onReceive. Production uses the explicit manifest receiver directly.
        val probe = shadowOf(MediaWidgetProvider.controlIntent(app, 42, MediaAction.NEXT)).savedIntent
        app.registerReceiver(MediaWidgetProvider(), IntentFilter(probe.action).apply {
            addDataScheme("mediaactions")
        }, Context.RECEIVER_NOT_EXPORTED)
    }

    private fun bind(id: Int = 42) {
        shadowOf(manager).addBoundWidget(id, AppWidgetProviderInfo().apply {
            provider = providerComponent
            initialLayout = R.layout.widget_media_controls
        })
    }

    private fun grantAccess(allowed: Boolean = true) {
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationListenerAccessGranted(
            AndroidSessionGateway.listenerComponent(app), allowed)
    }

    private fun withPlayer(block: (MediaController) -> Unit) {
        grantAccess()
        val session = MediaSession(app, "widget-host-test")
        try {
            val controller = session.controller
            shadowOf(controller).setPackageName("test.player")
            shadowOf(controller).setPlaybackState(PlaybackState.Builder()
                .setState(PlaybackState.STATE_PLAYING, 0, 1f)
                .setActions(PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackState.ACTION_PAUSE).build())
            shadowOf(app.getSystemService(MediaSessionManager::class.java)).addController(controller)
            block(controller)
        } finally {
            session.release()
        }
    }

    private fun send(pending: PendingIntent) {
        pending.send()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun providerIsPrivateResizableAndHasNoPeriodicUpdates() {
        val info = app.packageManager.getReceiverInfo(providerComponent, PackageManager.GET_META_DATA)
        assertFalse(info.exported)
        info.loadXmlMetaData(app.packageManager, AppWidgetManager.META_DATA_APPWIDGET_PROVIDER)!!.use { xml ->
            while (xml.next() != org.xmlpull.v1.XmlPullParser.START_TAG) { /* Find root. */ }
            val ns = "http://schemas.android.com/apk/res/android"
            assertEquals("appwidget-provider", xml.name)
            assertEquals(AppWidgetProviderInfo.RESIZE_BOTH, xml.getAttributeIntValue(ns, "resizeMode", 0))
            assertEquals(0, xml.getAttributeIntValue(ns, "updatePeriodMillis", -1))
            assertEquals(AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN or AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD,
                xml.getAttributeIntValue(ns, "widgetCategory", 0))
            assertEquals(R.layout.widget_media_controls, xml.getAttributeResourceValue(ns, "initialKeyguardLayout", 0))
        }
    }

    @Test fun controlsFitMinimumAndLargerSizesWithAccessibleHitAreas() {
        val density = app.resources.displayMetrics.density
        for ((width, height) in listOf(156 to 56, 240 to 64, 360 to 112)) {
            val root = MediaWidgetProvider.createViews(app, 42).apply(app, FrameLayout(app))
            val widthPx = (width * density).toInt()
            val heightPx = (height * density).toInt()
            root.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, widthPx, heightPx)
            for (id in listOf(R.id.widget_previous, R.id.widget_toggle, R.id.widget_next)) {
                val control = root.findViewById<View>(id)
                assertTrue("Touch width at $width × $height", control.width >= 48 * density - 1)
                assertTrue("Touch height at $width × $height", control.height >= 48 * density - 1)
                assertTrue(control.left >= 0 && control.right <= widthPx)
                assertTrue(control.top >= 0 && control.bottom <= heightPx)
                assertFalse(control.contentDescription.isNullOrBlank())
                assertTrue(control.hasOnClickListeners())
            }
        }
    }

    @Test fun eachControlAndWidgetHasADistinctImmutableBroadcastToken() {
        val tokens = mutableSetOf<PendingIntent>()
        for (id in listOf(42, 43)) for (action in MediaAction.entries) {
            val pending = MediaWidgetProvider.controlIntent(app, id, action)
            assertTrue(shadowOf(pending).isImmutable)
            assertTrue(shadowOf(pending).isBroadcast)
            assertEquals(providerComponent, shadowOf(pending).savedIntent.component)
            tokens += pending
        }
        assertEquals(6, tokens.size)
    }

    @Test fun tappingTheRenderedControlsDispatchesWithoutOpeningAnActivity() = withPlayer { controller ->
        bind()
        val root = MediaWidgetProvider.createViews(app, 42).apply(app, FrameLayout(app))
        val controls = shadowOf(controller.transportControls)
        for ((id, expected) in listOf(R.id.widget_previous to PlaybackState.ACTION_SKIP_TO_PREVIOUS,
            R.id.widget_toggle to PlaybackState.ACTION_PAUSE, R.id.widget_next to PlaybackState.ACTION_SKIP_TO_NEXT)) {
            assertTrue(root.findViewById<View>(id).performClick())
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(expected, controls.lastPerformedAction)
        }
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test fun revokedAccessAndAbsentSessionsDoNotLaunchOrControlAnything() = withPlayer { controller ->
        bind()
        val controls = shadowOf(controller.transportControls)
        val initial = controls.lastPerformedAction
        grantAccess(false)
        send(MediaWidgetProvider.controlIntent(app, 42, MediaAction.NEXT))
        assertEquals(initial, controls.lastPerformedAction)
        grantAccess()
        shadowOf(app.getSystemService(MediaSessionManager::class.java)).clearControllers()
        send(MediaWidgetProvider.controlIntent(app, 42, MediaAction.PLAY_PAUSE))
        assertEquals(initial, controls.lastPerformedAction)
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test fun unknownRemovedAndMalformedWidgetRequestsDoNothing() = withPlayer { controller ->
        val controls = shadowOf(controller.transportControls)
        val initial = controls.lastPerformedAction
        send(MediaWidgetProvider.controlIntent(app, 404, MediaAction.NEXT))
        bind()
        val original = shadowOf(MediaWidgetProvider.controlIntent(app, 42, MediaAction.NEXT)).savedIntent
        for (invalid in listOf(Intent(original).setData(Uri.parse("mediaactions://widget/42/next?package=other")),
            Intent(original).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 43),
            Intent(original).setAction("unknown.action"), Intent(original).setData(null))) {
            MediaWidgetProvider().onReceive(app, invalid)
        }
        assertEquals(initial, controls.lastPerformedAction)
    }

    @Test fun hostCannotChangeTheCommandThroughFillInData() = withPlayer { controller ->
        bind()
        val pending = MediaWidgetProvider.controlIntent(app, 42, MediaAction.NEXT)
        pending.send(app, 0, Intent("different.action", Uri.parse("mediaactions://widget/42/previous")))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(PlaybackState.ACTION_SKIP_TO_NEXT, shadowOf(controller.transportControls).lastPerformedAction)
    }

    @Test fun hostUpdatesAndResizeNeverSendPlaybackCommands() = withPlayer { controller ->
        val id = shadowOf(manager).createWidget(MediaWidgetProvider::class.java, R.layout.widget_media_controls)
        val controls = shadowOf(controller.transportControls)
        val initial = controls.lastPerformedAction
        MediaWidgetProvider().onUpdate(app, manager, intArrayOf(id))
        MediaWidgetProvider().onAppWidgetOptionsChanged(app, manager, id, Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 240)
        })
        assertEquals(initial, controls.lastPerformedAction)
        assertNotNull(shadowOf(manager).getViewFor(id).findViewById<View>(R.id.widget_next))
    }

    @Test fun deletingAWidgetCancelsItsTokensWithoutAffectingAnotherWidget() {
        val deleted = MediaAction.entries.map { MediaWidgetProvider.controlIntent(app, 42, it) }
        val other = MediaWidgetProvider.controlIntent(app, 43, MediaAction.NEXT)
        MediaWidgetProvider().onDeleted(app, intArrayOf(42))
        assertTrue(deleted.all { shadowOf(it).isCanceled })
        assertFalse(shadowOf(other).isCanceled)
    }
}
