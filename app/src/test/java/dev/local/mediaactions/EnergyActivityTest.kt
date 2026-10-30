package dev.local.mediaactions

import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Looper
import android.os.PowerManager
import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import android.widget.RemoteViews
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowAppWidgetManager
import org.robolectric.shadows.ShadowMediaController
import org.robolectric.shadows.ShadowMediaSessionManager
import java.io.File
import java.time.Duration

object EnergyCounters {
    var sessions = 0; var progress = 0; var updates = 0; var bitmaps = 0; var ticks = 0; var events = 0
    var state: PlaybackState? = null
    var metadata: MediaMetadata? = null
    val tracked = mutableSetOf<Int>()
    fun clear() { sessions=0; progress=0; updates=0; bitmaps=0; ticks=0; events=0 }
}
@Implements(MediaSessionManager::class)
class CountingSessionsShadow : ShadowMediaSessionManager() {
    @Implementation public override fun getActiveSessions(component: ComponentName?): List<MediaController> {
        EnergyCounters.sessions++; return super.getActiveSessions(component)
    }
}
@Implements(MediaController::class)
class CountingControllerShadow : ShadowMediaController() {
    @Implementation public override fun getPlaybackState(): PlaybackState? = EnergyCounters.state ?: super.getPlaybackState()
    @Implementation public override fun getMetadata(): MediaMetadata? { EnergyCounters.progress++; return EnergyCounters.metadata }
}
@Implements(AppWidgetManager::class)
class CountingWidgetsShadow : ShadowAppWidgetManager() {
    @Implementation public override fun updateAppWidget(id: Int, views: RemoteViews) {
        super.updateAppWidget(id, views)
        if (id in EnergyCounters.tracked) {
            EnergyCounters.updates++
            if (getViewFor(id).findViewById<ImageView>(R.id.widget_toggle).drawable is BitmapDrawable) EnergyCounters.bitmaps++
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33,36], shadows=[CountingSessionsShadow::class,CountingControllerShadow::class,CountingWidgetsShadow::class])
class EnergyActivityTest {
    private val app get() = RuntimeEnvironment.getApplication()
    @Test fun exportControlledActivityCounts() {
        val widgets=AppWidgetManager.getInstance(app)
        EnergyCounters.tracked.clear()
        EnergyCounters.state=PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING,1000,1f).build()
        EnergyCounters.metadata=MediaMetadata.Builder().putLong(MediaMetadata.METADATA_KEY_DURATION,300000).build()
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationListenerAccessGranted(AndroidSessionGateway.listenerComponent(app),true)
        shadowOf(app.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        val session=MediaSession(app,"energy-counts")
        shadowOf(session.controller).setPackageName("test.player")
        shadowOf(app.getSystemService(MediaSessionManager::class.java)).addController(session.controller)
        val rows=mutableListOf<String>()
        try {
            for (count in listOf(1,3,6)) {
                for (progress in listOf(false,true)) {
                  for (seconds in (if(progress) WidgetProgressSettings.intervals else listOf(5))) {
                    WidgetProgressSettings.save(app,seconds)
                    EnergyCounters.tracked.forEach { WidgetStyleStore.save(app,WidgetAppearance(progressRing=false),it) }
                    val ids=(1..count).map {
                        val id=shadowOf(widgets).createWidget(MediaWidgetProvider::class.java,R.layout.widget_media_controls)
                        shadowOf(widgets).addBoundWidget(id,AppWidgetProviderInfo().apply { provider=ComponentName(app,MediaWidgetProvider::class.java) })
                        WidgetStyleStore.save(app,WidgetAppearance(progressRing=progress),id);EnergyCounters.tracked+=id;id
                    }
                    WidgetPlaybackObserver(app).use { observer ->
                        observer.observeWork={ when(it) {
                            WidgetPlaybackObserver.Work.PERIODIC_TICK -> EnergyCounters.ticks++
                            WidgetPlaybackObserver.Work.EVENT_REFRESH -> EnergyCounters.events++
                        } }
                        observer.start();shadowOf(Looper.getMainLooper()).idle();EnergyCounters.clear()
                        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(60))
                        assertEquals(if(progress)60/seconds else 0,EnergyCounters.ticks)
                        assertEquals(0,EnergyCounters.events)
                        assertEquals(if(progress)(60/seconds)*count else 0,EnergyCounters.updates)
                        rows+="{\"widgets\":$count,\"enabled\":$progress,\"interval_seconds\":$seconds,\"periodic_ticks\":${EnergyCounters.ticks},\"event_refreshes\":${EnergyCounters.events},\"session_reads\":${EnergyCounters.sessions},\"progress_reads\":${EnergyCounters.progress},\"widget_updates\":${EnergyCounters.updates},\"bitmap_builds\":${EnergyCounters.bitmaps}}"
                    }
                    ids.forEach { WidgetStyleStore.save(app,WidgetAppearance(progressRing=false),it) }
                  }
                }
            }
        } finally { session.release(); EnergyCounters.state=null;EnergyCounters.metadata=null }
        val sdk=android.os.Build.VERSION.SDK_INT
        val file=File("build/outputs/energy/activity-sdk-$sdk.json");file.parentFile!!.mkdirs()
        file.writeText("{\"sdk\":$sdk,\"duration_seconds\":60,\"kind\":\"simulated_activity_not_battery\",\"rows\":[${rows.joinToString(",")}]}\n")
    }
}
