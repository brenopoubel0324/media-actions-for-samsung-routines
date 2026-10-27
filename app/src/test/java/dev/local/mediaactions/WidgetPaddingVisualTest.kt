package dev.local.mediaactions

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
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
@Config(sdk = [33], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetPaddingVisualTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val base = WidgetAppearance(fillWidth = true, iconScalePercent = 55, iconStyle = WidgetIconStyle.CHEVRONS,
        widgetBorder = true, borderWidthDp = 2, borderColor = 0xFF62CFF4.toInt(),
        buttonBorder = true, buttonBorderColor = 0xFFFFD166.toInt(), touchFeedback = false)
    private fun layout(root: View, width: Int, height: Int) {
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, width, height)
    }
    private fun view(style: WidgetAppearance, size: SizeF? = SizeF(360f, 96f)): View {
        val root = WidgetRenderer.build(app, 42, style, size, bindControls = false).apply(app, FrameLayout(app))
        layout(root, size?.width?.toInt() ?: 144, size?.height?.toInt() ?: 48); return root
    }
    private fun bitmap(root: View) = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also { root.draw(Canvas(it)) }
    private fun pixels(bitmap: Bitmap) = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
    @Test fun paddingInsetsTheControlsButTheWholeWidgetBorderStaysAtTheOuterEdge() {
        val root = view(base.copy(widgetPaddingHorizontalDp = 24, widgetPaddingVerticalDp = 12))
        assertEquals(24, root.paddingLeft); assertEquals(12, root.paddingTop)
        val row = root.findViewById<View>(R.id.widget_controls_row)
        assertEquals(Rect(24, 12, 336, 84), Rect(row.left, row.top, row.right, row.bottom))
        for (id in listOf(R.id.widget_previous, R.id.widget_toggle, R.id.widget_next)) {
            val child = root.findViewById<View>(id); assertEquals(72, child.width); assertEquals(72, child.height)
        }
        val image = bitmap(root)
        assertEquals(base.borderColor, image.getPixel(180, 0))
        assertEquals(0, Color.alpha(image.getPixel(180, 8)))
    }
    @Test fun unreportedHostSizeUsesConservativePaddingAndPreservesTouchAreas() {
        val root = view(base.copy(widgetPaddingHorizontalDp = 100, widgetPaddingVerticalDp = 100), null)
        assertEquals(0, root.paddingLeft); assertEquals(0, root.paddingTop)
        for (id in listOf(R.id.widget_previous, R.id.widget_toggle, R.id.widget_next)) {
            val child = root.findViewById<View>(id); assertEquals(48, child.width); assertEquals(48, child.height)
        }
    }
    @Test fun reapplyCanRemovePaddingWithoutChangingSavedBorderAppearance() {
        val root = view(base.copy(widgetPaddingHorizontalDp = 24, widgetPaddingVerticalDp = 12))
        WidgetRenderer.build(app, 42, base, SizeF(360f, 96f), bindControls = false).reapply(app, root)
        layout(root, 360, 96)
        assertEquals(0, root.paddingLeft); assertEquals(0, root.paddingTop)
        assertTrue(pixels(bitmap(view(base))).contentEquals(pixels(bitmap(root))))
    }
    @Test fun exportWholeWidgetPaddingComparison() {
        val sheet = Bitmap.createBitmap(900, 390, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet); canvas.drawColor(0xFF101820.toInt())
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 29f }
        canvas.drawText("Padding horizontal e vertical do widget", 24f, 43f, text)
        text.color = 0xFFBACCD5.toInt(); text.textSize = 16f
        canvas.drawText("Renderização Android/Skia · moldura e bordas dos botões têm configurações próprias", 24f, 76f, text)
        for (column in 0..1) {
            val x = 20f + column * 450
            canvas.drawRect(x, 110f, x + 410, 320f, Paint().apply { color = 0xFF24495A.toInt() })
            text.color = Color.WHITE; text.textSize = 22f
            canvas.drawText(if (column == 0) "Horizontal 0 · vertical 0" else "Horizontal 24 · vertical 12", x + 16, 145f, text)
            val style = if (column == 0) base else base.copy(widgetPaddingHorizontalDp = 24, widgetPaddingVerticalDp = 12)
            canvas.drawBitmap(bitmap(view(style)), x + 25, 184f, null)
        }
        text.color = 0xFFBACCD5.toInt(); text.textSize = 17f
        canvas.drawText("O padding ocupa espaço dentro da moldura; áreas de toque continuam com pelo menos 48 dp.", 24f, 363f, text)
        val dir = File("build/outputs/widget-previews").apply { mkdirs() }
        File(dir, "padding-widget.png").outputStream().use { assertTrue(sheet.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
