package dev.local.mediaactions

import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.util.SizeF
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 36], shadows = [VolumeAudioShadow::class, CountingSessionsShadow::class,
    CountingControllerShadow::class, CountingWidgetsShadow::class])
class PlaybackVolumeWidgetTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val manager get() = AppWidgetManager.getInstance(app)
    private val audio get() = app.getSystemService(AudioManager::class.java)
    private val audioShadow get() = Shadow.extract<VolumeAudioShadow>(audio)
    private val provider get() = PlaybackVolumeWidgetProvider()
    private fun options() = Bundle().apply {
        putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(240f, 64f)))
    }
    private fun widget(kind: WidgetKind = WidgetKind.PLAYBACK_VOLUME): Int {
        val type = when (kind) {
            WidgetKind.MEDIA -> MediaWidgetProvider::class.java
            WidgetKind.VOLUME -> VolumeWidgetProvider::class.java
            WidgetKind.PLAYBACK_VOLUME -> PlaybackVolumeWidgetProvider::class.java
        }
        val layout = when (kind) {
            WidgetKind.MEDIA -> R.layout.widget_media_controls
            WidgetKind.VOLUME -> R.layout.widget_volume_controls
            WidgetKind.PLAYBACK_VOLUME -> R.layout.widget_playback_volume_controls
        }
        val id = shadowOf(manager).createWidget(type, layout)
        shadowOf(manager).addBoundWidget(id, AppWidgetProviderInfo().apply { provider = ComponentName(app, type) })
        manager.updateAppWidgetOptions(id, options())
        EnergyCounters.tracked += id
        WidgetOptionsUpdates.forget(app, intArrayOf(id))
        return id
    }
    private fun command(id: Int, action: PlaybackVolumeWidgetProvider.Command) =
        Intent(shadowOf(PlaybackVolumeWidgetProvider.controlIntent(app, id, action)).savedIntent)
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Before fun reset() {
        EnergyCounters.clear(); EnergyCounters.tracked.clear(); EnergyCounters.state = null
        EnergyCounters.metadata = null; EnergyCounters.failSessionReads = 0; EnergyCounters.failUpdates = 0
    }
    @After fun clean() {
        EnergyCounters.state = null; EnergyCounters.metadata = null; EnergyCounters.tracked.clear()
        EnergyCounters.failSessionReads = 0; EnergyCounters.failUpdates = 0
    }

    @Test fun registryDistinguishesAllThreeInstalledKindsAndTheirCommandsStayIsolated() {
        val combined = widget(); val media = widget(WidgetKind.MEDIA); val volume = widget(WidgetKind.VOLUME)
        assertEquals(WidgetKind.PLAYBACK_VOLUME, WidgetRegistry.kind(app, combined))
        assertEquals(WidgetKind.MEDIA, WidgetRegistry.kind(app, media))
        assertEquals(WidgetKind.VOLUME, WidgetRegistry.kind(app, volume))
        assertEquals(setOf(combined, media, volume), WidgetRegistry.ids(app).toSet())
        assertEquals(setOf(combined, media), WidgetRegistry.playbackIds(app).toSet())
        assertFalse(WidgetRegistry.isVolume(app, combined))
        assertEquals(app.getString(R.string.playback_volume_widget_name), WidgetRegistry.label(app, combined))
        assertNull(WidgetRegistry.kind(app, 999999))
        assertEquals(MediaWidgetProvider.RefreshResult.MISSING_WIDGET, WidgetRegistry.refresh(app, 999999))
        VolumeWidgetProvider().onReceive(app, Intent(shadowOf(VolumeWidgetProvider.controlIntent(app, combined, true)).savedIntent))
        assertTrue(audioShadow.calls.isEmpty())
        assertFalse(MediaWidgetProvider.isOwnWidget(app, combined))
        assertFalse(VolumeWidgetProvider.isOwnWidget(app, combined))
    }

    @Test fun immutableTokensAreDistinctPerCommandInstanceAndProvider() {
        val tokens = mutableSetOf<PendingIntent>()
        for (id in listOf(42, 43)) for (action in PlaybackVolumeWidgetProvider.Command.entries) {
            val token = PlaybackVolumeWidgetProvider.controlIntent(app, id, action)
            tokens += token
            assertTrue(shadowOf(token).isImmutable)
            assertTrue(shadowOf(token).isBroadcast)
            assertEquals(ComponentName(app, PlaybackVolumeWidgetProvider::class.java), shadowOf(token).savedIntent.component)
            assertNotEquals(VolumeWidgetProvider.controlIntent(app, id, true), token)
            assertNotEquals(MediaWidgetProvider.controlIntent(app, id, MediaAction.PLAY_PAUSE), token)
        }
        assertEquals(6, tokens.size)
    }

    @Test fun wrongProviderRemovedIdsMismatchedUriActionAndComponentCannotAdjustVolume() {
        val combined = widget(); val media = widget(WidgetKind.MEDIA); val volume = widget(WidgetKind.VOLUME)
        val action = PlaybackVolumeWidgetProvider.Command.RAISE
        for (invalid in listOf(
            command(media, action), command(volume, action),
            command(combined, action).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, media),
            command(combined, action).setData(Uri.parse("mediaactions://playback-volume/$combined/up?other=1")),
            command(combined, action).setComponent(ComponentName(app, VolumeWidgetProvider::class.java)),
            command(combined, action).setAction("unknown.action"), command(combined, action).setData(null),
        )) provider.onReceive(app, invalid)
        ReflectionHelpers.getField<MutableMap<Int, *>>(shadowOf(manager), "widgetInfos").remove(combined)
        provider.onReceive(app, command(combined, action))
        assertTrue(audioShadow.calls.isEmpty())
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test fun tenVolumeClicksNeedNoSessionReadsMetadataUpdatesOrNotificationGrant() {
        val id = widget()
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, 5, 0)
        val alarm = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        WidgetPlaybackObserver(app).use { observer ->
            observer.start(); idle(); EnergyCounters.clear(); audioShadow.calls.clear()
            repeat(10) { provider.onReceive(app, command(id, if (it % 2 == 0)
                PlaybackVolumeWidgetProvider.Command.RAISE else PlaybackVolumeWidgetProvider.Command.LOWER)) }
            idle()
            assertEquals(10, audioShadow.calls.size)
            assertTrue(audioShadow.calls.all { it.stream == AudioManager.STREAM_MUSIC && it.flags == 0 })
            assertEquals(0, EnergyCounters.sessions)
            assertEquals(0, EnergyCounters.progress)
            assertEquals(0, EnergyCounters.updates)
        }
        assertEquals(5, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        assertEquals(alarm, audio.getStreamVolume(AudioManager.STREAM_ALARM))
        assertNull(shadowOf(app).nextStartedActivity)
        assertTrue(shadowOf(app).broadcastIntents.none { it.action == Intent.ACTION_MEDIA_BUTTON })
    }

    @Test fun fixedAndPolicyDeniedVolumeAreNeverReplayed() {
        val id = widget(); audioShadow.calls.clear()
        audioShadow.fixed = true
        provider.onReceive(app, command(id, PlaybackVolumeWidgetProvider.Command.RAISE))
        assertTrue(audioShadow.calls.isEmpty())
        audioShadow.fixed = false; audioShadow.deny = true
        provider.onReceive(app, command(id, PlaybackVolumeWidgetProvider.Command.LOWER))
        idle(); assertEquals(1, audioShadow.calls.size)
        audioShadow.deny = false; idle(); assertEquals(1, audioShadow.calls.size)
    }

    @Test fun warmAuthorizedSessionVolumeClicksDoNotAddMediaWork() {
        val id = widget()
        WidgetStyleStore.save(app, WidgetAppearance(progressRing = false), id)
        shadowOf(app.getSystemService(NotificationManager::class.java))
            .setNotificationListenerAccessGranted(AndroidSessionGateway.listenerComponent(app), true)
        val session = MediaSession(app, "combined-warm-volume")
        try {
            shadowOf(session.controller).setPackageName("test.player")
            EnergyCounters.state = PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 1000, 1f, 1L)
                .setActions(PlaybackState.ACTION_PAUSE).build()
            shadowOf(app.getSystemService(MediaSessionManager::class.java)).addController(session.controller)
            WidgetPlaybackObserver(app).use { observer ->
                observer.start(); idle()
                assertEquals(1, shadowOf(session.controller).callbacks.size)
                EnergyCounters.clear(); audioShadow.calls.clear()
                repeat(10) { provider.onReceive(app, command(id, if (it % 2 == 0)
                    PlaybackVolumeWidgetProvider.Command.RAISE else PlaybackVolumeWidgetProvider.Command.LOWER)) }
                idle()
                assertEquals(10, audioShadow.calls.size)
                assertEquals(0, EnergyCounters.sessions)
                assertEquals(0, EnergyCounters.progress)
                assertEquals(0, EnergyCounters.updates)
                val file = java.io.File("build/outputs/energy/combined-volume-warm-${android.os.Build.VERSION.SDK_INT}.json")
                file.parentFile!!.mkdirs()
                file.writeText("""{"scenario":"combined-volume-warm","gestures":10,"audio_adjustments":10,"session_reads":0,"metadata_reads":0,"widget_updates":0,"battery_measured":false}
""")
            }
        } finally { session.release() }
    }

    @Test fun toggleUsesTheExistingPlaybackDispatcherWithoutAnAudioStepOrActivity() {
        val id = widget()
        shadowOf(app.getSystemService(NotificationManager::class.java))
            .setNotificationListenerAccessGranted(AndroidSessionGateway.listenerComponent(app), true)
        val session = MediaSession(app, "combined-toggle")
        try {
            shadowOf(session.controller).setPackageName("test.player")
            EnergyCounters.state = PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f)
                .setActions(PlaybackState.ACTION_PAUSE).build()
            shadowOf(app.getSystemService(MediaSessionManager::class.java)).addController(session.controller)
            audioShadow.calls.clear()
            provider.onReceive(app, command(id, PlaybackVolumeWidgetProvider.Command.TOGGLE)); idle()
            assertEquals(PlaybackState.ACTION_PAUSE, shadowOf(session.controller.transportControls).lastPerformedAction)
            assertTrue(audioShadow.calls.isEmpty())
            assertNull(shadowOf(app).nextStartedActivity)
        } finally { session.release() }
    }

    @Test fun revokedPlaybackAccessDoesNotSendMediaOrSuppressTheNextVolumeGesture() {
        val id = widget()
        val session = MediaSession(app, "combined-revoked")
        try {
            shadowOf(session.controller).setPackageName("test.player")
            EnergyCounters.state = PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f)
                .setActions(PlaybackState.ACTION_PAUSE).build()
            shadowOf(app.getSystemService(MediaSessionManager::class.java)).addController(session.controller)
            shadowOf(app.getSystemService(NotificationManager::class.java))
                .setNotificationListenerAccessGranted(AndroidSessionGateway.listenerComponent(app), false)
            val before = shadowOf(session.controller.transportControls).lastPerformedAction
            audioShadow.calls.clear()
            provider.onReceive(app, command(id, PlaybackVolumeWidgetProvider.Command.TOGGLE)); idle()
            assertEquals(before, shadowOf(session.controller.transportControls).lastPerformedAction)
            assertEquals(app.getString(R.string.playback_volume_missing_access), org.robolectric.shadows.ShadowToast.getTextOfLatestToast())
            provider.onReceive(app, command(id, PlaybackVolumeWidgetProvider.Command.RAISE))
            assertEquals(1, audioShadow.calls.size)
            assertNull(shadowOf(app).nextStartedActivity)
        } finally { session.release() }
    }

    @Test fun deletionCancelsOnlyTheDeletedInstancesTokensAndSettings() {
        val deleted = widget(); val retained = widget()
        val tokens = PlaybackVolumeWidgetProvider.Command.entries.map { PlaybackVolumeWidgetProvider.controlIntent(app, deleted, it) }
        val other = PlaybackVolumeWidgetProvider.controlIntent(app, retained, PlaybackVolumeWidgetProvider.Command.RAISE)
        WidgetStyleStore.save(app, WidgetAppearance(iconScalePercent = 60), deleted)
        WidgetLocationStore.save(app, deleted, WidgetLocation.LOCK_SCREEN)
        provider.onDeleted(app, intArrayOf(deleted))
        assertTrue(tokens.all { shadowOf(it).isCanceled })
        assertFalse(shadowOf(other).isCanceled)
        assertFalse(WidgetStyleStore.hasOverride(app, deleted))
        assertEquals(WidgetLocation.AUTOMATIC, WidgetLocationStore.load(app, deleted))
    }

    @Test fun providerMetadataAllowsBothAxesWithoutPeriodicUpdatesAndKeepsEditor() {
        val info = app.packageManager.getReceiverInfo(ComponentName(app, PlaybackVolumeWidgetProvider::class.java),
            android.content.pm.PackageManager.GET_META_DATA)
        assertFalse(info.exported)
        info.loadXmlMetaData(app.packageManager, AppWidgetManager.META_DATA_APPWIDGET_PROVIDER)!!.use { xml ->
            while (xml.next() != org.xmlpull.v1.XmlPullParser.START_TAG) { }
            val ns = "http://schemas.android.com/apk/res/android"
            assertEquals(0, xml.getAttributeIntValue(ns, "updatePeriodMillis", -1))
            assertEquals(AppWidgetProviderInfo.RESIZE_BOTH, xml.getAttributeIntValue(ns, "resizeMode", 0))
            assertEquals(WidgetConfigureActivity::class.java.name, xml.getAttributeValue(ns, "configure"))
            assertEquals(R.layout.widget_playback_volume_controls, xml.getAttributeResourceValue(ns, "initialKeyguardLayout", 0))
        }
    }
}
