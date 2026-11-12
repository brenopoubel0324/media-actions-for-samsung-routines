package dev.local.mediaactions

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.graphics.*
import android.os.Looper
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],qualifiers="pt-rBR-w480dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompactFrameVisualTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private val compact=WidgetAppearance(widgetBorder=true,frameMode=WidgetFrameMode.COMPACT,frameHeightDp=64,
        borderWidthDp=2,fillWidth=true,iconScalePercent=55,touchFeedback=false)
    private fun root(style:WidgetAppearance,w:Int=300,h:Int=160,volume:Boolean=false):View =
        WidgetRenderer.build(app,42,style,SizeF(w.toFloat(),h.toFloat()),bindControls=false,volume=volume).apply(app,FrameLayout(app)).also{layout(it,w,h)}
    private fun layout(v:View,w:Int,h:Int){v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h);settle(v)}
    private fun settle(v:View){v.jumpDrawablesToCurrentState();if(v is ViewGroup)for(i in 0 until v.childCount)settle(v.getChildAt(i))}
    private fun raster(v:View)=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888).also{v.draw(Canvas(it))}
    private fun save(b:Bitmap,name:String){val f=File("build/outputs/widget-previews/$name.png");f.parentFile!!.mkdirs();f.outputStream().use{assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))}}
    @Test fun compactPixelsStayWithinCenteredBandAndTurningItOffClearsWhiteStroke() {
        val v=root(compact);val b=raster(v)
        assertEquals(0,Color.alpha(b.getPixel(150,0)))
        assertEquals(Color.WHITE,b.getPixel(150,49))
        assertEquals(0,Color.alpha(b.getPixel(150,115)))
        WidgetRenderer.build(app,42,compact.copy(widgetBorder=false),SizeF(300f,160f),bindControls=false).reapply(app,v);layout(v,300,160)
        assertEquals(0,Color.alpha(raster(v).getPixel(150,49)))
    }
    @Test fun exportNativeComparison() {
        val sheet=Bitmap.createBitmap(980,310,Bitmap.Config.ARGB_8888);val canvas=Canvas(sheet);canvas.drawColor(0xFF101619.toInt())
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.WHITE;textSize=23f}
        canvas.drawText("Moldura independente da área reservada pelo LockStar",20f,32f,paint)
        paint.textSize=15f;paint.color=0xFFB7C9CD.toInt();canvas.drawText("Prévia Android/Skia · mesmo espaço de 300 × 160 dp · fundo transparente",20f,58f,paint)
        val styles=listOf(compact.copy(frameMode=WidgetFrameMode.FILL),compact,compact.copy(frameHeightDp=96))
        for((i,style)in styles.withIndex()) {
            val x=20f+i*320
            paint.shader=LinearGradient(x,95f,x+300,255f,intArrayOf(0xFFFF7A00.toInt(),0xFFC94700.toInt(),0xFF1A1410.toInt()),null,Shader.TileMode.CLAMP)
            canvas.drawRect(x,95f,x+300,255f,paint);paint.shader=null
            canvas.drawBitmap(raster(root(style)),x,95f,null)
            paint.color=Color.WHITE;paint.textSize=18f
            canvas.drawText(listOf("Preencher widget","Compacta · 64 dp","Compacta · 96 dp")[i],x,84f,paint)
        }
        paint.textSize=15f;paint.color=0xFFB7C9CD.toInt();canvas.drawText("A altura da moldura compacta muda a folga; os botões permanecem em 48 dp.",20f,289f,paint)
        save(sheet,"moldura-compacta-comparacao")
    }
    @Test fun squareAndRoundedCornersHaveDifferentPixelsAndExportNativeChoices() {
        val sheet=Bitmap.createBitmap(940,350,Bitmap.Config.ARGB_8888);val c=Canvas(sheet);c.drawColor(0xFF101619.toInt())
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.WHITE;textSize=22f};c.drawText("Arredondamento da moldura · prévia Android/Skia",20f,29f,p)
        val pixels=mutableListOf<IntArray>()
        for((i,corner)in WidgetFrameCorner.entries.withIndex()) {
            val v=root(compact.copy(frameCorner=corner,frameHeightDp=96),280,120)
            val b=raster(v);pixels+=IntArray(b.width*b.height).also{b.getPixels(it,0,b.width,0,0,b.width,b.height)}
            val x=20f+(i%3)*305;val y=65f+(i/3)*140
            p.color=Color.WHITE;p.textSize=16f;c.drawText(app.getString(corner.label),x,y-7,p)
            p.color=0xFFB44B0B.toInt();c.drawRect(x,y,x+280,y+120,p);c.drawBitmap(b,x,y,null)
        }
        assertEquals(6,pixels.map{it.contentHashCode()}.distinct().size)
        save(sheet,"moldura-arredondamento")
    }
    @Test fun exportActualEditorWithFrameSettings() {
        val m=AppWidgetManager.getInstance(app);val id=shadowOf(m).createWidget(MediaWidgetProvider::class.java,R.layout.widget_media_controls)
        shadowOf(m).addBoundWidget(id,AppWidgetProviderInfo().apply{provider=ComponentName(app,MediaWidgetProvider::class.java)})
        m.updateAppWidgetOptions(id,android.os.Bundle().apply{putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,arrayListOf(SizeF(300f,160f)))})
        WidgetStyleStore.save(app,compact,id)
        val intent=Intent(app,WidgetConfigureActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id)
        Robolectric.buildActivity(WidgetConfigureActivity::class.java,intent).setup().use{c ->
            c.get().findViewById<View>(R.id.settings_borders).performClick()
            val decor=c.get().window.decorView;layout(decor,480,1000);shadowOf(Looper.getMainLooper()).idle();layout(decor,480,1000)
            val scroll=c.get().findViewById<ScrollView>(R.id.widget_configuration_scroll)
            val target=c.get().findViewById<View>(R.id.widget_frame_mode);val rect=Rect(0,0,target.width,target.height+260)
            scroll.offsetDescendantRectToMyCoords(target,rect);scroll.scrollTo(0,rect.top+scroll.scrollY-40)
            settle(decor);save(raster(decor),"moldura-compacta-configuracao")
        }
    }
}
