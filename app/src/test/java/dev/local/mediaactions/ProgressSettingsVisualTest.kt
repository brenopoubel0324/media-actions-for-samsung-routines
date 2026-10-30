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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],qualifiers="pt-rBR-w412dp-h892dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProgressSettingsVisualTest {
    @Test fun exportGlobalIntervalEditor() {
        Robolectric.buildActivity(ProgressSettingsActivity::class.java).setup().use { c ->
            c.get().findViewById<View>(R.id.progress_interval_15).performClick()
            val root=c.get().window.decorView
            root.measure(View.MeasureSpec.makeMeasureSpec(412,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(892,View.MeasureSpec.EXACTLY));root.layout(0,0,412,892)
            fun settle(v:View) { v.jumpDrawablesToCurrentState();if(v is ViewGroup)for(i in 0 until v.childCount)settle(v.getChildAt(i)) }
            settle(root)
            val bitmap=Bitmap.createBitmap(412,892,Bitmap.Config.ARGB_8888);root.draw(Canvas(bitmap))
            val file=File("build/outputs/widget-previews/intervalo-progresso.png");file.parentFile!!.mkdirs()
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
        }
    }
}
