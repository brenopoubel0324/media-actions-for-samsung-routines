package dev.local.mediaactions

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w412dp-h892dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppAuditVisualTest {
    private fun render(root: View): Bitmap {
        fun layout() {
            root.measure(View.MeasureSpec.makeMeasureSpec(412, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(892, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 412, 892)
        }
        layout(); shadowOf(Looper.getMainLooper()).idle(); layout()
        fun settle(view: View) {
            view.jumpDrawablesToCurrentState()
            if (view is ViewGroup) for (i in 0 until view.childCount) settle(view.getChildAt(i))
        }
        settle(root)
        return Bitmap.createBitmap(412, 892, Bitmap.Config.ARGB_8888).also { root.draw(Canvas(it)) }
    }

    @Test fun exportPreservedInvalidDraftAndItsCorrection() {
        Robolectric.buildActivity(WidgetDefaultsActivity::class.java).setup().use { c ->
            c.get().findViewById<View>(R.id.settings_spacing).performClick()
            c.get().findViewById<EditText>(R.id.widget_padding_vertical_value).setText("-25")
            c.recreate()
            assertEquals("-25", c.get().findViewById<EditText>(R.id.widget_padding_vertical_value).text.toString())
            c.get().findViewById<View>(R.id.widget_configuration_save).performClick()
            assertFalse(c.get().isFinishing)
            val invalid = render(c.get().window.decorView)
            c.get().findViewById<EditText>(R.id.widget_padding_vertical_value).setText("-12")
            val corrected = render(c.get().window.decorView)
            val sheet = Bitmap.createBitmap(860, 972, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sheet); canvas.drawColor(Color.rgb(16, 24, 32))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 18f }
            canvas.drawText("Rascunho após recriar a tela", 20f, 34f, paint)
            canvas.drawText("Valor corrigido para salvar", 444f, 34f, paint)
            canvas.drawBitmap(invalid, 12f, 56f, null); canvas.drawBitmap(corrected, 436f, 56f, null)
            val file = File("build/outputs/widget-previews/auditoria-configuracoes.png")
            requireNotNull(file.parentFile).mkdirs()
            file.outputStream().use { assertTrue(sheet.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            c.get().findViewById<View>(R.id.widget_configuration_save).performClick()
            assertEquals(-12, WidgetStyleStore.load(c.get()).widgetPaddingVerticalDp)
        }
    }
}
