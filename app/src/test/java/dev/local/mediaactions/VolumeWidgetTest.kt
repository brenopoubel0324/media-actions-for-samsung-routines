package dev.local.mediaactions

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.media.AudioManager
import android.view.View
import android.widget.EditText
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowAudioManager

@Implements(AudioManager::class)
class VolumeAudioShadow: ShadowAudioManager() {
    data class Call(val stream:Int,val direction:Int,val flags:Int)
    val calls=mutableListOf<Call>()
    var fixed=false
    var deny=false
    @Implementation override fun adjustStreamVolume(stream:Int,direction:Int,flags:Int) {
        calls+=Call(stream,direction,flags)
        if(deny)throw SecurityException("Injected system policy")
        // Robolectric 4.17 adjusts all streams via its single-argument helper.
        // Model the OS stream boundary explicitly; assertions also verify every dispatched call.
        super.setStreamVolume(stream,(getStreamVolume(stream)+direction).coerceIn(getStreamMinVolume(stream),getStreamMaxVolume(stream)),flags)
    }
    @Implementation fun isVolumeFixed()=fixed
}
@Implements(AppWidgetManager::class)
class VolumeHostShadow: org.robolectric.shadows.ShadowAppWidgetManager() {
    var failId: Int? = null
    @Implementation override fun updateAppWidget(id:Int, views:android.widget.RemoteViews) {
        if(id==failId)throw IllegalStateException("Injected host failure")
        super.updateAppWidget(id,views)
    }
}
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33,36],shadows=[VolumeAudioShadow::class,VolumeHostShadow::class])
class VolumeWidgetTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private val manager get()=AppWidgetManager.getInstance(app)
    private val audio get()=app.getSystemService(AudioManager::class.java)
    private val audioShadow get()=org.robolectric.shadow.api.Shadow.extract<VolumeAudioShadow>(audio)
    private fun widget(volume:Boolean=true):Int {
        val type=if(volume)VolumeWidgetProvider::class.java else MediaWidgetProvider::class.java
        val id=shadowOf(manager).createWidget(type,if(volume)R.layout.widget_volume_controls else R.layout.widget_media_controls)
        shadowOf(manager).addBoundWidget(id,AppWidgetProviderInfo().apply {provider=ComponentName(app,type)})
        return id
    }
    private fun command(id:Int,up:Boolean)=Intent(shadowOf(VolumeWidgetProvider.controlIntent(app,id,up)).savedIntent)
    @Test fun eachClickAdjustsOnlyLocalMusicOneStepWithoutNotificationAccessOrActivity() {
        val id=widget();val receiver=VolumeWidgetProvider()
        audio.setStreamVolume(AudioManager.STREAM_MUSIC,5,0)
        val alarm=audio.getStreamVolume(AudioManager.STREAM_ALARM)
        receiver.onReceive(app,command(id,true));assertEquals(6,audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        receiver.onReceive(app,command(id,false));assertEquals(5,audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        assertEquals(listOf(VolumeAudioShadow.Call(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_RAISE,0),VolumeAudioShadow.Call(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_LOWER,0)),audioShadow.calls)
        assertEquals(alarm,audio.getStreamVolume(AudioManager.STREAM_ALARM));assertNull(shadowOf(app).nextStartedActivity)
        assertTrue(shadowOf(app).broadcastIntents.none {it.action==Intent.ACTION_MEDIA_BUTTON})
    }
    @Test fun wrongProviderRemovedIdsAndMismatchedUrisCannotChangeVolume() {
        val receiver=VolumeWidgetProvider();val media=widget(false);val volume=widget()
        receiver.onReceive(app,command(media,true))
        receiver.onReceive(app,command(volume,true).setData(android.net.Uri.parse("mediaactions://volume/$volume/invalid")))
        receiver.onReceive(app,command(volume,true).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,media))
        org.robolectric.util.ReflectionHelpers.getField<MutableMap<Int,*>>(shadowOf(manager),"widgetInfos").remove(volume)
        receiver.onReceive(app,command(volume,true));assertTrue(audioShadow.calls.isEmpty())
        assertFalse(MediaWidgetProvider.isOwnWidget(app,media+100))
    }
    @Test fun boundsFixedVolumeAndPolicyFailureHaveNoRetries() {
        val id=widget();val receiver=VolumeWidgetProvider()
        audio.setStreamVolume(AudioManager.STREAM_MUSIC,audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),0)
        receiver.onReceive(app,command(id,true));assertEquals(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        audio.setStreamVolume(AudioManager.STREAM_MUSIC,0,0);receiver.onReceive(app,command(id,false));assertEquals(0,audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        audioShadow.calls.clear();audioShadow.fixed=true;receiver.onReceive(app,command(id,true));assertTrue(audioShadow.calls.isEmpty())
        audioShadow.fixed=false;audioShadow.deny=true;receiver.onReceive(app,command(id,true));assertEquals(1,audioShadow.calls.size)
    }
    @Test fun volumeEditorSavesOnlyItsInstanceAndKeepsLocationAndDraftAcrossRecreation() {
        val id=widget();val other=widget(false)
        WidgetLocationStore.save(app,id,WidgetLocation.HOME_SCREEN)
        val intent=Intent(app,WidgetConfigureActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id)
        Robolectric.buildActivity(WidgetConfigureActivity::class.java,intent).setup().use {c ->
            assertNull(c.get().findViewById<View>(R.id.widget_progress_ring))
            assertNull(c.get().findViewById<View>(R.id.widget_preview_playing))
            c.get().findViewById<EditText>(R.id.widget_scale_value).setText("55")
            c.recreate();assertEquals("55",c.get().findViewById<EditText>(R.id.widget_scale_value).text.toString())
            c.get().findViewById<View>(R.id.widget_configuration_save).performClick();assertTrue(c.get().isFinishing)
        }
        assertEquals(55,WidgetStyleStore.load(app,id).iconScalePercent);assertEquals(100,WidgetStyleStore.load(app,other).iconScalePercent)
        assertEquals(WidgetLocation.HOME_SCREEN,WidgetLocationStore.load(app,id));assertTrue(audioShadow.calls.isEmpty())
    }
    @Test fun defaultRefreshAndInheritanceReachBothKindsWhileMediaRefreshLeavesVolumeAlone() {
        val id=widget();val media=widget(false)
        WidgetStyleStore.save(app,WidgetAppearance(iconScalePercent=50),id)
        val editor=WidgetAppearanceSection(app,id);val view=editor.create()
        view.findViewById<View>(R.id.widget_use_default_visual).performClick();assertTrue(editor.applyChanges());assertFalse(WidgetStyleStore.hasOverride(app,id))
        WidgetStyleStore.save(app,WidgetAppearance(iconScalePercent=70))
        WidgetRegistry.refreshAll(app)
        val target=shadowOf(manager).getViewFor(id).findViewById<View>(R.id.widget_next)
        target.contentDescription="untouched"
        MediaWidgetProvider.refreshAll(app,WidgetPlaybackState.PLAYING,progressOnly=true)
        assertEquals("untouched",target.contentDescription)
        assertEquals(setOf(id,media),WidgetRegistry.ids(app).toSet());assertTrue(audioShadow.calls.isEmpty())
    }
    @Test fun twoButtonGeometryAndProviderMetadataAreIndependentOfThreeMediaButtons() {
        assertEquals(96,WidgetGeometry.calculate(96f,48f,WidgetAppearance(spacingDp=144),2).widthDp)
        assertEquals(144,WidgetGeometry.calculate(144f,48f,WidgetAppearance(spacingDp=144)).widthDp)
        val receiver=app.packageManager.getReceiverInfo(ComponentName(app,VolumeWidgetProvider::class.java),android.content.pm.PackageManager.GET_META_DATA)
        assertFalse(receiver.exported)
        receiver.loadXmlMetaData(app.packageManager,AppWidgetManager.META_DATA_APPWIDGET_PROVIDER)!!.use {xml ->
            while(xml.next()!=org.xmlpull.v1.XmlPullParser.START_TAG){}
            val ns="http://schemas.android.com/apk/res/android"
            assertEquals("0",xml.getAttributeValue(ns,"updatePeriodMillis"))
            assertEquals(WidgetConfigureActivity::class.java.name,xml.getAttributeValue(ns,"configure"))
        }
    }
    @Test fun defaultsEditorReportsVolumeHostFailureAndAllowsRetry() {
        val id=widget()
        val editor=WidgetAppearanceSection(app);val view=editor.create()
        view.findViewById<EditText>(R.id.widget_scale_value).setText("65")
        val host=org.robolectric.shadow.api.Shadow.extract<VolumeHostShadow>(manager)
        host.failId=id;assertFalse(editor.applyChanges());assertEquals(R.string.widget_save_refresh_failed,editor.lastApplyFailure)
        host.failId=null;assertTrue(editor.applyChanges());assertEquals(65,WidgetStyleStore.load(app,id).iconScalePercent)
    }
    @Test fun editorRejectsAProviderKindChangedWhileItWasOpen() {
        val id=widget();val editor=WidgetAppearanceSection(app,id);val view=editor.create()
        view.findViewById<EditText>(R.id.widget_scale_value).setText("65")
        shadowOf(manager).addBoundWidget(id,AppWidgetProviderInfo().apply {provider=ComponentName(app,MediaWidgetProvider::class.java)})
        assertFalse(editor.applyChanges());assertFalse(WidgetStyleStore.hasOverride(app,id))
    }

}
