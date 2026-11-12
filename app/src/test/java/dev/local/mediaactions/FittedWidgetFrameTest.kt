package dev.local.mediaactions

import android.graphics.*
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33,36],qualifiers="mdpi")
class FittedWidgetFrameTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private val fitted=WidgetAppearance(widgetBorder=true,frameMode=WidgetFrameMode.FIT_BUTTONS,spacingDp=16,iconScalePercent=60)
    private fun layout(v:View,w:Int,h:Int){v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h)}
    private fun view(style:WidgetAppearance=fitted,w:Int=320,h:Int=160,volume:Boolean=false,known:Boolean=true):View =
        WidgetRenderer.build(app,42,style,if(known)SizeF(w.toFloat(),h.toFloat())else null,bindControls=false,volume=volume).apply(app,FrameLayout(app)).also{layout(it,w,h)}
    @Test fun fittedBorderHugsBothAxesWithoutShrinkingTheTargets() {
        val root=view();val row=root.findViewById<View>(R.id.widget_controls_row);val frame=root.findViewById<View>(R.id.widget_frame)
        assertEquals(34,frame.height);assertEquals(row.width-24+6,frame.width)
        assertEquals((320-frame.width)/2,frame.left);assertEquals((160-frame.height)/2,frame.top)
        for(id in listOf(R.id.widget_previous,R.id.widget_toggle,R.id.widget_next))assertEquals(48,root.findViewById<View>(id).height)
    }
    @Test fun frameFollowsArtworkAndSpacingButIgnoresOldManualHeight() {
        val a=view(fitted.copy(frameHeightDp=160,iconScalePercent=60));val b=view(fitted.copy(frameHeightDp=56,iconScalePercent=60))
        assertEquals(a.findViewById<View>(R.id.widget_frame).height,b.findViewById<View>(R.id.widget_frame).height)
        assertEquals(a.findViewById<View>(R.id.widget_frame).width,b.findViewById<View>(R.id.widget_frame).width)
        assertEquals(54,view(fitted.copy(progressRing=true)).findViewById<View>(R.id.widget_frame).height)
        assertEquals(54,view(fitted.copy(buttonBorder=true)).findViewById<View>(R.id.widget_frame).height)
        assertTrue(view(fitted.copy(iconScalePercent=150)).findViewById<View>(R.id.widget_frame).height>a.findViewById<View>(R.id.widget_frame).height)
        assertTrue(view(fitted.copy(spacingDp=32)).findViewById<View>(R.id.widget_frame).width>a.findViewById<View>(R.id.widget_frame).width)
    }
    @Test fun volumePreservesOrientationAndBoundsIncludingPaddingAndThickStroke() {
        for((w,h)in listOf(320 to 160,80 to 240,144 to 48))for(volume in listOf(false,true)) {
            val root=view(fitted.copy(borderWidthDp=8,widgetPaddingHorizontalDp=8,widgetPaddingVerticalDp=-24),w,h,volume)
            val frame=root.findViewById<View>(R.id.widget_frame)
            assertTrue(frame.left>=0);assertTrue(frame.right<=w);assertTrue(frame.top>=0);assertTrue(frame.bottom<=h)
            if(volume && h>w)assertTrue(root.findViewById<View>(R.id.widget_controls_row).height>root.findViewById<View>(R.id.widget_controls_row).width)
        }
    }
    @Test fun reapplyRestoresFullWidthAndClearsNativeMinimums() {
        val root=view(known=false)
        for(mode in listOf(WidgetFrameMode.COMPACT,WidgetFrameMode.FILL,WidgetFrameMode.FIT_BUTTONS)) {
            WidgetRenderer.build(app,42,fitted.copy(frameMode=mode),SizeF(320f,160f),bindControls=false).reapply(app,root);layout(root,320,160)
            val frame=root.findViewById<View>(R.id.widget_frame)
            assertEquals(0,frame.minimumWidth)
            if(mode!=WidgetFrameMode.FIT_BUTTONS)assertEquals(320,frame.width)else assertEquals(34,frame.height)
        }
        for(mode in listOf(WidgetFrameMode.COMPACT,WidgetFrameMode.FILL,WidgetFrameMode.FIT_BUTTONS)) {
            WidgetRenderer.build(app,42,fitted.copy(frameMode=mode),null,bindControls=false).reapply(app,root);layout(root,320,160)
            val row=root.findViewById<View>(R.id.widget_controls_row)
            assertEquals(if(mode==WidgetFrameMode.FIT_BUTTONS)144 else 320,row.width)
            assertEquals(if(mode==WidgetFrameMode.FILL)160 else 48,row.height)
        }
    }
    @Test fun unknownHostClampsTheFrameAndKeepsTheControlsInsideIt() {
        for((w,h)in listOf(144 to 48,320 to 160)) {
            val root=view(w=w,h=h,known=false);val frame=root.findViewById<View>(R.id.widget_frame);val row=root.findViewById<View>(R.id.widget_controls_row)
            assertTrue(frame.left>=0);assertTrue(frame.right<=w);assertTrue(frame.top>=0);assertTrue(frame.bottom<=h)
            assertEquals(144,row.width);assertEquals(48,row.height)
            assertTrue(frame.width<row.width);assertTrue(frame.height<row.height)
        }
    }
    @Test fun newModeRoundTripsAndOlderBackupsRemainSupported() {
        WidgetStyleStore.save(app,fitted);assertEquals(fitted,WidgetStyleStore.load(app))
        assertEquals(fitted,WidgetAppearanceState.read(WidgetAppearanceState.write(fitted)))
        val value=SettingsBackup(fitted,15,emptyList());val text=SettingsBackupCodec.encode(value)
        assertEquals(4,JSONObject(text).getInt("version"));assertEquals(value,SettingsBackupCodec.decode(text))
        for(version in 1..3) {
            val legacy=JSONObject(SettingsBackupCodec.encode(value.copy(defaults=WidgetAppearance()))).put("version",version)
            assertEquals(WidgetFrameMode.FILL,SettingsBackupCodec.decode(legacy.toString()).defaults.frameMode)
        }
        try{SettingsBackupCodec.decode(JSONObject(text).put("version",3).toString());fail("Old schema cannot contain fitted mode")}catch(_:IllegalArgumentException){}
    }
    @Test fun editorHidesManualHeightAndSavesTheNewMode() {
        WidgetStyleStore.save(app,fitted.copy(frameMode=WidgetFrameMode.COMPACT))
        org.robolectric.Robolectric.buildActivity(WidgetDefaultsActivity::class.java).setup().use { controller ->
            val activity=controller.get()
            activity.findViewById<android.widget.Spinner>(R.id.widget_frame_mode).setSelection(WidgetFrameMode.FIT_BUTTONS.ordinal)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            val height=activity.findViewById<View>(R.id.widget_frame_height_slider)
            // The numeric control lives inside the hidden height group.
            assertEquals(View.GONE,((height.parent as View).parent as View).visibility)
            activity.findViewById<View>(R.id.widget_configuration_save).performClick()
            assertEquals(WidgetFrameMode.FIT_BUTTONS,WidgetStyleStore.load(app).frameMode)
        }
    }
    @Test @Config(sdk=[33]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun exportNativeBeforeAndAfter() {
        val bitmap=Bitmap.createBitmap(740,310,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap);canvas.drawColor(0xFF14212A.toInt())
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.WHITE;textSize=23f};canvas.drawText("Borda mais próxima dos botões",20f,32f,paint)
        for((i,mode)in listOf(WidgetFrameMode.COMPACT,WidgetFrameMode.FIT_BUTTONS).withIndex()) {
            val x=20f+i*360;paint.textSize=18f;canvas.drawText(if(i==0)"Compacta · 64 dp"else "Justa aos botões · 34 dp",x,75f,paint)
            val root=view(fitted.copy(frameMode=mode),w=340,h=160)
            canvas.save();canvas.translate(x,95f);root.draw(canvas);canvas.restore()
        }
        paint.textSize=15f;canvas.drawText("Mesmos ícones, espaçamento e áreas de toque de 48 dp. Fundo transparente.",20f,285f,paint)
        val file=File("build/outputs/widget-previews/borda-justa-comparacao.png");file.parentFile!!.mkdirs();file.outputStream().use{assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}
    }
}
