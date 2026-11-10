package dev.local.mediaactions

import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33,36],shadows=[CountingSessionsShadow::class,CountingControllerShadow::class,CountingWidgetsShadow::class])
class BackgroundWorkComparisonTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private fun export(scenario:String,body:()->Unit) {
        EnergyCounters.tracked.clear();EnergyCounters.clear()
        EnergyCounters.state=PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED,1000,0f,1L).build()
        EnergyCounters.metadata=MediaMetadata.Builder().putLong(MediaMetadata.METADATA_KEY_DURATION,300000).build()
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationListenerAccessGranted(AndroidSessionGateway.listenerComponent(app),true)
        val session=MediaSession(app,"comparison")
        shadowOf(session.controller).setPackageName("test.player")
        shadowOf(app.getSystemService(MediaSessionManager::class.java)).addController(session.controller)
        try {
            body()
            WidgetPlaybackObserver(app).use {observer ->
                observer.start();shadowOf(Looper.getMainLooper()).idle()
                val startup=EnergyCounters.sessions
                EnergyCounters.clear()
                repeat(10){shadowOf(session.controller).executeOnPlaybackStateChanged(EnergyCounters.state);shadowOf(Looper.getMainLooper()).idle()}
                assertEquals(0,EnergyCounters.sessions)
                assertEquals(0,EnergyCounters.progress)
                assertEquals(0,EnergyCounters.updates)
                val f=File("build/outputs/energy/$scenario-${android.os.Build.VERSION.SDK_INT}.json");f.parentFile!!.mkdirs()
                f.writeText("{\"scenario\":\"$scenario\",\"duplicate_callbacks\":10,\"startup_session_reads\":$startup,\"session_reads\":${EnergyCounters.sessions},\"progress_reads\":${EnergyCounters.progress},\"widget_updates\":${EnergyCounters.updates}}\n")
            }
        } finally {session.release();EnergyCounters.state=null;EnergyCounters.metadata=null}
    }
    @Test fun repeatedPausedCallbacks()=export("paused-duplicates") {
        val manager=AppWidgetManager.getInstance(app)
        repeat(6){
            val id=shadowOf(manager).createWidget(MediaWidgetProvider::class.java,R.layout.widget_media_controls)
            shadowOf(manager).addBoundWidget(id,AppWidgetProviderInfo().apply {provider=ComponentName(app,MediaWidgetProvider::class.java)})
            WidgetStyleStore.save(app,WidgetAppearance(progressRing=true),id);EnergyCounters.tracked+=id
        }
    }
    @Test fun noWidgets()=export("no-widgets") {}
}
