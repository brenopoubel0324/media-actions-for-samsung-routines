package dev.local.mediaactions

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
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

/** Native Skia rendering of the actual RemoteViews, not a mock-up or a phone screenshot. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetVisualTest {
    private val app get() = RuntimeEnvironment.getApplication()

    private fun render(width: Int, height: Int, style: WidgetAppearance = WidgetAppearance()): Bitmap {
        val view = WidgetRenderer.build(app, 42, style, SizeF(width.toFloat(), height.toFloat()))
            .apply(app, FrameLayout(app))
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    @Test fun canvasAndSpacesBetweenButtonsRemainFullyTransparent() {
        val style = WidgetAppearance(spacingDp = 12)
        val bitmap = render(280, 72, style)
        val geometry = WidgetGeometry.calculate(280f, 72f, style)
        val left = (bitmap.width - geometry.widthDp) / 2
        assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
        assertEquals(0, Color.alpha(bitmap.getPixel(bitmap.width - 1, bitmap.height - 1)))
        assertEquals(0, Color.alpha(bitmap.getPixel(left + geometry.buttonDp + geometry.gapDp / 2, 36)))
        var visible = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if (Color.alpha(bitmap.getPixel(x, y)) > 0) visible++
        }
        assertTrue(visible > 100)
        assertTrue("No filled panel behind the icons", visible < bitmap.width * bitmap.height / 3)
    }

    @Test fun darkColorAndLargerIconSettingChangeActualRenderedPixels() {
        val white = render(240, 80)
        val dark = render(240, 80, WidgetAppearance(darkIcons = true))
        val sample = (0 until white.height).flatMap { y -> (0 until white.width).map { x -> x to y } }
            .first { (x, y) -> white.getPixel(x, y) == Color.WHITE }
        assertEquals(Color.rgb(26, 29, 34), dark.getPixel(sample.first, sample.second))
        fun visible(bitmap: Bitmap): Int {
            var count = 0
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 127) count++
            }
            return count
        }
        assertTrue(visible(render(240, 80, WidgetAppearance(iconScalePercent = 135))) >
            visible(render(240, 80, WidgetAppearance(iconScalePercent = 75))))
    }

    @Test fun allIconStylesHaveDistinctTransparentArtworkAndSupportDarkColor() {
        val fingerprints = mutableSetOf<Int>()
        for (style in WidgetIconStyle.entries) {
            val white = render(360, 80, WidgetAppearance(iconStyle = style))
            val dark = render(360, 80, WidgetAppearance(darkIcons = true, iconStyle = style))
            assertEquals(0, Color.alpha(white.getPixel(0, 0)))
            val pixels = IntArray(white.width * white.height)
            white.getPixels(pixels, 0, white.width, 0, 0, white.width, white.height)
            fingerprints += pixels.contentHashCode()
            val opaque = pixels.indexOfFirst { it == Color.WHITE }
            assertTrue(opaque >= 0)
            assertEquals(Color.rgb(26, 29, 34), dark.getPixel(opaque % white.width, opaque / white.width))
        }
        assertEquals(3, fingerprints.size)
    }

    @Test fun largerSpacingAndFullWidthMoveTheVisibleIconsApart() {
        fun visibleWidth(bitmap: Bitmap): Int {
            var first = bitmap.width
            var last = 0
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 127) {
                    first = minOf(first, x)
                    last = maxOf(last, x)
                }
            }
            return last - first
        }
        val compact = visibleWidth(render(360, 56))
        val manual = visibleWidth(render(360, 56, WidgetAppearance(spacingDp = 64)))
        val full = visibleWidth(render(360, 56, WidgetAppearance(fillWidth = true)))
        assertTrue(compact < manual)
        assertTrue(manual < full)
    }

    @Test fun exportNativeStyleAndSpacingComparison() {
        val output = File("build/outputs/widget-previews").apply { mkdirs() }
        val sheet = Bitmap.createBitmap(1248, 736, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.rgb(13, 18, 27))
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 34f }
        canvas.drawText("Icon styles + more spacing", 32f, 52f, text)
        text.color = Color.rgb(162, 176, 194)
        text.textSize = 18f
        canvas.drawText("Actual Android rendering / transparent widget / 360 x 56 dp", 32f, 84f, text)
        val columns = listOf("Compact / 0 dp", "Wide / 64 dp", "Full width / automatic")
        for ((column, name) in columns.withIndex()) canvas.drawText(name, 48f + column * 400, 120f, text)
        val names = listOf("Outline / modern", "Rounded / filled", "Classic")
        for ((row, iconStyle) in WidgetIconStyle.entries.withIndex()) {
            val top = 144f + row * 192
            val wallpaper = Paint().apply {
                shader = LinearGradient(32f, top, 1216f, top + 152,
                    intArrayOf(0xFF1C4B60.toInt(), 0xFF242A47.toInt()), null, Shader.TileMode.CLAMP)
            }
            canvas.drawRect(32f, top, 1216f, top + 152, wallpaper)
            text.color = Color.rgb(190, 208, 221)
            canvas.drawText(names[row], 48f, top + 28, text)
            for (column in 0..2) {
                val style = WidgetAppearance(iconStyle = iconStyle,
                    spacingDp = if (column == 1) 64 else 0, fillWidth = column == 2)
                val bitmap = render(360, 56, style)
                canvas.drawBitmap(bitmap, 44f + column * 400, top + 66, null)
                File(output, "widget-${iconStyle.name.lowercase()}-${column}.png")
                    .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        File(output, "widget-preview.png").outputStream().use {
            assertTrue(sheet.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
