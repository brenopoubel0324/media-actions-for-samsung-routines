package dev.local.mediaactions

import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.widget.ImageView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33,36], shadows=[CountingSessionsShadow::class,CountingControllerShadow::class,CountingWidgetsShadow::class])
class WidgetBatchSnapshotTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private val widgets get()=AppWidgetManager.getInstance(app)
    private fun widget(progress: Boolean): Int {
        val id=shadowOf(widgets).createWidget(MediaWidgetProvider::class.java,R.layout.widget_media_controls)
        shadowOf(widgets).addBoundWidget(id,AppWidgetProviderInfo().apply { provider=ComponentName(app,MediaWidgetProvider::class.java) })
        WidgetStyleStore.save(app,WidgetAppearance(progressRing=progress,buttonBorder=true),id)
        return id
    }
    private fun withPlayer(block:()->Unit) {
        EnergyCounters.state=PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING,1000,1f).build()
        EnergyCounters.metadata=MediaMetadata.Builder().putLong(MediaMetadata.METADATA_KEY_DURATION,10000).build()
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationListenerAccessGranted(AndroidSessionGateway.listenerComponent(app),true)
        val session=MediaSession(app,"batch")
        shadowOf(session.controller).setPackageName("test.player")
        shadowOf(app.getSystemService(MediaSessionManager::class.java)).addController(session.controller)
        try { block() } finally { session.release();EnergyCounters.state=null;EnergyCounters.metadata=null }
    }
    @Test fun mixedBatchesReadOnceAndUnavailableProgressNeverTriggersFallback() = withPlayer {
        for (count in listOf(1,3,6)) {
            repeat(count) { widget(it%2==0) }
            EnergyCounters.clear();MediaWidgetProvider.refreshAll(app)
            assertEquals(1,EnergyCounters.sessions);assertEquals(1,EnergyCounters.progress)
            EnergyCounters.metadata=null
            EnergyCounters.clear();MediaWidgetProvider.refreshAll(app)
            assertEquals(1,EnergyCounters.sessions);assertEquals(1,EnergyCounters.progress)
            EnergyCounters.metadata=MediaMetadata.Builder().putLong(MediaMetadata.METADATA_KEY_DURATION,10000).build()
        }
    }
    @Test fun noEligibleIdsAndExplicitUnknownDoNoReadsAndUnknownClearsDecoration() = withPlayer {
        EnergyCounters.clear();MediaWidgetProvider.refreshAll(app)
        assertEquals(0,EnergyCounters.sessions)
        val id=widget(true)
        EnergyCounters.clear();MediaWidgetProvider.refreshAll(app,WidgetPlaybackState.UNKNOWN)
        assertEquals(0,EnergyCounters.sessions);assertEquals(0,EnergyCounters.progress)
        val center=shadowOf(widgets).getViewFor(id).findViewById<ImageView>(R.id.widget_toggle)
        assertEquals(app.getString(R.string.widget_state_unknown),center.contentDescription)
        assertNull(center.backgroundTintList)
        WidgetStyleStore.save(app,WidgetAppearance(progressRing=false),id)
        EnergyCounters.clear();MediaWidgetProvider.refreshAll(app,progressOnly=true)
        assertEquals(0,EnergyCounters.sessions);assertEquals(0,EnergyCounters.progress)
        MediaWidgetProvider.refreshAll(app)
        assertEquals(1,EnergyCounters.sessions);assertEquals(0,EnergyCounters.progress)
    }
    @Test fun updateResizeAndStandaloneRenderingHaveBoundedReadsAndRevocationWins() = withPlayer {
        val id=widget(true)
        for (render in listOf<()->Unit>(
            {MediaWidgetProvider().onUpdate(app,widgets,intArrayOf(id))},
            {MediaWidgetProvider().onAppWidgetOptionsChanged(app,widgets,id,Bundle())},
            {MediaWidgetProvider.createViews(app,id)})) {
            EnergyCounters.clear();render()
            assertEquals(1,EnergyCounters.sessions);assertEquals(1,EnergyCounters.progress)
        }
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationListenerAccessGranted(AndroidSessionGateway.listenerComponent(app),false)
        EnergyCounters.clear();MediaWidgetProvider.refreshAll(app)
        assertEquals(0,EnergyCounters.sessions);assertEquals(0,EnergyCounters.progress)
        assertEquals(app.getString(R.string.widget_state_unknown),shadowOf(widgets).getViewFor(id).findViewById<ImageView>(R.id.widget_toggle).contentDescription)
    }
    @Test fun positionComesFromCapturedSelectionEvenIfGatewayChangesDuringCollection() {
        fun player(state: SessionState)=object:PlayerSession {
            override val packageName="test.player"
            override val state=state
            override val supportedCommands=emptySet<TransportCommand>()
            override fun send(command:TransportCommand) { fail("Rendering must not send commands") }
        }
        val first=player(SessionState.PLAYING);val next=player(SessionState.PAUSED)
        var current=first;var selections=0;var positions=0
        val gateway=object:SessionGateway {
            override fun hasNotificationAccess()=true
            override fun snapshot():SessionSnapshot { selections++;return SessionSnapshot(listOf(current)).also {current=next} }
        }
        val snapshot=WidgetUpdateSnapshot.capture(gateway,true,positionReader={
            positions++;assertSame(first,it);SessionPosition(250,1000,0,1f,true)
        })
        assertEquals(WidgetPlaybackState.PLAYING,snapshot.state)
        assertEquals(.25f,snapshot.progress!!.fraction,0f)
        assertEquals(1,selections);assertEquals(1,positions)
        assertEquals(WidgetPlaybackState.PAUSED,WidgetUpdateSnapshot.capture(gateway,true,positionReader={fail("Paused has no progress read");null}).state)
        assertEquals(WidgetPlaybackState.UNKNOWN,WidgetUpdateSnapshot.capture(gateway,true,WidgetPlaybackState.UNKNOWN).state)
        assertEquals(2,selections)
    }
}
