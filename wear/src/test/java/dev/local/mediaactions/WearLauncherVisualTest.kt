package dev.local.mediaactions

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WearLauncherVisualTest {
    @Test fun exportRealApkIconsAndLabelsWithoutPretendingToCaptureWbrOrTheSamsungLauncher() {
        val app = RuntimeEnvironment.getApplication()
        val entries = app.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(app.packageName), 0).sortedBy { it.activityInfo.name }
        assertEquals(3, entries.size)
        val bitmap = Bitmap.createBitmap(1050, 410, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFF101820.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 32f }
        canvas.drawText("Media Actions · três entradas para o remapeador", 30f, 50f, paint)
        paint.textSize = 17f; paint.color = 0xFFB8C9D2.toInt()
        canvas.drawText("Ícones e nomes reais do APK · não é captura do launcher ou do Wear Button Remapper", 30f, 83f, paint)
        for ((i, entry) in entries.withIndex()) {
            val x = 175 + 350 * i
            val icon = entry.loadIcon(app.packageManager)
            icon.setBounds(x - 56, 140, x + 56, 252); icon.draw(canvas)
            paint.color = Color.WHITE; paint.textSize = 25f; paint.textAlign = Paint.Align.CENTER
            canvas.drawText(entry.loadLabel(app.packageManager).toString(), x.toFloat(), 302f, paint)
        }
        paint.color = 0xFFB8C9D2.toInt(); paint.textSize = 18f; paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Um APK · cada entrada executa uma ação · sem tela de reprodução", 30f, 374f, paint)
        File("build/outputs/wear-previews").mkdirs()
        File("build/outputs/wear-previews/entradas-wear.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
