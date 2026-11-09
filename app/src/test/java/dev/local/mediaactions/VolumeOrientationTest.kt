package dev.local.mediaactions

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33,36],qualifiers="pt-rBR-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VolumeOrientationTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private val manager get()=AppWidgetManager.getInstance(app)
    private fun options(w:Float,h:Float)=Bundle().apply {putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,arrayListOf(SizeF(w,h)))}
    private fun layout(v:View,w:Int,h:Int){v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h)}
    @Test fun resizingSameInstanceChangesAxisOrderAndKeepsStyleAndCommands() {
        val id=shadowOf(manager).createWidget(VolumeWidgetProvider::class.java,R.layout.widget_volume_controls)
        shadowOf(manager).addBoundWidget(id,AppWidgetProviderInfo().apply {provider=ComponentName(app,VolumeWidgetProvider::class.java)})
        val style=WidgetAppearance(iconScalePercent=60,spacingDp=24,buttonBorder=true,widgetPaddingHorizontalDp=8,widgetPaddingVerticalDp=12)
        WidgetStyleStore.save(app,style,id)
        val upBefore=VolumeWidgetProvider.controlIntent(app,id,true)
        for ((w,h) in listOf(320 to 96,96 to 320,320 to 96)) {
            VolumeWidgetProvider().onAppWidgetOptionsChanged(app,manager,id,options(w.toFloat(),h.toFloat()))
            val root=shadowOf(manager).getViewFor(id);layout(root,w,h)
            val row=root.findViewById<LinearLayout>(R.id.widget_controls_row)
            val minus=root.findViewById<View>(R.id.widget_previous);val plus=root.findViewById<View>(R.id.widget_next)
            assertEquals(if(h>w) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL,row.orientation)
            assertTrue(if(h>w) plus.top<minus.top else minus.left<plus.left)
            assertTrue(minus.width>=48 && minus.height>=48);assertTrue(plus.width>=48 && plus.height>=48)
            assertEquals(8,root.paddingLeft);assertEquals(12,root.paddingTop)
            assertEquals(style,WidgetStyleStore.load(app,id));assertEquals(upBefore,VolumeWidgetProvider.controlIntent(app,id,true))
        }
    }
    @Test fun verticalPaddingDoesNotSwapAxesOrLoseItsNegativeRange() {
        val style=WidgetAppearance(widgetPaddingHorizontalDp=8,widgetPaddingVerticalDp=-12,spacingDp=24)
        val result=VolumeWidgetLayout.calculate(96f,320f,style)
        assertTrue(result.vertical);assertEquals(8,result.geometry.widgetPaddingHorizontalDp);assertEquals(-12,result.geometry.widgetPaddingVerticalDp)
        assertEquals(48,VolumeWidgetLayout.calculate(48f,96f,WidgetAppearance(spacingDp=144)).geometry.buttonDp)
        assertEquals(48,VolumeWidgetLayout.calculate(96f,48f,WidgetAppearance(spacingDp=144)).geometry.buttonDp)
        assertFalse(VolumeWidgetLayout.calculate(128f,128f,style).vertical)
        assertFalse(VolumeWidgetLayout.isVertical(null));assertFalse(VolumeWidgetLayout.calculate(Float.NaN,200f,style).vertical)
        val media=WidgetRenderer.build(app,0,style,SizeF(96f,320f),false).apply(app,FrameLayout(app))
        assertEquals(LinearLayout.HORIZONTAL,media.findViewById<LinearLayout>(R.id.widget_controls_row).orientation)
    }
    @Test fun responsiveVariantsAndMissingSizeUseValidLayoutsWithoutChangingStoredValues() {
        val packet=VolumeWidgetProvider.render(app,1,Bundle().apply {putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,arrayListOf(SizeF(320f,96f),SizeF(96f,320f)))})
        assertNotNull(packet.apply(app,FrameLayout(app)))
        val fallback=VolumeWidgetProvider.render(app,1,Bundle()).apply(app,FrameLayout(app))
        assertEquals(LinearLayout.HORIZONTAL,fallback.findViewById<LinearLayout>(R.id.widget_controls_row).orientation)
        val vertical=WidgetRenderer.build(app,1,WidgetAppearance(),SizeF(96f,320f),false,volume=true)
        val horizontal=WidgetRenderer.build(app,1,WidgetAppearance(),SizeF(320f,96f),false,volume=true)
        assertNotEquals(horizontal.layoutId,vertical.layoutId)
    }
    @Test @Config(sdk=[33]) fun exportRealAutomaticLayouts() {
        val b=Bitmap.createBitmap(760,510,Bitmap.Config.ARGB_8888);val c=Canvas(b);c.drawColor(0xFF152832.toInt())
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.WHITE;textSize=25f}
        c.drawText("Volume · orientação automática ao redimensionar",20f,40f,p)
        p.textSize=16f;c.drawText("Android/Skia · o fundo é de exemplo; widget transparente",20f,72f,p)
        val style=WidgetAppearance(iconScalePercent=65,spacingDp=24,buttonBorder=true,buttonBorderWidthDp=2)
        for((w,h,x,y) in listOf(listOf(320,96,40,200),listOf(96,320,510,130))) {
            val root=WidgetRenderer.build(app,0,style,SizeF(w.toFloat(),h.toFloat()),false,volume=true).apply(app,FrameLayout(app));layout(root,w,h)
            val render=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);root.draw(Canvas(render));c.drawBitmap(render,x.toFloat(),y.toFloat(),null)
        }
        p.textSize=21f;c.drawText("Mais largo: − / +",55f,365f,p);c.drawText("Mais alto",500f,110f,p)
        p.textSize=16f;c.drawText("Mesmo widget e ajustes · + em cima, − embaixo",20f,490f,p)
        val f=File("build/outputs/widget-previews/volume-orientacao.png");f.parentFile!!.mkdirs();f.outputStream().use {assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))}
    }
}
