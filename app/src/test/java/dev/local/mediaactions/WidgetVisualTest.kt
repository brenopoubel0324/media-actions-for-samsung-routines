package dev.local.mediaactions

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.os.Looper
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
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
        assertEquals(WidgetIconStyle.entries.size, fingerprints.size)
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

    @Test fun smallIconsHaveLessInkAtTheSameSpacing() {
        fun ink(bitmap: Bitmap): Int {
            var count = 0
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 127) count++
            }
            return count
        }
        for (family in listOf(WidgetIconStyle.LUCIDE, WidgetIconStyle.PHOSPHOR_THIN, WidgetIconStyle.MATERIAL_ROUNDED)) {
            val large = ink(render(360, 64, WidgetAppearance(iconScalePercent = 100, iconStyle = family, fillWidth = true)))
            val small = ink(render(360, 64, WidgetAppearance(iconScalePercent = 35, iconStyle = family, fillWidth = true)))
            assertTrue(small > 0)
            assertTrue(small < large / 2)
        }
    }

    @Test fun exportUnpublishedFamilyAndSizePreviews() {
        val output = File("build/outputs/widget-previews").apply { mkdirs() }
        val families = listOf(WidgetIconStyle.LUCIDE, WidgetIconStyle.PHOSPHOR_THIN, WidgetIconStyle.MATERIAL_ROUNDED)
        val names = listOf("Lucide / traço arredondado", "Phosphor Thin / traço fino", "Material Symbols Rounded")
        fun export(fileName: String, spacingComparison: Boolean) {
        val sheet = Bitmap.createBitmap(1248, 736, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.rgb(13, 18, 27))
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 34f }
        canvas.drawText(if (spacingComparison) "Pequenos, com mais espaço" else "Outras famílias + ícones menores", 32f, 52f, text)
        text.color = Color.rgb(162, 176, 194)
        text.textSize = 18f
        canvas.drawText(if (spacingComparison) "Tamanho em 40%; só o espaçamento muda. Fundo transparente."
            else "Mesma largura e espaçamento; só o tamanho dos ícones muda.", 32f, 84f, text)
        val columns = if (spacingComparison) listOf("40% / compacto", "40% / espaço de 64 dp", "40% / largura total")
            else listOf("100% / largura total", "50% / largura total", "30% / largura total")
        for ((column, name) in columns.withIndex()) canvas.drawText(name, 48f + column * 400, 120f, text)
        for ((row, iconStyle) in families.withIndex()) {
            val top = 144f + row * 192
            val wallpaper = Paint().apply {
                shader = LinearGradient(32f, top, 1216f, top + 152,
                    intArrayOf(0xFF1C4B60.toInt(), 0xFF242A47.toInt()), null, Shader.TileMode.CLAMP)
            }
            canvas.drawRect(32f, top, 1216f, top + 152, wallpaper)
            text.color = Color.rgb(190, 208, 221)
            canvas.drawText(names[row], 48f, top + 28, text)
            for (column in 0..2) {
                val style = if (spacingComparison) WidgetAppearance(iconScalePercent = 40, iconStyle = iconStyle,
                    spacingDp = if (column == 1) 64 else 0, fillWidth = column == 2)
                    else WidgetAppearance(iconScalePercent = listOf(100, 50, 30)[column], iconStyle = iconStyle, fillWidth = true)
                val bitmap = render(360, 64, style)
                canvas.drawBitmap(bitmap, 44f + column * 400, top + 66, null)
                File(output, "${fileName.removeSuffix(".png")}-${iconStyle.name.lowercase()}-${column}.png")
                    .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        File(output, fileName).outputStream().use {
            assertTrue(sheet.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        }
        export("familias-e-tamanhos.png", false)
        export("pequenos-e-espacados.png", true)
    }

    @Test fun exportDistinctSilhouettePreviews() {
        val output = File("build/outputs/widget-previews").apply { mkdirs() }
        val styles = listOf(WidgetIconStyle.OUTLINE, WidgetIconStyle.CIRCULAR, WidgetIconStyle.CHEVRONS,
            WidgetIconStyle.DOUBLE_TRIANGLES, WidgetIconStyle.SQUARE)
        val names = listOf("Atual / mesma silhueta das versões anteriores", "A / Círculos",
            "B / Setas simples", "C / Triângulos duplos", "D / Quadrados arredondados")
        fun export(fileName: String, tinySizes: Boolean) {
            val rows = if (tinySizes) styles.drop(1) else styles
            val labels = if (tinySizes) names.drop(1) else names
            val sheet = Bitmap.createBitmap(1248, 144 + rows.size * 168, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sheet)
            canvas.drawColor(Color.rgb(13, 18, 27))
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 32f }
            canvas.drawText(if (tinySizes) "Pequenos e afastados" else "Agora, desenhos realmente diferentes", 32f, 48f, text)
            text.textSize = 17f
            text.color = Color.rgb(170, 187, 204)
            canvas.drawText("Fundo transparente; o botão central continua sendo Play/Pause.", 32f, 80f, text)
            val scales = if (tinySizes) listOf(50, 35, 20) else listOf(85, 40)
            val columnWidth = if (tinySizes) 400 else 600
            for ((col, scale) in scales.withIndex()) {
                canvas.drawText("${scale}% / largura total", 48f + col * columnWidth, 120f, text)
            }
            for ((row, style) in rows.withIndex()) {
                val top = 144f + row * 168
                val wallpaper = Paint().apply {
                    shader = LinearGradient(32f, top, 1216f, top + 136,
                        intArrayOf(0xFF1C4B60.toInt(), 0xFF242A47.toInt()), null, Shader.TileMode.CLAMP)
                }
                canvas.drawRect(32f, top, 1216f, top + 136, wallpaper)
                text.color = Color.rgb(190, 208, 221)
                canvas.drawText(labels[row], 48f, top + 25, text)
                for ((col, scale) in scales.withIndex()) {
                    val bitmap = render(360, 64, WidgetAppearance(iconScalePercent = scale, iconStyle = style, fillWidth = true))
                    val x = if (tinySizes) 44f + col * 400 else 104f + col * 600
                    canvas.drawBitmap(bitmap, x, top + 56, null)
                }
            }
            File(output, fileName).outputStream().use {
                assertTrue(sheet.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        }
        export("desenhos-diferentes.png", false)
        export("pequenos-distintos.png", true)
    }

    @Test
    @Config(qualifiers = "w420dp-h960dp-mdpi")
    fun exportTheDedicatedWidgetConfigurationPage() {
        val manager = AppWidgetManager.getInstance(app)
        val id = shadowOf(manager).createWidget(MediaWidgetProvider::class.java, R.layout.widget_media_controls)
        shadowOf(manager).addBoundWidget(id, AppWidgetProviderInfo().apply {
            provider = ComponentName(app, MediaWidgetProvider::class.java)
            initialLayout = R.layout.widget_media_controls
        })
        WidgetStyleStore.save(app, WidgetAppearance(iconScalePercent = 40,
            iconStyle = WidgetIconStyle.CHEVRONS, fillWidth = true), id)
        val intent = Intent(app, WidgetConfigureActivity::class.java)
            .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        Robolectric.buildActivity(WidgetConfigureActivity::class.java, intent).use { controller ->
            val activity = controller.setup().get()
            assertFalse(activity.isFinishing)
            val root = (activity.findViewById<android.view.ViewGroup>(android.R.id.content)).getChildAt(0)
            // The preview refits itself when its container gets a width; draw the next layout pass.
            repeat(2) {
                root.forceLayout()
                root.measure(View.MeasureSpec.makeMeasureSpec(420, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, 420, 960)
                shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100))
            }
            assertTrue(root.findViewById<View>(R.id.widget_next).width > 0)
            val bitmap = Bitmap.createBitmap(420, 960, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(250, 250, 250))
            root.draw(canvas)
            val output = File("build/outputs/widget-previews").apply { mkdirs() }
            File(output, "widget-configuration.png").outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        }
    }

    @Test fun optionalShadowsPreserveArtworkAndTransparentBackground() {
        val plain = render(360, 64, WidgetAppearance(iconScalePercent = 55, spacingDp = 37))
        val shaded = render(360, 64, WidgetAppearance(iconScalePercent = 55, spacingDp = 37, iconShadow = true))
        assertEquals(0, Color.alpha(shaded.getPixel(0, 0)))
        var whites = 0
        var darkShadow = 0
        for (y in 0 until shaded.height) for (x in 0 until shaded.width) {
            val pixel = shaded.getPixel(x, y)
            if (pixel == Color.WHITE) whites++
            if (Color.alpha(pixel) > 0 && Color.red(pixel) < 80 && Color.green(pixel) < 80 &&
                Color.blue(pixel) < 80 && Color.alpha(plain.getPixel(x, y)) == 0) darkShadow++
        }
        assertTrue("Shadow must not replace the white artwork", whites > 20)
        assertTrue("Shadow must survive ImageView tint and software rendering", darkShadow > 20)
        val plainPixels = IntArray(plain.width * plain.height)
        val shadedPixels = IntArray(plain.width * plain.height)
        plain.getPixels(plainPixels, 0, plain.width, 0, 0, plain.width, plain.height)
        shaded.getPixels(shadedPixels, 0, shaded.width, 0, 0, shaded.width, shaded.height)
        assertFalse(plainPixels.contentEquals(shadedPixels))
    }

    @Test fun exportShadowComparisonOnLightWallpaper() {
        val sheet = Bitmap.createBitmap(900, 360, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.rgb(245, 244, 239))
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(34, 45, 55); textSize = 26f }
        canvas.drawText("Sombra opcional / fundo transparente", 24f, 44f, text)
        text.textSize = 20f
        canvas.drawText("Sem sombra", 40f, 110f, text)
        canvas.drawText("Com sombra suave", 490f, 110f, text)
        val wallpaper = Paint().apply {
            shader = LinearGradient(0f, 130f, 900f, 300f,
                intArrayOf(0xFFBFCDC8.toInt(), 0xFFF3EEE3.toInt()), null, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(24f, 130f, 876f, 290f, wallpaper)
        for ((index, shadow) in listOf(false, true).withIndex()) {
            canvas.drawBitmap(render(360, 64, WidgetAppearance(iconScalePercent = 55,
                iconStyle = WidgetIconStyle.CHEVRONS, spacingDp = 37, iconShadow = shadow)),
                48f + index * 450, 178f, null)
        }
        text.textSize = 17f
        canvas.drawText("Mesmos ícones, tamanho e espaçamento; só a sombra muda.", 24f, 332f, text)
        val output = File("build/outputs/widget-previews").apply { mkdirs() }
        File(output, "sombra-opcional.png").outputStream().use {
            assertTrue(sheet.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test
    @Config(qualifiers = "w420dp-h960dp-mdpi")
    fun exportExactSpacingControlsAndVisibleAppEditEntry() {
        val manager = AppWidgetManager.getInstance(app)
        val id = shadowOf(manager).createWidget(MediaWidgetProvider::class.java, R.layout.widget_media_controls)
        shadowOf(manager).addBoundWidget(id, AppWidgetProviderInfo().apply {
            provider = ComponentName(app, MediaWidgetProvider::class.java)
            initialLayout = R.layout.widget_media_controls
        })
        manager.updateAppWidgetOptions(id, android.os.Bundle().apply {
            putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(360f, 56f)))
        })
        WidgetStyleStore.save(app, WidgetAppearance(iconScalePercent = 35, spacingDp = 37,
            iconStyle = WidgetIconStyle.CHEVRONS, iconShadow = true), id)
        val output = File("build/outputs/widget-previews").apply { mkdirs() }
        fun layout(root: View) {
            repeat(2) {
                root.forceLayout()
                root.measure(View.MeasureSpec.makeMeasureSpec(420, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, 420, 960)
                shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100))
            }
        }
        fun export(root: View, name: String) {
            val bitmap = Bitmap.createBitmap(420, 960, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(250, 250, 250))
            root.draw(canvas)
            File(output, name).outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        }
        val intent = Intent(app, WidgetConfigureActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        Robolectric.buildActivity(WidgetConfigureActivity::class.java, intent).use {
            val root = (it.setup().get().findViewById<android.view.ViewGroup>(android.R.id.content)).getChildAt(0)
            layout(root)
            export(root, "configuracao-externa.png")
            val scroll = root.findViewById<android.widget.ScrollView>(R.id.widget_configuration_scroll)
            scroll.scrollTo(0, scroll.getChildAt(0).height - scroll.height)
            layout(root)
            assertEquals("37", root.findViewById<android.widget.EditText>(R.id.widget_spacing_value).text.toString())
            val visible = android.graphics.Rect()
            assertTrue("The preview stays visible while the spacing controls are scrolled",
                root.findViewById<View>(R.id.widget_next).getGlobalVisibleRect(visible))
            assertTrue(visible.top >= 0 && visible.bottom <= 960)
            export(root, "espacamento-preciso.png")
        }
        Robolectric.buildActivity(MainActivity::class.java).use {
            val root = (it.setup().get().findViewById<android.view.ViewGroup>(android.R.id.content)).getChildAt(0)
            layout(root)
            assertNotNull(root.findViewById<View>(R.id.widget_edit_existing))
            export(root, "editar-widget-no-app.png")
        }
    }
}
