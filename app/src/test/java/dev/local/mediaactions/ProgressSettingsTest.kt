package dev.local.mediaactions

import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Looper
import android.os.PowerManager
import android.view.View
import android.widget.RadioButton
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33,36])
class ProgressSettingsTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private fun idle()=shadowOf(Looper.getMainLooper()).idle()
    private fun advance(seconds:Long)=shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds))
    private fun player():MediaSession {
        val widgets=AppWidgetManager.getInstance(app)
        val id=shadowOf(widgets).createWidget(MediaWidgetProvider::class.java,R.layout.widget_media_controls)
        shadowOf(widgets).addBoundWidget(id,AppWidgetProviderInfo().apply { provider=ComponentName(app,MediaWidgetProvider::class.java) })
        WidgetStyleStore.save(app,WidgetAppearance(progressRing=true),id)
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationListenerAccessGranted(AndroidSessionGateway.listenerComponent(app),true)
        shadowOf(app.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        return MediaSession(app,"timing").also {
            shadowOf(it.controller).setPackageName("test.player")
            shadowOf(it.controller).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING,1000,1f).build())
            shadowOf(app.getSystemService(MediaSessionManager::class.java)).addController(it.controller)
        }
    }
    @Test fun absentWrongTypeAndUnsupportedValueUseFiveSecondsWithoutChangingAppearance() {
        val style=WidgetAppearance(progressRing=true,widgetPaddingVerticalDp=-12)
        WidgetStyleStore.save(app,style)
        val prefs=app.getSharedPreferences("widget_progress_settings",0)
        assertEquals(5,WidgetProgressSettings.load(app))
        prefs.edit().putString("interval_seconds","15").apply();assertEquals(5,WidgetProgressSettings.load(app))
        prefs.edit().putInt("interval_seconds",10).apply();assertEquals(5,WidgetProgressSettings.load(app))
        WidgetProgressSettings.save(app,30);assertEquals(30,WidgetProgressSettings.load(app))
        assertEquals(style,WidgetStyleStore.load(app))
        try { WidgetProgressSettings.save(app,1);fail("Unsupported interval") } catch(_:IllegalArgumentException) { }
        assertEquals(30,WidgetProgressSettings.load(app))
    }
    @Test fun privateEditorKeepsDraftAcrossRecreationAndOnlySaveChangesTheGlobalSetting() {
        assertFalse(app.packageManager.getActivityInfo(ComponentName(app,ProgressSettingsActivity::class.java),0).exported)
        Robolectric.buildActivity(ProgressSettingsActivity::class.java).setup().use { c ->
            c.get().findViewById<View>(R.id.progress_interval_15).performClick();c.recreate()
            assertTrue(c.get().findViewById<RadioButton>(R.id.progress_interval_15).isChecked)
            assertEquals(5,WidgetProgressSettings.load(app))
            c.get().findViewById<View>(R.id.progress_settings_save).performClick()
        }
        assertEquals(15,WidgetProgressSettings.load(app))
        Robolectric.buildActivity(ProgressSettingsActivity::class.java).setup().use { c ->
            assertTrue(c.get().findViewById<RadioButton>(R.id.progress_interval_15).isChecked)
            c.get().findViewById<View>(R.id.progress_interval_30).performClick()
            c.get().findViewById<View>(R.id.progress_settings_cancel).performClick()
        }
        assertEquals(15,WidgetProgressSettings.load(app))
    }
    @Test fun intervalsProduceTwelveFourAndTwoTicksAndCloseCancelsFutureWork() {
        val session=player()
        try {
            for(seconds in WidgetProgressSettings.intervals) {
                WidgetProgressSettings.save(app,seconds);var ticks=0
                val observer=WidgetPlaybackObserver(app,progressReader={WidgetTrackProgress(.4f)}){}
                observer.observeWork={if(it==WidgetPlaybackObserver.Work.PERIODIC_TICK)ticks++}
                observer.start();idle();advance(60)
                assertEquals(60/seconds,ticks)
                observer.close();advance(60);assertEquals(60/seconds,ticks)
            }
        } finally { session.release() }
    }
    @Test fun changingDuringPlaybackReplacesOldTimerAndEventsRemainImmediate() {
        val session=player();var ticks=0;var events=0
        try { WidgetPlaybackObserver(app,progressReader={WidgetTrackProgress(.4f)}){}.use { observer ->
            observer.observeWork={if(it==WidgetPlaybackObserver.Work.PERIODIC_TICK)ticks++ else events++}
            observer.start();idle();advance(2)
            WidgetProgressSettings.save(app,15);idle();advance(5);assertEquals(0,ticks)
            advance(10);assertEquals(1,ticks)
            WidgetProgressSettings.save(app,30);idle();val before=events
            shadowOf(session.controller).executeOnPlaybackStateChanged(PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED,2000,0f).build())
            idle();assertEquals(before+1,events);advance(60);assertEquals(1,ticks)
        } } finally { session.release() }
    }
}
