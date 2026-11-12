package dev.local.mediaactions

import android.app.NotificationManager
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.widget.Spinner
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowWallpaperManager
import java.util.concurrent.atomic.AtomicInteger

object WallpaperProbe {
    val reads=AtomicInteger();var adds=0;var removes=0
    var callback:WallpaperManager.OnColorsChangedListener?=null
    @Volatile var colors:WallpaperColors?=null
}
@Implements(WallpaperManager::class)
class ObservedWallpaperShadow:ShadowWallpaperManager() {
    @Implementation fun getWallpaperColors(which:Int):WallpaperColors? { WallpaperProbe.reads.incrementAndGet();return WallpaperProbe.colors }
    @Implementation fun addOnColorsChangedListener(callback:WallpaperManager.OnColorsChangedListener,handler:Handler?) { WallpaperProbe.adds++;WallpaperProbe.callback=callback }
    @Implementation fun removeOnColorsChangedListener(callback:WallpaperManager.OnColorsChangedListener) { WallpaperProbe.removes++;if(WallpaperProbe.callback===callback)WallpaperProbe.callback=null }
}
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33,36],shadows=[ObservedWallpaperShadow::class,CountingSessionsShadow::class,CountingWidgetsShadow::class])
class WallpaperRuntimeTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private val runtime get()=WidgetWallpaperRuntime.get(app)!!
    private val auto=WidgetAppearance(colorMode=WidgetColorMode.WALLPAPER,wallpaperTarget=WidgetWallpaperTarget.HOME)
    private fun idle()=shadowOf(Looper.getMainLooper()).idle()
    @Before fun setup() {
        WallpaperProbe.reads.set(0);WallpaperProbe.adds=0;WallpaperProbe.removes=0;WallpaperProbe.callback=null;WallpaperProbe.colors=null
        EnergyCounters.tracked.clear();EnergyCounters.clear();EnergyCounters.failUpdates=0;EnergyCounters.failSessionReads=0
        shadowOf(app.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        runtime.coordinator.screen(true);idle()
    }
    @After fun teardown(){runtime.coordinator.close();EnergyCounters.tracked.clear()}
    private fun widget(volume:Boolean=false,style:WidgetAppearance=auto):Int {
        val manager=AppWidgetManager.getInstance(app);val provider=if(volume)VolumeWidgetProvider::class.java else MediaWidgetProvider::class.java
        val id=shadowOf(manager).createWidget(provider,if(volume)R.layout.widget_volume_controls else R.layout.widget_media_controls)
        shadowOf(manager).addBoundWidget(id,AppWidgetProviderInfo().apply{this.provider=ComponentName(app,provider)})
        WidgetStyleStore.save(app,style,id);EnergyCounters.tracked+=id;return id
    }
    private fun settleReads(expected:Int) {
        val deadline=System.nanoTime()+2_000_000_000L
        while(WallpaperProbe.reads.get()<expected && System.nanoTime()<deadline){Thread.sleep(5);idle()}
        // The source posts completion immediately after counting a read; give that post a turn.
        Thread.sleep(10);idle();assertEquals(expected,WallpaperProbe.reads.get())
    }
    private fun event(dark:Boolean?,which:Int=WallpaperManager.FLAG_SYSTEM) {
        val colors=dark?.let{WallpaperColors(Color.valueOf(if(it)Color.WHITE else Color.BLACK),null,null)}
        WallpaperProbe.callback!!.onColorsChanged(colors,which);idle()
    }
    @Test fun volumeAutomaticWorksWithoutNotificationAccessAndSameContrastDoesNotUpdateOrQuery() {
        widget(true);runtime.settingsChanged();idle();settleReads(1)
        assertEquals(1,WallpaperProbe.adds);EnergyCounters.clear()
        event(true);assertEquals(1,EnergyCounters.updates);assertEquals(0,EnergyCounters.sessions)
        repeat(12){event(true)};assertEquals(1,EnergyCounters.updates);assertEquals(1,WallpaperProbe.reads.get())
    }
    @Test fun wallpaperChangeUsesObserverCacheWithoutRescanningMediaSessions() {
        val id=widget();shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationListenerAccessGranted(AndroidSessionGateway.listenerComponent(app),true)
        val session=MediaSession(app,"wallpaper-cache");shadowOf(session.controller).setPackageName("test.player")
        shadowOf(session.controller).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED,0,0f).build())
        shadowOf(app.getSystemService(MediaSessionManager::class.java)).addController(session.controller)
        try {WidgetPlaybackObserver(app).use{observer ->
            observer.start();runtime.settingsChanged();idle();settleReads(1);EnergyCounters.clear()
            event(true);assertEquals(0,EnergyCounters.sessions);assertEquals(1,EnergyCounters.updates)
            val button=shadowOf(AppWidgetManager.getInstance(app)).getViewFor(id).findViewById<android.widget.ImageView>(R.id.widget_toggle)
            assertEquals(app.getString(R.string.widget_play_action),button.contentDescription)
            observer.close();event(false);assertEquals(0,EnergyCounters.sessions)
        }} finally {session.release()}
    }
    @Test fun revokedMediaGrantAndObserverRestartRecoverWithoutAColorTriggeredSessionScan() {
        val id=widget();val notifications=shadowOf(app.getSystemService(NotificationManager::class.java))
        notifications.setNotificationListenerAccessGranted(AndroidSessionGateway.listenerComponent(app),true)
        val session=MediaSession(app,"wallpaper-recovery");shadowOf(session.controller).setPackageName("test.player")
        shadowOf(session.controller).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING,0,1f).build())
        shadowOf(app.getSystemService(MediaSessionManager::class.java)).addController(session.controller)
        val observer=WidgetPlaybackObserver(app)
        try {
            observer.start();runtime.settingsChanged();idle();settleReads(1)
            notifications.setNotificationListenerAccessGranted(AndroidSessionGateway.listenerComponent(app),false)
            EnergyCounters.clear();event(true);assertEquals(0,EnergyCounters.sessions)
            val manager=AppWidgetManager.getInstance(app)
            fun description()=shadowOf(manager).getViewFor(id).findViewById<android.widget.ImageView>(R.id.widget_toggle).contentDescription
            assertEquals(app.getString(R.string.widget_state_unknown),description())
            observer.close();runtime.coordinator.close()
            WallpaperProbe.colors=WallpaperColors(Color.valueOf(Color.BLACK),null,null)
            runtime.settingsChanged();idle();settleReads(2);assertEquals(app.getString(R.string.widget_state_unknown),description())
            notifications.setNotificationListenerAccessGranted(AndroidSessionGateway.listenerComponent(app),true)
            WidgetPlaybackObserver(app).use { recovered ->
                recovered.start();idle();assertEquals(app.getString(R.string.widget_pause_action),description())
                EnergyCounters.clear();event(true);assertEquals(0,EnergyCounters.sessions)
            }
        } finally {observer.close();session.release()}
    }
    @Test fun combinedSourceFlagsUpdateBothDestinationsAndQueryEachOnlyOnce() {
        widget(true);widget(true,auto.copy(wallpaperTarget=WidgetWallpaperTarget.LOCK))
        runtime.settingsChanged();idle();settleReads(2);EnergyCounters.clear()
        event(true,WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
        assertEquals(2,EnergyCounters.updates);assertEquals(0,EnergyCounters.sessions);assertEquals(2,WallpaperProbe.reads.get())
    }
    @Test fun screenOffHasNoDeliveriesAndManualModeReleasesListener() {
        val id=widget(true);runtime.settingsChanged();idle();settleReads(1);EnergyCounters.clear()
        shadowOf(app.getSystemService(PowerManager::class.java)).setIsInteractive(false)
        app.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF));idle();event(true);assertEquals(0,EnergyCounters.updates)
        shadowOf(app.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        WallpaperProbe.colors=WallpaperColors(Color.valueOf(Color.WHITE),null,null)
        app.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON));idle();settleReads(2);assertTrue(EnergyCounters.updates>0)
        WidgetStyleStore.save(app,WidgetAppearance(),id);runtime.settingsChanged();idle();assertNull(WallpaperProbe.callback);assertEquals(1,WallpaperProbe.removes)
    }
    @Test fun newFieldsPersistAndOldBackupsStillImportAsManual() {
        val style=auto.copy(autoBorderColors=true,darkIcons=true,wallpaperTarget=WidgetWallpaperTarget.LOCK)
        WidgetStyleStore.save(app,style);assertEquals(style,WidgetStyleStore.load(app))
        assertEquals(style,WidgetAppearanceState.read(WidgetAppearanceState.write(style)))
        val value=SettingsBackup(style,15,listOf(BackupProfile("Lock",false,style,false,WidgetLocation.LOCK_SCREEN)))
        val text=SettingsBackupCodec.encode(value);assertEquals(value,SettingsBackupCodec.decode(text));assertEquals(4,JSONObject(text).getInt("version"))
        for(version in 1..2) {
            val old=JSONObject(text).put("version",version)
            for(obj in listOf(old.getJSONObject("defaults"),old.getJSONArray("profiles").getJSONObject(0).getJSONObject("appearance"))) {
                obj.remove("color_mode");obj.remove("wallpaper_target");obj.remove("auto_border_colors")
            }
            val restored=SettingsBackupCodec.decode(old.toString());assertEquals(WidgetColorMode.FIXED,restored.defaults.colorMode)
            assertEquals(WidgetColorMode.FIXED,restored.profiles.single().appearance.colorMode);assertTrue(restored.defaults.darkIcons)
        }
        for((key,bad)in listOf("color_mode" to "UNKNOWN","wallpaper_target" to "UNKNOWN","auto_border_colors" to "true")) {
            val invalid=JSONObject(text);invalid.getJSONObject("defaults").put(key,bad)
            try{SettingsBackupCodec.decode(invalid.toString());fail("Bad wallpaper field")}catch(_:Exception){}
        }
    }
    @Test fun editorCancelDoesNotSaveAutomaticDraftAndPauseReleasesPreviewListener() {
        Robolectric.buildActivity(WidgetDefaultsActivity::class.java).setup().use{controller ->
            val activity=controller.get();activity.findViewById<Spinner>(R.id.widget_wallpaper_source).setSelection(1)
            activity.findViewById<Spinner>(R.id.widget_color_mode).setSelection(1);idle();settleReads(1)
            assertEquals(WidgetColorMode.FIXED,WidgetStyleStore.load(app).colorMode)
            controller.pause();idle();assertNull(WallpaperProbe.callback)
            assertEquals(WidgetColorMode.FIXED,WidgetStyleStore.load(app).colorMode)
        }
    }
}
