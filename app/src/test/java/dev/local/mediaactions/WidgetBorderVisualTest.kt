package dev.local.mediaactions

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Rect
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetBorderVisualTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private fun view(style: WidgetAppearance, width: Int = 360, height: Int = 64): View {
        val view = WidgetRenderer.build(app, 42, style, SizeF(width.toFloat(), height.toFloat()), bindControls = false)
            .apply(app, FrameLayout(app))
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        return view
    }
    private fun bitmap(view: View, backgroundOnly: Boolean = false) = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
        if (backgroundOnly) { view.background.setBounds(0, 0, view.width, view.height); view.background.draw(Canvas(it)) } else view.draw(Canvas(it))
    }
    private fun pixels(bitmap: Bitmap) = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
    private fun save(bitmap: Bitmap, name: String) {
        val dir = File("build/outputs/widget-previews").apply { mkdirs() }
        File(dir, name).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
    @Test fun singleBorderReachesTheWholeWidgetAndKeepsItsInsideAndGapsTransparent() {
        val style = WidgetAppearance(iconScalePercent = 55, spacingDp = 37, widgetBorder = true, borderColor = Color.CYAN, borderWidthDp = 2)
        val framed = bitmap(view(style))
        assertEquals(Color.CYAN, framed.getPixel(1, 32))
        assertEquals(0, Color.alpha(framed.getPixel(12, 32)))
        assertEquals(0, Color.alpha(framed.getPixel(0, 0)))
        val geometry = WidgetGeometry.calculate(360f, 64f, style)
        val gap = (360 - geometry.widthDp) / 2 + geometry.buttonDp + geometry.gapDp / 2
        assertEquals(0, Color.alpha(framed.getPixel(gap, 32)))
        val background = bitmap(view(style), backgroundOnly = true)
        assertEquals(0, Color.alpha(background.getPixel(180, 32)))
    }
    @Test fun widthAndArgbColorAreActuallyRenderedInTheNativeDrawable() {
        val counts = mutableListOf<Int>()
        for (width in listOf(1, 3, 8)) {
            val bg = bitmap(view(WidgetAppearance(widgetBorder = true, borderWidthDp = width, borderColor = 0xFF123456.toInt())), true)
            assertEquals(0xFF123456.toInt(), bg.getPixel(180, 0))
            counts += pixels(bg).count { Color.alpha(it) != 0 }
        }
        assertTrue(counts[0] < counts[1] && counts[1] < counts[2])
        val translucent = bitmap(view(WidgetAppearance(widgetBorder = true, borderWidthDp = 3, borderColor = 0x80123456.toInt())), true)
        assertEquals(128, Color.alpha(translucent.getPixel(180, 1)))
    }
    @Test fun borderDoesNotMoveControlsOrChangeTheIconsAndCanBeRemovedOnReapply() {
        val style = WidgetAppearance(iconScalePercent = 55, spacingDp = 37, touchFeedback = false)
        val plain = view(style); val framed = view(style.copy(widgetBorder = true))
        for (id in listOf(R.id.widget_previous, R.id.widget_toggle, R.id.widget_next)) {
            val a = plain.findViewById<View>(id); val b = framed.findViewById<View>(id)
            assertEquals(Rect(a.left, a.top, a.right, a.bottom), Rect(b.left, b.top, b.right, b.bottom))
            val bg = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
            b.background.setBounds(0, 0, b.width, b.height)
            b.background.draw(Canvas(bg))
            assertTrue(pixels(bg).all { Color.alpha(it) == 0 })
        }
        WidgetRenderer.build(app, 42, style, SizeF(360f, 64f), bindControls = false).reapply(app, framed)
        assertTrue(pixels(bitmap(plain)).contentEquals(pixels(bitmap(framed))))
    }
    @Test fun borderScalesAtMinimumSizeAndWithDifferentIconFamilies() {
        for (family in WidgetIconStyle.entries) for (width in listOf(1, 8)) {
            val bg = bitmap(view(WidgetAppearance(iconStyle = family, widgetBorder = true, borderWidthDp = width), 144, 48), true)
            assertTrue(Color.alpha(bg.getPixel(72, 0)) > 0)
            assertEquals(0, Color.alpha(bg.getPixel(72, 24)))
        }
    }
    @Test fun exportShadowVersusSingleWidgetFrameComparison() {
        val sheet = Bitmap.createBitmap(1200, 770, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet); canvas.drawColor(0xFF101820.toInt())
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 30f }
        canvas.drawText("Uma moldura para o widget inteiro", 24f, 44f, text)
        text.color = 0xFFBACCD5.toInt(); text.textSize = 17f
        canvas.drawText("Renderização Android/Skia · ícones, tamanho e espaçamento iguais em cada linha", 24f, 76f, text)
        text.color = Color.WHITE; text.textSize = 23f
        for ((i, title) in listOf("Sem efeito", "Sombra atual", "Borda do widget").withIndex()) canvas.drawText(title, 24f + i * 400, 120f, text)
        for (row in 0..2) {
            val top = 148f + row * 180
            val background = when (row) {
                1 -> intArrayOf(0xFFDFE8E5.toInt(), 0xFFF4EBDC.toInt())
                else -> intArrayOf(0xFF244D61.toInt(), 0xFF172333.toInt())
            }
            canvas.drawRect(12f, top, 1188f, top + 152, Paint().apply {
                shader = LinearGradient(12f, top, 1188f, top + 152, background, null, Shader.TileMode.CLAMP)
            })
            text.color = if (row == 1) 0xFF27333E.toInt() else Color.WHITE; text.textSize = 17f
            canvas.drawText(when (row) { 0 -> "Borda branca · 1 dp"; 1 -> "Borda escura · 1 dp"; else -> "Cor personalizada · 3 dp" }, 24f, top + 29, text)
            val base = WidgetAppearance(iconScalePercent = 55, spacingDp = 37, iconStyle = WidgetIconStyle.CHEVRONS, darkIcons = row == 1)
            val borderColor = when (row) { 1 -> 0xFF1A1D22.toInt(); 2 -> 0xFF62CFF4.toInt(); else -> Color.WHITE }
            for (column in 0..2) {
                val style = base.copy(iconShadow = column == 1, widgetBorder = column == 2,
                    borderWidthDp = if (row == 2) 3 else 1, borderColor = borderColor)
                canvas.drawBitmap(bitmap(view(style)), 20f + column * 400, top + 64, null)
            }
        }
        text.color = 0xFFBACCD5.toInt(); text.textSize = 18f
        canvas.drawText("A moldura não tem preenchimento; a cor atrás dos controles é o papel de parede.", 24f, 737f, text)
        save(sheet, "borda-widget-comparacao.png")
    }
    @Test fun exportActualBorderConfigurationControls() {
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup()
        try {
            val form = WidgetBorderSection(activity.get(), WidgetAppearance(widgetBorder = true, borderWidthDp = 3, borderColor = 0xFF62CFF4.toInt())) { _, _, _ -> }.create()
            activity.get().setContentView(form)
            shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300))
            form.jumpDrawablesToCurrentState()
            form.measure(View.MeasureSpec.makeMeasureSpec(420, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST))
            form.layout(0, 0, 420, form.measuredHeight)
            val sheet = Bitmap.createBitmap(460, form.height + 80, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sheet); canvas.drawColor(0xFFF8F7F2.toInt())
            canvas.drawText("Controles reais da configuração", 20f, 34f, Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 23f; color = 0xFF23313A.toInt() })
            canvas.save(); canvas.translate(20f, 54f); form.draw(canvas); canvas.restore()
            save(sheet, "borda-widget-configuracao.png")
        } finally { activity.close() }
    }
}
