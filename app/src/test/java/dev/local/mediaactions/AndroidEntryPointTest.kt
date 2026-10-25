package dev.local.mediaactions

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

/** Framework simulation only: these tests cannot establish Samsung or physical-device behavior. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 36])
class AndroidEntryPointTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Test fun manifestDeclaresThreeResolvableStaticShortcuts() {
        val launcher = app.packageManager.getActivityInfo(ComponentName(app, MainActivity::class.java),
            PackageManager.GET_META_DATA)
        val parser = launcher.loadXmlMetaData(app.packageManager, "android.app.shortcuts")!!
        val ids = mutableSetOf<String>()
        val intents = mutableListOf<Intent>()
        val ns = "http://schemas.android.com/apk/res/android"
        parser.use {
            while (it.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (it.eventType != org.xmlpull.v1.XmlPullParser.START_TAG) continue
                if (it.name == "shortcut") ids += it.getAttributeValue(ns, "shortcutId")
                if (it.name == "intent") intents += Intent(it.getAttributeValue(ns, "action"),
                    Uri.parse(it.getAttributeValue(ns, "data"))).setClassName(
                    it.getAttributeValue(ns, "targetPackage"), it.getAttributeValue(ns, "targetClass"))
            }
        }
        assertEquals(setOf("next_track", "previous_track", "toggle_playback"), ids)
        assertEquals(3, intents.size)
        for (intent in intents) {
            assertEquals(ComponentName(app, ActionActivity::class.java), intent.component)
            assertNotNull(ShortcutRequest.parse(intent.action, intent.dataString))
            assertNotNull(app.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY))
        }
    }

    @Test fun noNotificationGrantIsReportedWithoutCrashing() {
        val listener = AndroidSessionGateway.listenerComponent(app)
        assertFalse(app.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(listener))
        val dispatcher = MediaActionDispatcher(AndroidSessionGateway(app))
        assertEquals(ActionResult.MissingNotificationAccess, dispatcher.execute(MediaAction.NEXT))
        assertEquals(PlayerStatus.MissingNotificationAccess, dispatcher.playerStatus())
    }

    @Test fun validShortcutFinishesWithoutPermissionPromptOrNewActivity() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("mediaactions://run/next"))
        Robolectric.buildActivity(ActionActivity::class.java, intent).use { controller ->
            val activity = controller.create().get()
            assertTrue(activity.isFinishing)
            assertNull(org.robolectric.Shadows.shadowOf(activity).nextStartedActivity)
        }
    }

    @Test fun invalidShortcutAndRecreationFinishImmediately() {
        val invalid = Intent(Intent.ACTION_VIEW, Uri.parse("mediaactions://run/next?package=other"))
        Robolectric.buildActivity(ActionActivity::class.java, invalid).use { controller ->
            assertTrue(controller.create().get().isFinishing)
        }
        val valid = Intent(Intent.ACTION_VIEW, Uri.parse("mediaactions://run/next"))
        Robolectric.buildActivity(ActionActivity::class.java, valid).use { controller ->
            assertTrue(controller.create(Bundle()).get().isFinishing)
        }
    }

    @Test fun settingsScreenCanCreateResumeAndRefreshWithoutAccess() {
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            val activity = controller.setup().get()
            assertFalse(activity.isFinishing)
            assertNotNull(activity.findViewById<android.view.View>(android.R.id.content))
        }
    }

    @Test fun productionManifestHasNoRequestedPermissionsAndProtectedListener() {
        val info = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES)
        assertTrue(info.requestedPermissions.isNullOrEmpty())
        val service = info.services!!.single()
        assertFalse(service.exported)
        assertEquals("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE", service.permission)
    }

    private fun grantSimulatedAccess() {
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationListenerAccessGranted(
            AndroidSessionGateway.listenerComponent(app), true)
    }

    @Test fun grantedAccessWithNoControllersProducesNoSession() {
        grantSimulatedAccess()
        val dispatcher = MediaActionDispatcher(AndroidSessionGateway(app))
        assertEquals(ActionResult.NoActiveSession, dispatcher.execute(MediaAction.NEXT))
        assertEquals(PlayerStatus.NoActiveSession, dispatcher.playerStatus())
    }

    @Test fun androidAdapterMapsStatesAndAdvertisedTransportActions() {
        grantSimulatedAccess()
        val session = MediaSession(app, "host-test")
        try {
            val controller = session.controller
            shadowOf(controller).setPackageName("test.player")
            shadowOf(controller).setPlaybackState(PlaybackState.Builder()
                .setState(PlaybackState.STATE_PLAYING, 0, 1f)
                .setActions(PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackState.ACTION_PAUSE).build())
            shadowOf(app.getSystemService(MediaSessionManager::class.java)).addController(controller)
            val dispatcher = MediaActionDispatcher(AndroidSessionGateway(app))
            val controls = shadowOf(controller.transportControls)
            for ((action, expected) in listOf(MediaAction.NEXT to PlaybackState.ACTION_SKIP_TO_NEXT,
                MediaAction.PREVIOUS to PlaybackState.ACTION_SKIP_TO_PREVIOUS,
                MediaAction.PLAY_PAUSE to PlaybackState.ACTION_PAUSE)) {
                assertEquals(ActionResult.CommandSent("test.player"), dispatcher.execute(action))
                assertEquals(expected, controls.lastPerformedAction)
            }
            shadowOf(controller).setPlaybackState(PlaybackState.Builder()
                .setState(PlaybackState.STATE_PAUSED, 0, 0f)
                .setActions(PlaybackState.ACTION_PLAY).build())
            assertEquals(ActionResult.CommandSent("test.player"), dispatcher.execute(MediaAction.PLAY_PAUSE))
            assertEquals(PlaybackState.ACTION_PLAY, controls.lastPerformedAction)
            assertEquals(ActionResult.UnsupportedAction, dispatcher.execute(MediaAction.NEXT))
            assertEquals(PlaybackState.ACTION_PLAY, controls.lastPerformedAction)
        } finally {
            session.release()
        }
    }

    @Test fun recreationDoesNotSendMediaCommand() {
        grantSimulatedAccess()
        val session = MediaSession(app, "recreation-test")
        try {
            val controller = session.controller
            shadowOf(controller).setPackageName("test.player")
            shadowOf(controller).setPlaybackState(PlaybackState.Builder()
                .setState(PlaybackState.STATE_PLAYING, 0, 1f)
                .setActions(PlaybackState.ACTION_SKIP_TO_NEXT).build())
            shadowOf(app.getSystemService(MediaSessionManager::class.java)).addController(controller)
            val controls = shadowOf(controller.transportControls)
            val initialAction = controls.lastPerformedAction
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("mediaactions://run/next"))
            Robolectric.buildActivity(ActionActivity::class.java, intent).use {
                assertTrue(it.create(Bundle()).get().isFinishing)
            }
            assertEquals(initialAction, controls.lastPerformedAction)
            Robolectric.buildActivity(ActionActivity::class.java, intent).use {
                assertTrue(it.create().get().isFinishing)
            }
            assertEquals(PlaybackState.ACTION_SKIP_TO_NEXT, controls.lastPerformedAction)
        } finally {
            session.release()
        }
    }
}
