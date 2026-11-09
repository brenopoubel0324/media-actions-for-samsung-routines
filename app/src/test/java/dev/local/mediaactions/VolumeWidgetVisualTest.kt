package dev.local.mediaactions

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.graphics.*
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],qualifiers="pt-rBR-w1280dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VolumeWidgetVisualTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private fun save(b:Bitmap,name:String) {val f=File("build/outputs/widget-previews/$name.png");f.parentFile!!.mkdirs();f.outputStream().use {assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))}}
    @Test fun exportActualTwoButtonWidgetStylesAndReapply() {
        val b=Bitmap.createBitmap(780,500,Bitmap.Config.ARGB_8888);val c=Canvas(b);c.drawColor(0xFF152832.toInt())
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.WHITE;textSize=26f}
        c.drawText("Volume de mídia · um nível por toque",24f,40f,p)
        p.textSize=16f;c.drawText("Android/Skia · exemplos de estilo, sem captura de Tab físico",24f,72f,p)
        val styles=listOf(WidgetAppearance(iconScalePercent=65,spacingDp=24),WidgetAppearance(iconScalePercent=75,spacingDp=40,buttonBorder=true,buttonBorderWidthDp=2),WidgetAppearance(iconScalePercent=50,spacingDp=70,widgetBorder=true,widgetPaddingHorizontalDp=16))
        for((i,style)in styles.withIndex()) {
            val root=WidgetRenderer.build(app,0,style,SizeF(400f,96f),bindControls=false,volume=true).apply(app,FrameLayout(app))
            root.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(96,View.MeasureSpec.EXACTLY));root.layout(0,0,400,96)
            assertNull(root.findViewById<View>(R.id.widget_toggle))
            val preview=Bitmap.createBitmap(400,96,Bitmap.Config.ARGB_8888);root.draw(Canvas(preview));c.drawBitmap(preview,280f,100f+i*125,null)
            p.textSize=16f;c.drawText(listOf("Transparente","Bordas nos botões","Ícones menores + moldura")[i],24f,154f+i*125,p)
            WidgetRenderer.build(app,0,style.copy(widgetBorder=false,buttonBorder=false),SizeF(400f,96f),false,volume=true).reapply(app,root)
        }
        save(b,"volume-estilos")
    }
    @Test @Config(qualifiers="pt-rBR-w480dp-h960dp-mdpi") fun exportRealVolumeInstanceEditor() {
        val manager=AppWidgetManager.getInstance(app)
        val id=shadowOf(manager).createWidget(VolumeWidgetProvider::class.java,R.layout.widget_volume_controls)
        shadowOf(manager).addBoundWidget(id,AppWidgetProviderInfo().apply {provider=ComponentName(app,VolumeWidgetProvider::class.java)})
        manager.updateAppWidgetOptions(id,android.os.Bundle().apply {putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,240);putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,64)})
        WidgetStyleStore.save(app,WidgetAppearance(iconScalePercent=65,spacingDp=32,buttonBorder=true),id)
        Robolectric.buildActivity(WidgetConfigureActivity::class.java,Intent(app,WidgetConfigureActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id)).setup().use {a ->
            val root=a.get().window.decorView
            root.measure(View.MeasureSpec.makeMeasureSpec(480,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(960,View.MeasureSpec.EXACTLY));root.layout(0,0,480,960)
            fun settle(v:View){v.jumpDrawablesToCurrentState();if(v is ViewGroup)for(i in 0 until v.childCount)settle(v.getChildAt(i))};settle(root)
            val b=Bitmap.createBitmap(480,960,Bitmap.Config.ARGB_8888);root.draw(Canvas(b));save(b,"volume-editor")
        }
    }
}
