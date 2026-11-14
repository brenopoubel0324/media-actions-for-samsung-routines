package dev.local.mediaactions

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Spinner
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33,36],qualifiers="mdpi")
class CompactWidgetFrameTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private val compact=WidgetAppearance(widgetBorder=true,frameMode=WidgetFrameMode.COMPACT,frameHeightDp=64,frameCorner=WidgetFrameCorner.ROUND)
    private fun view(style:WidgetAppearance=compact,w:Int=280,h:Int=160,volume:Boolean=false,size:Boolean=true):View {
        val root=WidgetRenderer.build(app,42,style,if(size)SizeF(w.toFloat(),h.toFloat()) else null,bindControls=false,volume=volume).apply(app,FrameLayout(app))
        layout(root,w,h);return root
    }
    private fun layout(root:View,w:Int,h:Int){root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));root.layout(0,0,w,h)}
    @Test fun frameIsCenteredAndCompactWithinATallHostWithoutChangingOuterSize() {
        val root=view();val frame=root.findViewById<View>(R.id.widget_frame)
        assertEquals(160,root.height);assertEquals(64,frame.height);assertEquals(48,frame.top)
        assertEquals(48,root.findViewById<View>(R.id.widget_toggle).height)
    }
    @Test fun changingFrameHeightDoesNotChangeButtonOrIconGeometry() {
        val short=view(compact.copy(frameHeightDp=32));val tall=view(compact.copy(frameHeightDp=120))
        for(id in listOf(R.id.widget_previous,R.id.widget_toggle,R.id.widget_next)) {
            val a=short.findViewById<View>(id);val b=tall.findViewById<View>(id)
            assertEquals(a.height,b.height);assertEquals(a.width,b.width);assertEquals(a.paddingLeft,b.paddingLeft)
        }
        assertEquals(32,short.findViewById<View>(R.id.widget_frame).height)
        assertEquals(120,tall.findViewById<View>(R.id.widget_frame).height)
    }
    @Test fun frameFitsSmallHostUnknownDimensionsAndNegativePadding() {
        for(known in listOf(true,false))for(height in listOf(48,56,80)) {
            val root=view(compact.copy(frameHeightDp=160,widgetPaddingVerticalDp=-24),h=height,size=known)
            val frame=root.findViewById<View>(R.id.widget_frame)
            assertTrue(frame.top>=0);assertTrue(frame.bottom<=height)
            assertEquals(48,root.findViewById<View>(R.id.widget_toggle).height)
        }
    }
    @Test fun volumeKeepsItsAxisAndTwoTouchTargets() {
        for((w,h) in listOf(280 to 160,80 to 240)) {
            val root=view(w=w,h=h,volume=true)
            val layout=root.findViewById<android.widget.LinearLayout>(R.id.widget_controls_row)
            assertEquals(if(h>w)android.widget.LinearLayout.VERTICAL else android.widget.LinearLayout.HORIZONTAL,layout.orientation)
            for(id in listOf(R.id.widget_previous,R.id.widget_next)) {
                val button=root.findViewById<View>(id);assertTrue(button.height>=48);assertTrue(button.width>=48)
            }
            assertTrue(root.findViewById<View>(R.id.widget_frame).height<=h)
        }
    }
    @Test fun reapplyFillCompactFillAndRemovingBorderClearsThePreviousLayer() {
        val parent=FrameLayout(app);val fill=compact.copy(frameMode=WidgetFrameMode.FILL)
        val root=WidgetRenderer.build(app,42,fill,SizeF(280f,160f),bindControls=false).apply(app,parent)
        fun apply(style:WidgetAppearance){WidgetRenderer.build(app,42,style,SizeF(280f,160f),bindControls=false).reapply(app,root);layout(root,280,160)}
        layout(root,280,160);assertEquals(160,root.findViewById<View>(R.id.widget_frame).height)
        apply(compact);assertEquals(64,root.findViewById<View>(R.id.widget_frame).height)
        apply(fill);assertEquals(160,root.findViewById<View>(R.id.widget_frame).height)
        apply(compact.copy(widgetBorder=false));assertEquals(160,root.findViewById<View>(R.id.widget_frame).height)
        assertNull(root.findViewById<View>(R.id.widget_frame).backgroundTintList)
    }
    @Test fun oldPreferencesAndBundlesKeepFillAndNewFieldsRoundTrip() {
        app.getSharedPreferences("widget_appearance",0).edit().putBoolean("widget_border",true).apply()
        assertEquals(WidgetFrameMode.FILL,WidgetStyleStore.load(app).frameMode)
        WidgetStyleStore.save(app,compact);assertEquals(compact,WidgetStyleStore.load(app))
        assertEquals(compact,WidgetAppearanceState.read(WidgetAppearanceState.write(compact)))
        assertEquals(WidgetFrameMode.FILL,WidgetAppearanceState.read(Bundle())!!.frameMode)
    }
    @Test fun backupV2PreservesFrameAndV1StillImportsWithLegacyGeometry() {
        val value=SettingsBackup(compact,15,listOf(BackupProfile("Compacta",WidgetKind.MEDIA,compact,false,WidgetLocation.LOCK_SCREEN)))
        val text=SettingsBackupCodec.encode(value);assertEquals(5,JSONObject(text).getInt("version"));assertEquals(value,SettingsBackupCodec.decode(text))
        val old=JSONObject(text).put("version",1)
        old.getJSONObject("defaults").remove("frame_mode");old.getJSONObject("defaults").remove("frame_height")
        old.getJSONArray("profiles").getJSONObject(0).getJSONObject("appearance").apply{remove("frame_mode");remove("frame_height")}
        val restored=SettingsBackupCodec.decode(old.toString());assertEquals(WidgetFrameMode.FILL,restored.defaults.frameMode)
        assertEquals(WidgetFrameMode.FILL,restored.profiles.single().appearance.frameMode)
        val invalid=JSONObject(text);invalid.getJSONObject("defaults").put("frame_height",15)
        try {SettingsBackupCodec.decode(invalid.toString());fail("Bad height")}catch(_:IllegalArgumentException){}
    }
    @Test fun globalPreviewWithoutHostShowsHeightChangesAndDoesNotWriteTheDraft() {
        WidgetStyleStore.save(app,compact)
        Robolectric.buildActivity(WidgetDefaultsActivity::class.java).setup().use{c ->
            val decor=c.get().window.decorView
            fun measure(){layout(decor,480,1000);shadowOf(android.os.Looper.getMainLooper()).idle();layout(decor,480,1000)}
            measure();assertEquals(64,c.get().findViewById<View>(R.id.widget_frame).height)
            c.get().findViewById<EditText>(R.id.widget_frame_height_value).setText("96")
            measure();assertEquals(96,c.get().findViewById<View>(R.id.widget_frame).height)
            assertEquals(64,WidgetStyleStore.load(app).frameHeightDp)
        }
    }
    @Test fun everyCornerRoundTripsThroughStoreStateAndBackupAndV1UsesAutomatic() {
        for(corner in WidgetFrameCorner.entries) {
            val style=compact.copy(frameCorner=corner)
            WidgetStyleStore.save(app,style);assertEquals(style,WidgetStyleStore.load(app))
            assertEquals(style,WidgetAppearanceState.read(WidgetAppearanceState.write(style)))
            val backup=SettingsBackup(style,5,emptyList())
            assertEquals(backup,SettingsBackupCodec.decode(SettingsBackupCodec.encode(backup)))
        }
        assertEquals(WidgetFrameCorner.AUTOMATIC,WidgetAppearanceState.read(Bundle())!!.frameCorner)
    }
    @Test fun editorKeepsDraftAcrossRecreationAndOnlySavesOnApply() {
        val manager=AppWidgetManager.getInstance(app)
        val id=shadowOf(manager).createWidget(MediaWidgetProvider::class.java,R.layout.widget_media_controls)
        shadowOf(manager).addBoundWidget(id,AppWidgetProviderInfo().apply{provider=ComponentName(app,MediaWidgetProvider::class.java)})
        WidgetStyleStore.save(app,WidgetAppearance(widgetBorder=true),id)
        val intent=Intent(app,WidgetConfigureActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id)
        Robolectric.buildActivity(WidgetConfigureActivity::class.java,intent).setup().use{c ->
            c.get().findViewById<Spinner>(R.id.widget_frame_mode).setSelection(WidgetFrameMode.COMPACT.ordinal)
            shadowOf(android.os.Looper.getMainLooper()).idle()
            c.get().findViewById<EditText>(R.id.widget_frame_height_value).setText("72")
            c.get().findViewById<Spinner>(R.id.widget_frame_corner).setSelection(WidgetFrameCorner.SOFT.ordinal)
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(WidgetFrameMode.FILL,WidgetStyleStore.load(app,id).frameMode)
            c.recreate();shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals("72",c.get().findViewById<EditText>(R.id.widget_frame_height_value).text.toString())
            c.get().findViewById<View>(R.id.widget_configuration_save).performClick()
            assertEquals(WidgetFrameMode.COMPACT,WidgetStyleStore.load(app,id).frameMode)
            assertEquals(72,WidgetStyleStore.load(app,id).frameHeightDp)
            assertEquals(WidgetFrameCorner.SOFT,WidgetStyleStore.load(app,id).frameCorner)
        }
    }
}
