package dev.local.mediaactions

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** These images are native resources, not photographs of a Samsung launcher or LockStar. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],qualifiers="pt-rBR-w480dp-h1120dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlaybackVolumeVisualTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private fun layout(root:View,w:Int,h:Int) {
        root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY))
        root.layout(0,0,w,h)
        fun settle(v:View) { v.jumpDrawablesToCurrentState();if(v is ViewGroup)for(i in 0 until v.childCount)settle(v.getChildAt(i)) }
        settle(root)
    }
    private fun save(bitmap:Bitmap,name:String) {
        val f=File("build/outputs/widget-previews/$name.png");f.parentFile!!.mkdirs()
        f.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
    }
    @Test fun exportCombinedHorizontalVerticalAndFallbackResources() {
        val b=Bitmap.createBitmap(980,720,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(b);canvas.drawColor(0xFF152832.toInt())
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE;textSize=30f }
        canvas.drawText("Reprodução e volume",28f,48f,p)
        p.textSize=17f
        canvas.drawText("Android/Skia · fundo ilustrativo · sem captura de host Samsung",28f,82f,p)
        fun widget(w:Int,h:Int,x:Int,y:Int,style:WidgetAppearance,state:WidgetPlaybackState) {
            val root=WidgetRenderer.build(app,0,style,SizeF(w.toFloat(),h.toFloat()),false,state,
                WidgetTrackProgress(0.42f),kind=WidgetKind.PLAYBACK_VOLUME).apply(app,FrameLayout(app))
            layout(root,w,h)
            val picture=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);root.draw(Canvas(picture))
            canvas.drawBitmap(picture,x.toFloat(),y.toFloat(),null)
        }
        val style=WidgetAppearance(iconScalePercent=65,spacingDp=24,buttonBorder=true,centerBorderOnly=true,
            progressRing=true,widgetBorder=true,frameMode=WidgetFrameMode.COMPACT,frameHeightDp=48,frameCorner=WidgetFrameCorner.EXTRA_ROUND)
        p.textSize=20f;canvas.drawText("Horizontal · − / Pausar / +",28f,140f,p)
        widget(420,96,28,160,style,WidgetPlaybackState.PLAYING)
        canvas.drawText("Pausado · ícones menores e sem progresso",28f,310f,p)
        widget(420,48,28,354,style.copy(iconScalePercent=45,widgetBorder=false),WidgetPlaybackState.PAUSED)
        canvas.drawText("Sem sessão · volume mantém suas bordas",28f,470f,p)
        widget(420,96,28,490,style.copy(centerBorderOnly=false,widgetBorder=false),WidgetPlaybackState.UNKNOWN)
        canvas.drawText("Vertical",555f,140f,p)
        widget(96,360,552,160,style,WidgetPlaybackState.PLAYING)
        p.textSize=17f;canvas.drawText("+ em cima",685f,210f,p);canvas.drawText("Play/Pause no centro",685f,340f,p);canvas.drawText("− embaixo",685f,470f,p)
        p.textSize=20f;canvas.drawText("Espaço insuficiente",555f,570f,p)
        widget(140,80,555,590,style,WidgetPlaybackState.PLAYING)
        p.textSize=16f;canvas.drawText("Novo tipo opcional · os widgets atuais continuam disponíveis",28f,690f,p)
        save(b,"reproducao-volume-layouts")
    }
    @Test fun exportActualCombinedWidgetEditor() {
        val manager=AppWidgetManager.getInstance(app)
        val id=shadowOf(manager).createWidget(PlaybackVolumeWidgetProvider::class.java,R.layout.widget_playback_volume_controls)
        shadowOf(manager).addBoundWidget(id,AppWidgetProviderInfo().apply {provider=ComponentName(app,PlaybackVolumeWidgetProvider::class.java)})
        manager.updateAppWidgetOptions(id,Bundle().apply {
            putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,arrayListOf(SizeF(280f,64f)))
        })
        WidgetStyleStore.save(app,WidgetAppearance(iconScalePercent=65,spacingDp=20,buttonBorder=true,centerBorderOnly=true,progressRing=true),id)
        Robolectric.buildActivity(WidgetConfigureActivity::class.java,
            Intent(app,WidgetConfigureActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id)).setup().use { c ->
            val root=c.get().window.decorView;layout(root,480,1120)
            shadowOf(android.os.Looper.getMainLooper()).idle();layout(root,480,1120)
            val b=Bitmap.createBitmap(480,1120,Bitmap.Config.ARGB_8888);root.draw(Canvas(b))
            save(b,"reproducao-volume-editor")
        }
    }
}
