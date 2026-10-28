package dev.local.mediaactions

import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.RippleDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Looper
import android.util.SizeF
import android.view.View
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.ImageButton
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNotificationListenerService

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 36])
class WidgetPlaybackTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val manager get() = AppWidgetManager.getInstance(app)
    private val sessions get() = app.getSystemService(MediaSessionManager::class.java)
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun grant(access: Boolean = true) = shadowOf(app.getSystemService(NotificationManager::class.java))
        .setNotificationListenerAccessGranted(AndroidSessionGateway.listenerComponent(app), access)

    private fun state(value: Int, position: Long = 0) = PlaybackState.Builder()
        .setState(value, position, if (value == PlaybackState.STATE_PLAYING) 1f else 0f)
        .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE).build()

    private fun controller(session: MediaSession, value: Int): MediaController = session.controller.apply {
        shadowOf(this).setPackageName("test.player")
        shadowOf(this).setPlaybackState(state(value))
        shadowOf(sessions).addController(this)
    }

    private fun widget(): Int {
        val id = shadowOf(manager).createWidget(MediaWidgetProvider::class.java, R.layout.widget_media_controls)
        shadowOf(manager).addBoundWidget(id, AppWidgetProviderInfo().apply {
            provider = ComponentName(app, MediaWidgetProvider::class.java)
            initialLayout = R.layout.widget_media_controls
        })
        manager.updateAppWidgetOptions(id, Bundle().apply {
            putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(360f, 64f)))
        })
        return id
    }

    private fun center(id: Int) = shadowOf(manager).getViewFor(id).findViewById<ImageButton>(R.id.widget_toggle)

    @Test fun externalPlaybackChangesUpdateEveryWidgetWithoutAWidgetTap() {
        grant()
        val first = widget()
        val second = widget()
        val session = MediaSession(app, "external-state")
        try {
            val player = controller(session, PlaybackState.STATE_PAUSED)
            WidgetStyleStore.save(app, WidgetAppearance(darkIcons = true, iconShadow = true), second)
            WidgetPlaybackObserver(app).use { observer ->
                observer.start(); idle()
                assertEquals("Play", center(first).contentDescription)
                shadowOf(player).executeOnPlaybackStateChanged(state(PlaybackState.STATE_PLAYING))
                idle()
                assertEquals("Pause", center(first).contentDescription)
                assertEquals("Pause", center(second).contentDescription)
                shadowOf(player).executeOnPlaybackStateChanged(state(PlaybackState.STATE_PAUSED))
                idle()
                assertEquals("Play", center(first).contentDescription)
                assertEquals("Play", center(second).contentDescription)
                assertTrue(WidgetStyleStore.load(app, second).darkIcons)
            }
            assertTrue(shadowOf(player).callbacks.isEmpty())
        } finally { session.release() }
    }

    @Test fun aTapDoesNotPretendThatAnIgnoredPauseCommandSucceeded() {
        grant()
        val id = widget()
        val session = MediaSession(app, "ignored-command")
        try {
            val player = controller(session, PlaybackState.STATE_PLAYING)
            MediaWidgetProvider.refreshAll(app)
            // Robolectric has no real AppWidgetHostView to deliver RemoteViews interactions.
            val request = shadowOf(MediaWidgetProvider.controlIntent(app, id, MediaAction.PLAY_PAUSE)).savedIntent
            MediaWidgetProvider().onReceive(app, request); idle()
            assertEquals(PlaybackState.ACTION_PAUSE, shadowOf(player.transportControls).lastPerformedAction)
            assertEquals("Pause", center(id).contentDescription)
        } finally { session.release() }
    }

    @Test fun observerFindsNewPlayersAndClearsRemovedSessions() {
        grant()
        val id = widget()
        val session = MediaSession(app, "new-session")
        try {
            WidgetPlaybackObserver(app).use { observer ->
                observer.start(); idle()
                assertTrue(center(id).contentDescription.contains("unavailable"))
                val player = controller(session, PlaybackState.STATE_PLAYING)
                idle()
                assertEquals("Pause", center(id).contentDescription)
                shadowOf(sessions).clearControllers(); idle()
                assertTrue(center(id).contentDescription.contains("unavailable"))
                assertTrue(shadowOf(player).callbacks.isEmpty())
            }
        } finally { session.release() }
    }

    @Test fun repeatedUpdatesAndMetadataDoNotCauseUnnecessaryWidgetRedraws() {
        grant()
        val session = MediaSession(app, "duplicate-state")
        try {
            val player = controller(session, PlaybackState.STATE_PLAYING)
            val changes = mutableListOf<WidgetPlaybackState>()
            WidgetPlaybackObserver(app) { changes += it }.use { observer ->
                observer.start(); observer.start(); idle()
                assertEquals(1, shadowOf(player).callbacks.size)
                for (position in 1L..3L) {
                    shadowOf(player).executeOnPlaybackStateChanged(state(PlaybackState.STATE_PLAYING, position))
                    idle()
                }
                shadowOf(player).executeOnMetadataChanged(MediaMetadata.Builder().putString(
                    MediaMetadata.METADATA_KEY_TITLE, "Not used by the widget").build())
                idle()
                assertEquals(listOf(WidgetPlaybackState.PLAYING), changes)
                shadowOf(player).executeOnPlaybackStateChanged(state(PlaybackState.STATE_BUFFERING)); idle()
                assertEquals(WidgetPlaybackState.TRANSITIONING, changes.last())
                shadowOf(player).executeOnPlaybackStateChanged(state(PlaybackState.STATE_PLAYING)); idle()
                assertEquals(3, changes.size)
            }
        } finally { session.release() }
    }

    @Test fun closingCancelsPendingUpdatesAndUnregistersAllControllerCallbacks() {
        grant()
        val session = MediaSession(app, "close-observer")
        try {
            val player = controller(session, PlaybackState.STATE_PLAYING)
            val changes = mutableListOf<WidgetPlaybackState>()
            val observer = WidgetPlaybackObserver(app) { changes += it }
            observer.start()
            observer.close(); observer.close(); idle()
            assertTrue(shadowOf(player).callbacks.isEmpty())
            assertTrue(changes.isEmpty())
            shadowOf(player).executeOnPlaybackStateChanged(state(PlaybackState.STATE_PAUSED)); idle()
            assertTrue(changes.isEmpty())
        } finally { session.release() }
    }

    @Test fun anUpdaterFailureDoesNotCrashObservationOrSuppressTheNextSameStateEvent() {
        grant()
        val session = MediaSession(app, "failed-update")
        try {
            val player = controller(session, PlaybackState.STATE_PLAYING)
            var attempts = 0
            val changes = mutableListOf<WidgetPlaybackState>()
            WidgetPlaybackObserver(app) {
                attempts++
                if (attempts == 1) throw IllegalStateException("Simulated host failure")
                changes += it
            }.use { observer ->
                observer.start(); idle()
                assertTrue(changes.isEmpty())
                shadowOf(player).executeOnPlaybackStateChanged(state(PlaybackState.STATE_PLAYING, 100)); idle()
                assertEquals(listOf(WidgetPlaybackState.PLAYING), changes)
            }
        } finally { session.release() }
    }

    @Test fun configurationRecreationPreservesUnsavedFeedbackChoice() {
        val id = widget()
        val intent = Intent(app, WidgetConfigureActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        val first = Robolectric.buildActivity(WidgetConfigureActivity::class.java, intent).setup()
        first.get().findViewById<CompoundButton>(R.id.widget_touch_feedback).performClick()
        val saved = Bundle()
        first.pause().saveInstanceState(saved).stop().destroy()
        assertTrue(WidgetStyleStore.load(app, id).touchFeedback)
        Robolectric.buildActivity(WidgetConfigureActivity::class.java, intent).use {
            val activity = it.create(saved).start().resume().visible().get()
            assertFalse(activity.findViewById<CompoundButton>(R.id.widget_touch_feedback).isChecked)
            activity.findViewById<android.widget.Button>(R.id.widget_configuration_save).performClick()
            assertFalse(WidgetStyleStore.load(app, id).touchFeedback)
        }
    }

    @Test fun permissionRevocationCannotLeaveAConfirmedPlayingState() {
        grant()
        val id = widget()
        val session = MediaSession(app, "revocation")
        try {
            val player = controller(session, PlaybackState.STATE_PLAYING)
            WidgetPlaybackObserver(app).use { observer ->
                observer.start(); idle()
                assertEquals("Pause", center(id).contentDescription)
                grant(false)
                shadowOf(player).executeOnPlaybackStateChanged(state(PlaybackState.STATE_PAUSED)); idle()
                assertTrue(center(id).contentDescription.contains("unavailable"))
            }
        } finally { session.release() }
    }

    @Test fun listenerReconnectDoesNotAccumulateObserversAndDisconnectMarksUnknown() {
        grant()
        val id = widget()
        val session = MediaSession(app, "listener-lifecycle")
        try {
            val player = controller(session, PlaybackState.STATE_PLAYING)
            val controller = Robolectric.buildService(MediaNotificationListener::class.java).create()
            try {
                val service = controller.get()
                service.onListenerConnected(); idle()
                service.onListenerConnected(); idle()
                assertEquals(1, shadowOf(player).callbacks.size)
                assertEquals("Pause", center(id).contentDescription)
                val rebinds = ShadowNotificationListenerService.getRebindRequestCount()
                service.onListenerDisconnected(); idle()
                assertEquals(rebinds + 1, ShadowNotificationListenerService.getRebindRequestCount())
                assertTrue(shadowOf(player).callbacks.isEmpty())
                assertTrue(center(id).contentDescription.contains("unavailable"))
                service.onListenerConnected(); idle()
                assertEquals("Pause", center(id).contentDescription)
            } finally { controller.destroy() }
            assertTrue(shadowOf(player).callbacks.isEmpty())
        } finally { session.release() }
    }

    @Test fun disconnectedListenerDoesNotRequestRebindAfterTheUserRevokesAccess() {
        grant()
        val controller = Robolectric.buildService(MediaNotificationListener::class.java).create()
        try {
            val service = controller.get()
            service.onListenerConnected(); idle()
            grant(false)
            val rebinds = ShadowNotificationListenerService.getRebindRequestCount()
            service.onListenerDisconnected(); idle()
            assertEquals(rebinds, ShadowNotificationListenerService.getRebindRequestCount())
        } finally { controller.destroy() }
    }

    @Test fun transitionStateUsesANeutralGlyphAndWaitLabel() {
        val root = WidgetRenderer.build(app, 42, WidgetAppearance(), SizeF(240f, 64f),
            playback = WidgetPlaybackState.TRANSITIONING).apply(app, FrameLayout(app))
        val button = root.findViewById<ImageButton>(R.id.widget_toggle)
        assertEquals(app.getString(R.string.widget_transitioning), button.contentDescription)
        assertEquals(150, button.imageAlpha)
    }

    @Test fun touchFeedbackCanBeDisabledAndPersistedPerWidget() {
        val id = widget()
        val editor = WidgetAppearanceSection(app, id)
        val page = editor.create()
        val toggle = page.findViewById<CompoundButton>(R.id.widget_touch_feedback)
        assertTrue(toggle.isChecked)
        toggle.performClick()
        assertTrue(WidgetStyleStore.load(app, id).touchFeedback)
        assertTrue(editor.applyChanges())
        assertFalse(WidgetStyleStore.load(app, id).touchFeedback)
        assertTrue(WidgetStyleStore.load(app).touchFeedback)
        val root = WidgetRenderer.render(app, id, Bundle()).apply(app, FrameLayout(app))
        assertFalse(root.findViewById<ImageButton>(R.id.widget_toggle).background is RippleDrawable)
    }

    @Test fun rippleIsNativeAndAvailableForBothIconColors() {
        for (dark in listOf(false, true)) {
            val root = WidgetRenderer.build(app, 42, WidgetAppearance(darkIcons = dark), SizeF(240f, 64f))
                .apply(app, FrameLayout(app))
            for (id in listOf(R.id.widget_previous, R.id.widget_toggle, R.id.widget_next)) {
                assertTrue(root.findViewById<ImageButton>(id).background is RippleDrawable)
            }
        }
    }

    @Test fun tappingAnAppearancePreviewDoesNotSendMediaCommands() {
        grant()
        val session = MediaSession(app, "preview-feedback-only")
        try {
            val player = controller(session, PlaybackState.STATE_PLAYING)
            val initial = shadowOf(player.transportControls).lastPerformedAction
            val page = WidgetAppearanceSection(app).create()
            page.findViewById<View>(R.id.widget_toggle).performClick(); idle()
            assertEquals(initial, shadowOf(player.transportControls).lastPerformedAction)
        } finally { session.release() }
    }
}
