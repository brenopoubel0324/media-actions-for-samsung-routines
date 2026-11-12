package dev.local.mediaactions

import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33,36], shadows=[CountingSessionsShadow::class,CountingControllerShadow::class,CountingWidgetsShadow::class])
class WidgetOptionsUpdatesTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private val manager get()=AppWidgetManager.getInstance(app)
    private fun idle()=shadowOf(Looper.getMainLooper()).idle()
    private fun options(w:Float=240f,h:Float=64f,category:Int=AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD)=Bundle().apply {
        putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,arrayListOf(SizeF(w,h)))
        putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY,category)
    }
    private fun widget(volume:Boolean):Pair<Int,AppWidgetProvider> {
        val type=if(volume)VolumeWidgetProvider::class.java else MediaWidgetProvider::class.java
        val id=shadowOf(manager).createWidget(type,if(volume)R.layout.widget_volume_controls else R.layout.widget_media_controls)
        shadowOf(manager).addBoundWidget(id,AppWidgetProviderInfo().apply{provider=ComponentName(app,type)})
        EnergyCounters.tracked+=id
        WidgetOptionsUpdates.forget(app,intArrayOf(id))
        return id to if(volume)VolumeWidgetProvider() else MediaWidgetProvider()
    }
    @Before fun reset(){EnergyCounters.clear();EnergyCounters.tracked.clear();EnergyCounters.state=null;EnergyCounters.metadata=null;EnergyCounters.failUpdates=0;EnergyCounters.failSessionReads=0}
    @After fun cleanup(){EnergyCounters.failUpdates=0;EnergyCounters.failSessionReads=0;EnergyCounters.state=null;EnergyCounters.metadata=null;EnergyCounters.tracked.clear()}

    @Test fun tenEquivalentCallbacksCauseZeroExtraUpdatesForBothProviders() {
        for(volume in listOf(false,true)) {
            val(id,provider)=widget(volume)
            provider.onAppWidgetOptionsChanged(app,manager,id,options());idle();EnergyCounters.clear()
            repeat(10){provider.onAppWidgetOptionsChanged(app,manager,id,options().apply{putString("irrelevant_host_extra",it.toString())})}
            idle();assertEquals(0,EnergyCounters.updates);assertEquals(0,EnergyCounters.sessions)
        }
    }
    @Test fun duplicateMediaCallbacksDoNotQuerySessionsOrResyncObserverWithProgressDisabled() {
        val(id,provider)=widget(false)
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationListenerAccessGranted(AndroidSessionGateway.listenerComponent(app),true)
        val session=MediaSession(app,"options")
        try {
            EnergyCounters.state=PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED,0,0f).build()
            shadowOf(session.controller).setPackageName("test.player")
            shadowOf(app.getSystemService(MediaSessionManager::class.java)).addController(session.controller)
            WidgetPlaybackObserver(app).use {observer ->
                observer.start();idle()
                provider.onAppWidgetOptionsChanged(app,manager,id,options());idle();EnergyCounters.clear()
                repeat(10){provider.onAppWidgetOptionsChanged(app,manager,id,options())};idle()
                assertEquals(0,EnergyCounters.updates);assertEquals(0,EnergyCounters.sessions)
            }
        } finally {session.release()}
    }
    @Test fun realResizeAndHostCategoryChangeAreDeliveredIncludingVolumeOrientation() {
        for(volume in listOf(false,true)) {
            val(id,provider)=widget(volume)
            provider.onAppWidgetOptionsChanged(app,manager,id,options());EnergyCounters.clear()
            provider.onAppWidgetOptionsChanged(app,manager,id,options(64f,240f))
            provider.onAppWidgetOptionsChanged(app,manager,id,options(64f,240f,AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN))
            assertEquals(2,EnergyCounters.updates)
            if(volume) {
                val root=shadowOf(manager).getViewFor(id)
                assertEquals(android.widget.LinearLayout.VERTICAL,root.findViewById<android.widget.LinearLayout>(R.id.widget_controls_row).orientation)
            }
        }
    }
    @Test fun styleInheritanceAndLocationChangesInvalidateTheOptionsKey() {
        for(volume in listOf(false,true)) {
            WidgetStyleStore.save(app,WidgetAppearance())
            val(id,provider)=widget(volume)
            provider.onAppWidgetOptionsChanged(app,manager,id,options());EnergyCounters.clear()
            WidgetStyleStore.save(app,WidgetAppearance(iconScalePercent=55))
            provider.onAppWidgetOptionsChanged(app,manager,id,options())
            WidgetStyleStore.save(app,WidgetStyleStore.load(app),id) // Same visual, changed inheritance.
            provider.onAppWidgetOptionsChanged(app,manager,id,options())
            WidgetLocationStore.save(app,id,WidgetLocation.HOME_SCREEN)
            provider.onAppWidgetOptionsChanged(app,manager,id,options())
            assertEquals(3,EnergyCounters.updates)
        }
    }
    @Test fun failureDoesNotCacheSuccessAndTheNextIdenticalRequestRetries() {
        for(volume in listOf(false,true)) {
            val(id,provider)=widget(volume)
            EnergyCounters.failUpdates=1
            try {provider.onAppWidgetOptionsChanged(app,manager,id,options());fail("Expected host failure")}catch(_:IllegalStateException){}
            EnergyCounters.clear();provider.onAppWidgetOptionsChanged(app,manager,id,options());assertEquals(1,EnergyCounters.updates)
            provider.onAppWidgetOptionsChanged(app,manager,id,options());assertEquals(1,EnergyCounters.updates)
        }
    }
    @Test fun explicitRefreshOnUpdateAndDeletionStillAllowAnIdenticalOptionsRequest() {
        for(volume in listOf(false,true)) {
            val(id,provider)=widget(volume)
            provider.onAppWidgetOptionsChanged(app,manager,id,options());idle()
            for(force in listOf<()->Unit>(
                {if(volume)VolumeWidgetProvider.refresh(app,id) else MediaWidgetProvider.refreshWidget(app,id)},
                {provider.onUpdate(app,manager,intArrayOf(id))},
                {provider.onDeleted(app,intArrayOf(id))},
            )) {
                force();idle();EnergyCounters.clear()
                provider.onAppWidgetOptionsChanged(app,manager,id,options());idle()
                assertEquals(1,EnergyCounters.updates)
            }
        }
    }
    @Test fun emptySizesFallbackIsDeduplicatedButLaterDimensionsAreDelivered() {
        val(id,provider)=widget(true)
        provider.onAppWidgetOptionsChanged(app,manager,id,Bundle());EnergyCounters.clear()
        provider.onAppWidgetOptionsChanged(app,manager,id,Bundle());assertEquals(0,EnergyCounters.updates)
        provider.onAppWidgetOptionsChanged(app,manager,id,options());assertEquals(1,EnergyCounters.updates)
    }
    @Test fun failedObserverNotificationDoesNotSuppressTheNextIdenticalOptionsRequest() {
        val(id,provider)=widget(false)
        val context=object:android.content.ContextWrapper(app) {
            var fail=true
            override fun sendBroadcast(intent:android.content.Intent) {
                if(fail) {fail=false;throw IllegalStateException("Test broadcast failure")}
                super.sendBroadcast(intent)
            }
        }
        try {provider.onAppWidgetOptionsChanged(context,manager,id,options());fail("Expected notification failure")}catch(_:IllegalStateException){}
        EnergyCounters.clear()
        provider.onAppWidgetOptionsChanged(context,manager,id,options());idle()
        assertEquals(1,EnergyCounters.updates)
        EnergyCounters.clear()
        provider.onAppWidgetOptionsChanged(context,manager,id,options());idle()
        assertEquals(0,EnergyCounters.updates)
    }
    @Test fun newerReentrantSizeWinsAndLateCompletionDoesNotRestoreOldKey() {
        val(id,_)=widget(true);var count=0
        WidgetOptionsUpdates.deliver(app,id,true,options()) {
            count++
            assertTrue(WidgetOptionsUpdates.deliver(app,id,true,options(64f,240f)){count++})
        }
        assertFalse(WidgetOptionsUpdates.deliver(app,id,true,options(64f,240f)){count++})
        assertEquals(2,count)
    }
    @Test fun pendingDeliverySuppressesReentryAndForgottenCacheForcesFirstUpdate() {
        val(id,_)=widget(true);var count=0
        assertTrue(WidgetOptionsUpdates.deliver(app,id,true,options()) {
            count++
            assertFalse(WidgetOptionsUpdates.deliver(app,id,true,options()){count++})
        })
        assertEquals(1,count)
        WidgetOptionsUpdates.forget(app,intArrayOf(id))
        assertTrue(WidgetOptionsUpdates.deliver(app,id,true,options()){count++});assertEquals(2,count)
    }
}
