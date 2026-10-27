package dev.local.mediaactions

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Looper
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IndependentBordersVisualTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val buttons = listOf(R.id.widget_previous, R.id.widget_toggle, R.id.widget_next)
    private val base = WidgetAppearance(iconScalePercent = 55, spacingDp = 37, iconStyle = WidgetIconStyle.CHEVRONS,
        borderWidthDp = 2, borderColor = 0xFF62CFF4.toInt(), buttonBorderColor = 0xFFFFD166.toInt())
    private fun view(style: WidgetAppearance, width: Int = 360, height: Int = 64): View {
        val root = WidgetRenderer.build(app, 42, style, SizeF(width.toFloat(), height.toFloat()), bindControls = false).apply(app, FrameLayout(app))
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, width, height); return root
    }
    private fun bitmap(view: View, onlyBackground: Boolean = false) = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
        if (onlyBackground) { view.background.setBounds(0, 0, view.width, view.height); view.background.draw(Canvas(it)) } else view.draw(Canvas(it))
    }
    private fun pixels(bitmap: Bitmap) = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
    private fun save(bitmap: Bitmap, name: String) {
        val dir = File("build/outputs/widget-previews").apply { mkdirs() }
        File(dir, name).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
    @Test fun fourCombinationsDrawIndependentBordersWithTransparentCentersAndGaps() {
        for (widget in listOf(false, true)) for (button in listOf(false, true)) {
            val style = base.copy(widgetBorder = widget, buttonBorder = button, buttonBorderWidthDp = 2)
            val root = view(style); val bg = bitmap(root, true)
            assertEquals(if (widget) style.borderColor else 0, bg.getPixel(180, 1))
            assertEquals(0, Color.alpha(bg.getPixel(180, 32)))
            for (id in buttons) {
                val child = root.findViewById<View>(id); val childBg = bitmap(child, true)
                assertEquals(if (button) style.buttonBorderColor else 0, childBg.getPixel(child.width / 2, 7))
                assertEquals(0, Color.alpha(childBg.getPixel(child.width / 2, child.height / 2)))
                assertEquals(0, Color.alpha(childBg.getPixel(0, 0)))
            }
            val image = bitmap(root)
            val g = WidgetGeometry.calculate(360f, 64f, style)
            assertEquals(0, Color.alpha(image.getPixel((360 - g.widthDp) / 2 + g.buttonDp + g.gapDp / 2, 32)))
        }
    }
    @Test fun changingOneBorderDoesNotChangeTheOtherOrTheIconGeometry() {
        val style = base.copy(widgetBorder = true, buttonBorder = true, touchFeedback = false)
        val original = view(style)
        val changedFrame = view(style.copy(borderWidthDp = 6, borderColor = Color.MAGENTA))
        val changedButtons = view(style.copy(buttonBorderWidthDp = 4, buttonBorderColor = Color.GREEN))
        val plain = view(style.copy(buttonBorder = false, widgetBorder = false))
        assertTrue(pixels(bitmap(original, true)).contentEquals(pixels(bitmap(changedButtons, true))))
        for (id in buttons) {
            val a = original.findViewById<View>(id); val b = changedFrame.findViewById<View>(id); val p = plain.findViewById<View>(id)
            assertTrue(pixels(bitmap(a, true)).contentEquals(pixels(bitmap(b, true))))
            assertEquals(Rect(p.left, p.top, p.right, p.bottom), Rect(a.left, a.top, a.right, a.bottom))
            assertEquals(p.paddingTop, a.paddingTop); assertEquals(p.paddingLeft, a.paddingLeft)
        }
        val whitePixels = pixels(bitmap(original)).count { it == Color.WHITE }
        assertTrue("Border tint must not tint the media glyphs", whitePixels > 20)
    }
    @Test fun reapplyCanRemoveEitherBorderAndItsTintWithoutRemovingTheOther() {
        val style = base.copy(widgetBorder = true, buttonBorder = true, touchFeedback = false)
        val actual = view(style)
        for (next in listOf(style.copy(buttonBorder = false), style.copy(widgetBorder = false), style.copy(widgetBorder = false, buttonBorder = false))) {
            WidgetRenderer.build(app, 42, next, SizeF(360f, 64f), bindControls = false).reapply(app, actual)
            // A changed InsetDrawable requests layout; the actual widget host performs this pass.
            actual.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(64, View.MeasureSpec.EXACTLY))
            actual.layout(0, 0, 360, 64)
            assertTrue(pixels(bitmap(view(next))).contentEquals(pixels(bitmap(actual))))
        }
    }
    @Test fun buttonBordersRespectTouchFeedbackAndRemainTransparentAtRest() {
        for (feedback in listOf(false, true)) {
            val activity = Robolectric.buildActivity(Activity::class.java).setup()
            try {
                val root = view(base.copy(buttonBorder = true, touchFeedback = feedback))
                activity.get().setContentView(root, android.view.ViewGroup.LayoutParams(360, 64))
                val button = root.findViewById<View>(R.id.widget_toggle)
                val before = pixels(bitmap(root))
                button.isPressed = true
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(120))
                val after = pixels(bitmap(root))
                assertEquals(!feedback, before.contentEquals(after))
            } finally { activity.close() }
        }
    }
    @Test fun widthsAndOpacityWorkAtMinimumSizeWithEitherFeedbackTheme() {
        for (feedback in listOf(false, true)) for (dark in listOf(false, true)) {
            val sizes = mutableListOf<Int>()
            for (width in listOf(1, 3, 8)) {
                val root = view(base.copy(buttonBorder = true, buttonBorderWidthDp = width, buttonBorderColor = 0x80FF0000.toInt(), touchFeedback = feedback, darkIcons = dark), 144, 48)
                val bg = bitmap(root.findViewById(R.id.widget_toggle), true)
                val maximumAlpha = pixels(bg).maxOf { Color.alpha(it) }
                assertTrue("Nominal alpha 128 with antialias coverage: $maximumAlpha", maximumAlpha in 120..128)
                if (width > 1) assertEquals(128, maximumAlpha)
                assertEquals(0, Color.alpha(bg.getPixel(24, 24)))
                sizes += pixels(bg).count { Color.alpha(it) > 0 }
            }
            assertTrue(sizes[0] < sizes[1] && sizes[1] < sizes[2])
        }
    }
    @Test fun exportFourIndependentBorderCombinations() {
        val sheet = Bitmap.createBitmap(900, 570, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet); canvas.drawColor(0xFF101820.toInt())
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 29f }
        canvas.drawText("Duas configurações independentes", 24f, 43f, text)
        text.textSize = 16f; text.color = 0xFFBACCD5.toInt()
        canvas.drawText("Renderização Android/Skia · mesmo tamanho, ícones e espaçamento nas quatro opções", 24f, 75f, text)
        val cases = listOf("Sem bordas" to base, "Só bordas dos botões" to base.copy(buttonBorder = true),
            "Só moldura do widget" to base.copy(widgetBorder = true), "As duas, com cores próprias" to base.copy(widgetBorder = true, buttonBorder = true))
        for ((i, item) in cases.withIndex()) {
            val x = 20f + i % 2 * 450; val y = 110f + i / 2 * 200
            canvas.drawRect(x, y, x + 410, y + 170, Paint().apply { color = 0xFF24495A.toInt() })
            text.color = Color.WHITE; text.textSize = 22f; canvas.drawText(item.first, x + 16, y + 34, text)
            canvas.drawBitmap(bitmap(view(item.second)), x + 25, y + 75, null)
        }
        text.color = 0xFFBACCD5.toInt(); text.textSize = 17f
        canvas.drawText("Botões: dourado, 1 dp · widget: azul, 2 dp · interiores transparentes", 24f, 544f, text)
        save(sheet, "bordas-independentes.png")
    }
    @Test fun exportTwoRealConfigurationSections() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val style = base.copy(widgetBorder = true, buttonBorder = true, widgetPaddingHorizontalDp = 24, widgetPaddingVerticalDp = 12)
            fun renderSection(target: WidgetBorderSection.Target): Bitmap {
                val form = WidgetBorderSection(activity.get(), style, target) { _, _, _ -> }.create()
                activity.get().setContentView(form)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300)); form.jumpDrawablesToCurrentState()
                form.measure(View.MeasureSpec.makeMeasureSpec(420, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST))
                form.layout(0, 0, 420, form.measuredHeight)
                return bitmap(form)
            }
            val buttonSection = renderSection(WidgetBorderSection.Target.BUTTONS)
            val widgetSection = renderSection(WidgetBorderSection.Target.WIDGET)
            val paddingForm = WidgetPaddingSection(activity.get(), style) { _, _ -> }.create()
            activity.get().setContentView(paddingForm)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300)); paddingForm.jumpDrawablesToCurrentState()
            paddingForm.measure(View.MeasureSpec.makeMeasureSpec(420, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST))
            paddingForm.layout(0, 0, 420, paddingForm.measuredHeight)
            val paddingSection = bitmap(paddingForm)
            val sheet = Bitmap.createBitmap(460, buttonSection.height + widgetSection.height + paddingSection.height + 120, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sheet); canvas.drawColor(0xFFF8F7F2.toInt())
            canvas.drawText("Bordas e padding do widget", 20f, 34f, Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 23f; color = 0xFF23313A.toInt() })
            canvas.drawBitmap(buttonSection, 20f, 54f, null)
            canvas.drawBitmap(widgetSection, 20f, 74f + buttonSection.height, null)
            canvas.drawBitmap(paddingSection, 20f, 94f + buttonSection.height + widgetSection.height, null)
            save(sheet, "bordas-independentes-config.png")
        } finally { activity.close() }
    }
}
