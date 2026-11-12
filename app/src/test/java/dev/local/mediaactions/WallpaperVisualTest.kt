package dev.local.mediaactions

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
@Config(sdk=[33],qualifiers="pt-rBR-w480dp-h1050dp-mdpi",shadows=[ObservedWallpaperShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WallpaperVisualTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private val automatic=WidgetAppearance(iconScalePercent=60,spacingDp=22,buttonBorder=true,centerBorderOnly=true,
        progressRing=true,colorMode=WidgetColorMode.WALLPAPER,wallpaperTarget=WidgetWallpaperTarget.HOME,autoBorderColors=true,
        widgetBorder=true,frameMode=WidgetFrameMode.COMPACT,frameHeightDp=72,touchFeedback=false)
    private fun layout(v:View,w:Int,h:Int){v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h);settle(v)}
    private fun settle(v:View){v.jumpDrawablesToCurrentState();if(v is ViewGroup)for(i in 0 until v.childCount)settle(v.getChildAt(i))}
    private fun raster(v:View)=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888).also{v.draw(Canvas(it))}
    private fun widget(style:WidgetAppearance,volume:Boolean=false,w:Int=400,h:Int=112):Bitmap {
        val view=WidgetRenderer.build(app,42,style,SizeF(w.toFloat(),h.toFloat()),bindControls=false,
            playback=WidgetPlaybackState.PLAYING,progress=WidgetTrackProgress(.42f),volume=volume).apply(app,FrameLayout(app))
        layout(view,w,h);return raster(view)
    }
    private fun save(bitmap:Bitmap,name:String){val f=File("build/outputs/widget-previews/$name.png");f.parentFile!!.mkdirs();f.outputStream().use{assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}}
    @Test fun exportAutomaticMediaAndVolumeOnLightAndDarkBackgrounds() {
        val sheet=Bitmap.createBitmap(940,550,Bitmap.Config.ARGB_8888);val c=Canvas(sheet);c.drawColor(0xFF111A20.toInt())
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.WHITE;textSize=24f};c.drawText("Cor automática pelo wallpaper",24f,36f,p)
        p.textSize=16f;p.color=0xFFC6D2DA.toInt();c.drawText("Prévia Android/Skia · mesma configuração · bordas também adaptáveis",24f,64f,p)
        for((i,dark)in listOf(true,false).withIndex()) {
            val x=24f+i*458;val background=if(dark)0xFFF1E9DA.toInt() else 0xFF172B3C.toInt()
            val style=WidgetWallpaperColors.resolve(automatic,dark)
            p.color=background;c.drawRoundRect(x,90f,x+434,478f,24f,24f,p)
            p.color=if(dark)WidgetWallpaperColors.darkInk else Color.WHITE;p.textSize=20f
            c.drawText(if(dark)"Wallpaper claro" else "Wallpaper escuro",x+18,124f,p)
            p.textSize=15f;c.drawText("Mídia · progresso durante o play",x+18,157f,p)
            c.drawBitmap(widget(style),x+17,174f,null)
            c.drawText("Volume · sem acesso a notificações",x+18,327f,p)
            c.drawBitmap(widget(style.copy(progressRing=false,centerBorderOnly=false,buttonBorder=false),volume=true),x+17,345f,null)
        }
        p.color=0xFFC6D2DA.toInt();p.textSize=15f;c.drawText("Fundo do widget transparente. Cor manual de reserva se o Android não fornecer cores.",24f,518f,p)
        save(sheet,"wallpaper-automatico-comparacao")
    }
    @Test fun ringAdaptsInAutomaticModeAndFixedModeKeepsLegacyWhiteRing() {
        fun ring(style:WidgetAppearance)=WidgetProgressArtwork.draw(app,WidgetPlaybackIcons.center(WidgetIconStyle.OUTLINE,WidgetPlaybackState.PLAYING),WidgetWallpaperColors.darkInk,style,WidgetTrackProgress(.42f))
        val dark=ring(automatic.copy(darkIcons=true));val fixed=ring(automatic.copy(darkIcons=true,colorMode=WidgetColorMode.FIXED))
        assertEquals(WidgetWallpaperColors.darkInk,dark.getPixel(64,10));assertEquals(Color.WHITE,fixed.getPixel(64,10))
        val compact=widget(WidgetWallpaperColors.resolve(automatic,true));assertEquals(0,Color.alpha(compact.getPixel(200,0)))
    }
    @Test fun exportRealEditorWithAutomaticColorsAndFallbackControls() {
        WallpaperProbe.reads.set(0);WallpaperProbe.colors=android.app.WallpaperColors(Color.valueOf(Color.WHITE),null,null)
        WidgetStyleStore.save(app,automatic)
        Robolectric.buildActivity(WidgetDefaultsActivity::class.java).setup().use {controller ->
            val activity=controller.get();activity.findViewById<View>(R.id.settings_icons).performClick()
            val decor=activity.window.decorView;layout(decor,480,1050)
            val deadline=System.nanoTime()+2_000_000_000L
            while(WallpaperProbe.reads.get()==0 && System.nanoTime()<deadline){Thread.sleep(5);shadowOf(Looper.getMainLooper()).idle()}
            Thread.sleep(10);shadowOf(Looper.getMainLooper()).idle();layout(decor,480,1050)
            assertEquals(app.getString(R.string.widget_wallpaper_dark_active),activity.findViewById<android.widget.TextView>(R.id.widget_wallpaper_status).text)
            val scroll=activity.findViewById<ScrollView>(R.id.widget_configuration_scroll)
            scroll.scrollTo(0,0);settle(decor)
            save(raster(decor),"wallpaper-diagnostico-configuracao")
        }
        WidgetWallpaperRuntime.get(app)?.coordinator?.close()
    }
}
