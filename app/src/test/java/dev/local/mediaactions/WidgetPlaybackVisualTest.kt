package dev.local.mediaactions

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Looper
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetPlaybackVisualTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val output get() = File("build/outputs/widget-previews").apply { mkdirs() }

    private fun view(style: WidgetAppearance, playback: WidgetPlaybackState): View {
        val root = WidgetRenderer.build(app, 42, style, SizeF(360f, 64f), playback = playback)
            .apply(app, FrameLayout(app))
        root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(64, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 360, 64)
        return root
    }

    private fun bitmap(view: View) = Bitmap.createBitmap(360, 64, Bitmap.Config.ARGB_8888)
        .also {
            assertEquals(360, view.width)
            assertEquals(64, view.height)
            view.draw(Canvas(it))
        }

    private fun pixels(bitmap: Bitmap) = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    private fun save(bitmap: Bitmap, name: String) = File(output, name).outputStream().use {
        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
    }

    @Test fun everyFamilyUsesDifferentPlayAndPauseArtworkWithOrWithoutShadow() {
        for (family in WidgetIconStyle.entries) for (shadow in listOf(false, true)) {
            val style = WidgetAppearance(iconScalePercent = 70, spacingDp = 32, iconStyle = family, iconShadow = shadow)
            val playing = bitmap(view(style, WidgetPlaybackState.PLAYING))
            val paused = bitmap(view(style, WidgetPlaybackState.PAUSED))
            assertFalse("$family / shadow=$shadow", pixels(playing).contentEquals(pixels(paused)))
            assertEquals(0, Color.alpha(playing.getPixel(0, 0)))
            assertEquals(0, Color.alpha(paused.getPixel(0, 0)))
        }
    }

    @Test fun exportPlayPauseAndUnknownStateComparisons() {
        val families = listOf(WidgetIconStyle.OUTLINE, WidgetIconStyle.LUCIDE, WidgetIconStyle.MATERIAL_ROUNDED,
            WidgetIconStyle.CIRCULAR, WidgetIconStyle.CHEVRONS, WidgetIconStyle.SQUARE)
        val names = listOf("Contorno", "Lucide", "Material", "Círculos", "Setas simples", "Quadrados")
        val states = listOf(WidgetPlaybackState.PLAYING, WidgetPlaybackState.PAUSED, WidgetPlaybackState.UNKNOWN)
        val sheet = Bitmap.createBitmap(1200, 140 + families.size * 132, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(0xFF101923.toInt())
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 29f }
        canvas.drawText("Play/Pause acompanha o estado do player", 24f, 44f, text)
        text.textSize = 17f
        text.color = 0xFFB6C5D3.toInt()
        canvas.drawText("Renderização Android/Skia. O azul representa o papel de parede.", 24f, 76f, text)
        for ((column, label) in listOf("Tocando → botão Pausa", "Pausado → botão Play", "Estado indisponível → neutro").withIndex()) {
            canvas.drawText(label, 28f + column * 400, 116f, text)
        }
        for ((row, family) in families.withIndex()) {
            val y = 140f + row * 132
            val wallpaper = Paint().apply {
                shader = LinearGradient(24f, y, 1176f, y + 112,
                    intArrayOf(0xFF245161.toInt(), 0xFF243049.toInt()), null, Shader.TileMode.CLAMP)
            }
            canvas.drawRect(24f, y, 1176f, y + 112, wallpaper)
            canvas.drawText(names[row], 36f, y + 24, text)
            for ((column, state) in states.withIndex()) {
                canvas.drawBitmap(bitmap(view(WidgetAppearance(iconScalePercent = 55, spacingDp = 37,
                    iconStyle = family, iconShadow = true), state)), 28f + column * 400, y + 36, null)
            }
        }
        save(sheet, "play-pause-sincronizado.png")
    }

    @Test fun exportNativeTouchFeedbackFrames() {
        val root = view(WidgetAppearance(iconScalePercent = 55, spacingDp = 37,
            iconStyle = WidgetIconStyle.CHEVRONS), WidgetPlaybackState.PAUSED)
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup()
        try {
        activity.get().setContentView(root, android.view.ViewGroup.LayoutParams(360, 64))
        root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(64, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 360, 64)
        val button = root.findViewById<ImageButton>(R.id.widget_toggle)
        val frames = mutableListOf<Bitmap>()
        frames += bitmap(root)
        button.background.setHotspot(button.width / 2f, button.height / 2f)
        button.isPressed = true
        repeat(10) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
            frames += bitmap(root)
        }
        button.isPressed = false
        repeat(14) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
            frames += bitmap(root)
        }
        // Export actual native frames; no synthetic morphing or simulated host popup.
        for ((index, frame) in frames.withIndex()) save(frame, "touch-frame-${index.toString().padStart(2, '0')}.png")
        assertEquals(0, Color.alpha(frames.first().getPixel(0, 0)))
        assertTrue("The pressed native ripple changes visible pixels",
            frames.drop(1).any { !pixels(it).contentEquals(pixels(frames.first())) })
        } finally { activity.close() }
    }
}
