package dev.local.mediaactions

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.widget.CompoundButton
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w412dp-h892dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsUxVisualTest {
    private fun render(activity: Activity): Bitmap {
        val root = activity.window.decorView
        root.measure(View.MeasureSpec.makeMeasureSpec(412, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(892, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 412, 892)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        root.measure(View.MeasureSpec.makeMeasureSpec(412, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(892, View.MeasureSpec.EXACTLY)); root.layout(0,0,412,892)
        fun settle(view: View) {
            view.jumpDrawablesToCurrentState()
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) settle(view.getChildAt(i))
        }
        settle(root)
        return Bitmap.createBitmap(412,892,Bitmap.Config.ARGB_8888).also { root.draw(Canvas(it)) }
    }
    private fun save(bitmap: Bitmap, name: String) {
        val file = File("build/outputs/widget-previews/$name"); requireNotNull(file.parentFile).mkdirs()
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
    }
    @Test fun exportHomeEditorAndIndependentProgressSwitch() {
        val images = mutableListOf<Bitmap>()
        Robolectric.buildActivity(MainActivity::class.java).setup().use { c -> images += render(c.get()) }
        Robolectric.buildActivity(WidgetDefaultsActivity::class.java).setup().use { c ->
            images += render(c.get())
            c.get().findViewById<View>(R.id.settings_playback).performClick()
            val progress = c.get().findViewById<CompoundButton>(R.id.widget_progress_ring)
            assertTrue(progress.text.toString().contains("progresso"))
            progress.performClick()
            assertTrue(c.get().findViewById<android.widget.RadioButton>(R.id.widget_preview_playing).isChecked)
            assertFalse(c.get().findViewById<android.widget.RadioButton>(R.id.widget_preview_paused).isChecked)
            images += render(c.get())
            progress.performClick()
            val off = render(c.get())
            save(off, "configuracoes-progresso-desligado.png")
            val comparison = Bitmap.createBitmap(860, 960, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(comparison); canvas.drawColor(0xFF101820.toInt())
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE; textSize=20f }
            canvas.drawText("Progresso ligado",20f,32f,paint); canvas.drawText("Progresso desligado",446f,32f,paint)
            canvas.drawBitmap(images.last(),12f,48f,null); canvas.drawBitmap(off,436f,48f,null)
            save(comparison,"configuracoes-progresso-opcoes.png")
            assertFalse(WidgetStyleStore.load(RuntimeEnvironment.getApplication()).progressRing)
        }
        val sheet = Bitmap.createBitmap(1284,972,Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet); canvas.drawColor(Color.rgb(16,24,32))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE; textSize=20f }
        val titles = listOf("Início", "Editor por grupos", "Progresso: ligar ou desligar")
        for(i in images.indices) {
            val x=12f+i*424
            canvas.drawText(titles[i],x+8,34f,paint);canvas.drawBitmap(images[i],x,56f,null)
        }
        save(sheet,"configuracoes-organizadas.png")
        images.forEachIndexed { i,image -> save(image,listOf("configuracoes-inicio.png","configuracoes-widget.png","configuracoes-progresso.png")[i]) }
    }
    @Test
    @Config(qualifiers = "pt-rBR-w412dp-h892dp-night-mdpi")
    fun exportDarkModeAndKeepSaveVisible() {
        Robolectric.buildActivity(WidgetDefaultsActivity::class.java).setup().use { c ->
            c.get().findViewById<View>(R.id.settings_spacing).performClick()
            val bitmap=render(c.get())
            assertEquals(0xFF101619.toInt(),c.get().getColor(R.color.settings_background))
            assertTrue(c.get().findViewById<View>(R.id.widget_configuration_save).isShown)
            save(bitmap,"configuracoes-escuro.png")
        }
    }
}
