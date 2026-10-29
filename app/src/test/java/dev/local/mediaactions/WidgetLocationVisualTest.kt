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
import android.view.View
import android.view.ViewGroup
import android.widget.Spinner
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w412dp-h892dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetLocationVisualTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private fun widget(category: Int = 0): Int {
        val manager = AppWidgetManager.getInstance(app)
        val id = shadowOf(manager).createWidget(MediaWidgetProvider::class.java, R.layout.widget_media_controls)
        shadowOf(manager).addBoundWidget(id, AppWidgetProviderInfo().apply { provider = ComponentName(app, MediaWidgetProvider::class.java) })
        manager.updateAppWidgetOptions(id, Bundle().apply {
            if (category != 0) putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, category)
            putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(android.util.SizeF(300f, 64f)))
        })
        return id
    }
    private fun render(root: View, width: Int = 412, height: Int = 892): Bitmap {
        fun layout() {
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, width, height)
        }
        layout(); shadowOf(android.os.Looper.getMainLooper()).idle(); layout()
        fun settle(view: View) {
            view.jumpDrawablesToCurrentState()
            if (view is ViewGroup) for (i in 0 until view.childCount) settle(view.getChildAt(i))
        }
        settle(root)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { root.draw(Canvas(it)) }
    }
    private fun save(bitmap: Bitmap, name: String) {
        val file = File("build/outputs/widget-previews/$name"); requireNotNull(file.parentFile).mkdirs()
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    @Test fun exportLocationChooserAndPerWidgetEditor() {
        val lock = widget()
        WidgetLocationStore.save(app, lock, WidgetLocation.LOCK_SCREEN)
        widget(AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
        widget()
        val frames = mutableListOf<Bitmap>()
        Robolectric.buildActivity(MainActivity::class.java).setup().use { c ->
            c.get().findViewById<View>(R.id.widget_edit_existing).performClick()
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            assertEquals(3, dialog.listView.adapter.count)
            val home = render(c.get().window.decorView)
            val chooser = render(dialog.window!!.decorView, 380, 360)
            Canvas(home).apply {
                drawColor(0x88000000.toInt())
                drawBitmap(chooser, 16f, 245f, null)
            }
            frames += home
        }
        val intent = Intent(app, WidgetConfigureActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, lock)
        Robolectric.buildActivity(WidgetConfigureActivity::class.java, intent).setup().use { c ->
            c.get().findViewById<View>(R.id.settings_location).performClick()
            assertEquals(WidgetLocation.LOCK_SCREEN.ordinal, c.get().findViewById<Spinner>(R.id.widget_location_choice).selectedItemPosition)
            frames += render(c.get().window.decorView)
        }
        val sheet = Bitmap.createBitmap(860, 972, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet); canvas.drawColor(Color.rgb(16, 24, 32))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 19f }
        listOf("Escolha pelo local", "Identificação por widget").forEachIndexed { i, title ->
            canvas.drawText(title, 20f + i * 424, 34f, paint)
            canvas.drawBitmap(frames[i], 12f + i * 424, 56f, null)
        }
        save(sheet, "widgets-por-local.png")
    }
}
