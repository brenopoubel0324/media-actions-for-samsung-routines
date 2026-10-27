package dev.local.mediaactions

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 36])
class WearLauncherTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val entries = mapOf<Class<out Activity>, MediaAction>(PreviousActionActivity::class.java to MediaAction.PREVIOUS,
        ToggleActionActivity::class.java to MediaAction.PLAY_PAUSE, NextActionActivity::class.java to MediaAction.NEXT)
    @Test fun wbrStyleLauncherQueryFindsExactlyThreeDistinctComponentsAndLabels() {
        val resolved = app.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(app.packageName), 0)
        assertEquals(entries.keys.map { it.name }.toSet(), resolved.map { it.activityInfo.name }.toSet())
        assertEquals(setOf("MA · Anterior", "MA · Play/Pause", "MA · Próxima"), resolved.map { it.loadLabel(app.packageManager).toString() }.toSet())
        assertEquals(3, resolved.map { it.activityInfo.icon }.distinct().size)
        for (entry in resolved) {
            assertTrue(entry.activityInfo.exported)
            assertNotEquals(0, entry.activityInfo.flags and android.content.pm.ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS)
        }
    }
    @Test fun explicitWbrIntentWithoutActionAndLauncherMainBothDispatchCorrectFixedAction() {
        for ((type, action) in entries) for (intentAction in listOf(null, Intent.ACTION_MAIN)) {
            shadowOf(app).clearBroadcastIntents()
            val intent = Intent().setComponent(ComponentName(app, type)).setAction(intentAction)
                .putExtra(WearActionReceiver.EXTRA_ACTION, "UNTRUSTED_OVERRIDE")
            Robolectric.buildActivity(type, intent).use {
                val activity = it.create().get()
                assertTrue(activity.isFinishing)
                assertNull(shadowOf(activity).nextStartedActivity)
                val sent = shadowOf(app).broadcastIntents.single()
                assertEquals(ComponentName(app, WearActionReceiver::class.java), sent.component)
                assertEquals(action.name, sent.getStringExtra(WearActionReceiver.EXTRA_ACTION))
            }
        }
    }
    @Test fun recreationOrForeignActionDataNeverDispatches() {
        for (intent in listOf(Intent(Intent.ACTION_VIEW), Intent(Intent.ACTION_MAIN, Uri.parse("mediaactions://run/next")))) {
            shadowOf(app).clearBroadcastIntents()
            Robolectric.buildActivity(NextActionActivity::class.java, intent).use { assertTrue(it.create().get().isFinishing) }
            assertTrue(shadowOf(app).broadcastIntents.isEmpty())
        }
        shadowOf(app).clearBroadcastIntents()
        Robolectric.buildActivity(NextActionActivity::class.java, Intent(Intent.ACTION_MAIN)).use { assertTrue(it.create(Bundle()).get().isFinishing) }
        assertTrue(shadowOf(app).broadcastIntents.isEmpty())
    }
    @Test fun receiverIsPrivateAndSetupIsNotALauncherEntry() {
        val info = app.packageManager.getReceiverInfo(ComponentName(app, WearActionReceiver::class.java), 0)
        assertFalse(info.exported)
        val setup = app.packageManager.getActivityInfo(ComponentName(app, MediaAccessSettingsActivity::class.java), 0)
        assertTrue(setup.exported)
    }
}
