package dev.local.mediaactions
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],qualifiers="mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetRingVisualTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private val base=WidgetAppearance(iconStyle=WidgetIconStyle.MATERIAL_ROUNDED,iconScalePercent=60,
        buttonBorder=true,centerBorderOnly=true,buttonBorderWidthDp=2,touchFeedback=false,progressRing=true,spacingDp=24)
    private fun view(style: WidgetAppearance, fraction: Float?=0.42f, state: WidgetPlaybackState=WidgetPlaybackState.PLAYING): View {
        val root=WidgetRenderer.build(app,42,style,SizeF(360f,96f),false,state,
            fraction?.let(::WidgetTrackProgress)).apply(app,FrameLayout(app))
        root.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(96,View.MeasureSpec.EXACTLY));root.layout(0,0,360,96)
        return root
    }
    private fun bitmap(view: View)=Bitmap.createBitmap(360,96,Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    private fun pixels(bitmap: Bitmap)=IntArray(bitmap.width*bitmap.height).also { bitmap.getPixels(it,0,bitmap.width,0,0,bitmap.width,bitmap.height) }
    @Test fun onlyCenterHasAStaticBorderWhenChosenAndReapplyRestoresOtherBorders() {
        val style=base.copy(progressRing=false);val root=view(style)
        for(id in listOf(R.id.widget_previous,R.id.widget_next)) assertTrue(root.findViewById<View>(id).background is ColorDrawable)
        assertFalse(root.findViewById<View>(R.id.widget_toggle).background is ColorDrawable)
        WidgetRenderer.build(app,42,style.copy(centerBorderOnly=false),SizeF(360f,96f),false).reapply(app,root)
        assertFalse(root.findViewById<View>(R.id.widget_previous).background is ColorDrawable)
    }
    @Test fun realProgressChangesTheArcAndUnknownDurationDoesNotDrawAFakeMarker() {
        assertFalse(pixels(bitmap(view(base,0.1f))).contentEquals(pixels(bitmap(view(base,0.8f)))))
        val image=WidgetProgressArtwork.draw(app,R.drawable.ic_state_outline_pause,Color.WHITE,base,WidgetTrackProgress(0.25f))
        assertEquals(0xFF26313B.toInt(),image.getPixel(118,64))
        assertEquals(0,Color.alpha(image.getPixel(0,0)))
        val unknown=WidgetProgressArtwork.draw(app,R.drawable.ic_state_outline_pause,Color.WHITE,base,null)
        assertNotEquals(0xFF26313B.toInt(),unknown.getPixel(118,64))
    }
    @Test fun negativePaddingIsVerticalOnlyAndCanBeRemovedByReapply() {
        val root=view(base.copy(widgetPaddingVerticalDp=-12))
        assertEquals(-12,root.paddingTop);assertEquals(0,root.paddingLeft)
        WidgetRenderer.build(app,42,base,SizeF(360f,96f),false,WidgetPlaybackState.PLAYING,WidgetTrackProgress(0.42f)).reapply(app,root)
        root.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(96,View.MeasureSpec.EXACTLY));root.layout(0,0,360,96)
        assertEquals(0,root.paddingTop)
        assertTrue(pixels(bitmap(view(base))).contentEquals(pixels(bitmap(root))))
    }
    @Test fun maximumStrokeKeepsTheWholeMarkerInsideTheRaster() {
        val image=WidgetProgressArtwork.draw(app,R.drawable.ic_state_outline_pause,Color.WHITE,
            base.copy(buttonBorderWidthDp=8),WidgetTrackProgress(0.25f))
        for(i in 0 until image.width) {
            assertEquals(0,Color.alpha(image.getPixel(i,0)))
            assertEquals(0,Color.alpha(image.getPixel(i,image.height-1)))
            assertEquals(0,Color.alpha(image.getPixel(0,i)))
            assertEquals(0,Color.alpha(image.getPixel(image.width-1,i)))
        }
    }
    @Test fun turningProgressOffClearsTheBitmapAndRestoresStaticAppearance() {
        val root=view(base)
        val plain=base.copy(progressRing=false)
        WidgetRenderer.build(app,42,plain,SizeF(360f,96f),false,WidgetPlaybackState.PLAYING).reapply(app,root)
        root.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(96,View.MeasureSpec.EXACTLY));root.layout(0,0,360,96)
        assertTrue(pixels(bitmap(view(plain))).contentEquals(pixels(bitmap(root))))
    }
    @Test fun onlyConfirmedPlayingDrawsTheIndicatorAndOtherStatesMatchTheirStaticAppearance() {
        for (shadow in listOf(false, true)) for (feedback in listOf(false, true)) {
            val style = base.copy(iconShadow=shadow, touchFeedback=feedback)
            for (state in WidgetPlaybackState.entries.filter { it != WidgetPlaybackState.PLAYING }) {
                assertTrue("Unexpected indicator for $state", pixels(bitmap(view(style, state=state)))
                    .contentEquals(pixels(bitmap(view(style.copy(progressRing=false), state=state)))))
            }
            assertFalse(pixels(bitmap(view(style))).contentEquals(pixels(bitmap(view(style.copy(progressRing=false))))))
        }
    }
    @Test fun playPauseAndUnknownTransitionsClearAndRestoreTheRingOnReapply() {
        val root = view(base)
        for (state in listOf(WidgetPlaybackState.PAUSED, WidgetPlaybackState.PLAYING,
            WidgetPlaybackState.UNKNOWN, WidgetPlaybackState.PLAYING, WidgetPlaybackState.TRANSITIONING)) {
            WidgetRenderer.build(app,42,base,SizeF(360f,96f),false,state,WidgetTrackProgress(0.42f)).reapply(app,root)
            root.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(96,View.MeasureSpec.EXACTLY));root.layout(0,0,360,96)
            assertTrue("Stale bitmap/background/padding for $state", pixels(bitmap(view(base,state=state))).contentEquals(pixels(bitmap(root))))
        }
    }
    @Test fun unavailableSessionRemovesButtonBordersAndReapplyRestoresThemWhenPaused() {
        val root = view(base,state=WidgetPlaybackState.UNKNOWN)
        assertTrue(root.findViewById<View>(R.id.widget_toggle).background is ColorDrawable)
        WidgetRenderer.build(app,42,base,SizeF(360f,96f),false,WidgetPlaybackState.PAUSED).reapply(app,root)
        root.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(96,View.MeasureSpec.EXACTLY));root.layout(0,0,360,96)
        assertFalse(root.findViewById<View>(R.id.widget_toggle).background is ColorDrawable)
        assertTrue(pixels(bitmap(view(base,state=WidgetPlaybackState.PAUSED))).contentEquals(pixels(bitmap(root))))
    }
    @Test fun exportPlayingOnlyComparison() {
        val image=Bitmap.createBitmap(1180,300,Bitmap.Config.ARGB_8888);val canvas=Canvas(image);canvas.drawColor(0xFF111820.toInt())
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE;textSize=28f }
        canvas.drawText("Indicador de progresso só durante reprodução",24f,42f,paint)
        paint.textSize=17f;paint.color=0xFFBACCD5.toInt()
        canvas.drawText("Prévia Android/Skia · progresso de demonstração · borda estática central ativada",24f,76f,paint)
        val states=listOf(WidgetPlaybackState.PLAYING,WidgetPlaybackState.PAUSED,WidgetPlaybackState.UNKNOWN)
        val labels=listOf("Tocando · indicador visível","Pausado · só borda estática","Sem sessão · sem borda")
        for(i in states.indices) {
            val x=16f+i*390;paint.color=0xFF233344.toInt();canvas.drawRoundRect(x,102f,x+378,278f,18f,18f,paint)
            paint.color=Color.WHITE;paint.textSize=19f;canvas.drawText(labels[i],x+12,134f,paint)
            canvas.drawBitmap(bitmap(view(base,state=states[i])),x+9,164f,null)
        }
        val out=File("build/outputs/widget-previews/progresso-so-em-play.png");requireNotNull(out.parentFile).mkdirs()
        out.outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,it)) }
    }
    @Test fun exportNativeReferenceStyleAndVerticalPadding() {
        val sheet=Bitmap.createBitmap(1180,650,Bitmap.Config.ARGB_8888);val canvas=Canvas(sheet);canvas.drawColor(0xFF111820.toInt())
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE;textSize=30f }
        canvas.drawText("Play/Pause em destaque",24f,44f,paint)
        paint.textSize=18f;paint.color=0xFFBACCD5.toInt()
        canvas.drawText("Prévia Android/Skia · progresso de demonstração · fundo do widget transparente",24f,78f,paint)
        val names=listOf("Só borda no centro","Progresso branco · 25%","Progresso branco · 70%")
        for(i in 0..2) {
            val x=16f+i*390;paint.color=0xFF233344.toInt();canvas.drawRoundRect(x,106f,x+378,288f,18f,18f,paint)
            paint.color=Color.WHITE;paint.textSize=20f;canvas.drawText(names[i],x+12,138f,paint)
            canvas.drawBitmap(bitmap(view(base.copy(progressRing=i!=0),if(i==2)0.7f else 0.25f)),x+9,168f,null)
        }
        paint.color=Color.WHITE;paint.textSize=24f;canvas.drawText("Padding vertical: positivo, zero e negativo",24f,342f,paint)
        for((i,padding) in listOf(12,0,-12).withIndex()) {
            val x=16f+i*390;paint.color=0xFF233344.toInt();canvas.drawRoundRect(x,370f,x+378,555f,18f,18f,paint)
            paint.color=Color.WHITE;paint.textSize=20f;canvas.drawText("Vertical $padding dp",x+12,402f,paint)
            canvas.drawBitmap(bitmap(view(base.copy(widgetPaddingVerticalDp=padding))),x+9,430f,null)
        }
        paint.color=0xFFBACCD5.toInt();paint.textSize=17f
        canvas.drawText("Valores negativos ampliam para cima/baixo; o host pode cortar o desenho. A área do LockStar continua igual.",24f,608f,paint)
        val out=File("build/outputs/widget-previews/progresso-circular.png");requireNotNull(out.parentFile).mkdirs()
        out.outputStream().use { assertTrue(sheet.compress(Bitmap.CompressFormat.PNG,100,it)) }
    }
}
