package dev.local.mediaactions

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
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
@Config(sdk=[33],qualifiers="pt-rBR-w412dp-h892dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackupVisualTest {
    private fun export(name:String,review:Boolean,width:Int=412,large:Boolean=false) {
        val app=RuntimeEnvironment.getApplication()
        if(large) app.resources.updateConfiguration(android.content.res.Configuration(app.resources.configuration).apply {fontScale=1.3f},app.resources.displayMetrics)
        val defaults=WidgetAppearance(iconScalePercent=65,spacingDp=24,progressRing=true,centerBorderOnly=true)
        val backup=SettingsBackup(defaults,15,listOf(
            BackupProfile("Mídia · Bloqueio",false,defaults,false,WidgetLocation.LOCK_SCREEN),
            BackupProfile("Volume · Tablet",true,defaults.copy(iconScalePercent=50),false,WidgetLocation.HOME_SCREEN)))
        SettingsBackupStore.restore(app,backup,false)
        Robolectric.buildActivity(BackupActivity::class.java).setup().use {c ->
            if(review) {
                val transfer=c.get().onRetainNonConfigurationInstance() as BackupTransfer
                transfer.importText=SettingsBackupCodec.encode(backup);transfer.status=R.string.backup_review
                transfer.listener?.invoke()
            }
            val root=c.get().window.decorView
            fun layout(){root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(892,View.MeasureSpec.EXACTLY));root.layout(0,0,width,892)}
            layout();org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();layout()
            fun settle(view:View){view.jumpDrawablesToCurrentState();if(view is ViewGroup)for(i in 0 until view.childCount)settle(view.getChildAt(i))}
            settle(root)
            assertTrue(c.get().findViewById<View>(R.id.backup_export).isEnabled)
            assertEquals(if(review)View.VISIBLE else View.GONE,c.get().findViewById<View>(R.id.backup_preview).visibility)
            assertEquals(WidgetAppearance(),WidgetStyleStore.load(app))
            val image=Bitmap.createBitmap(width,892,Bitmap.Config.ARGB_8888);root.draw(Canvas(image))
            val out=File("build/outputs/widget-previews/$name.png");out.parentFile!!.mkdirs()
            out.outputStream().use {assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,it))}
        }
    }
    @Test fun backupScreen()=export("backup-transferencia",false)
    @Test fun importReview()=export("backup-revisar-importacao",true)
    @Test @Config(qualifiers="pt-rBR-w360dp-h892dp-night-mdpi") fun darkLargeText()=export("backup-escuro-ampliado",true,360,true)
}
