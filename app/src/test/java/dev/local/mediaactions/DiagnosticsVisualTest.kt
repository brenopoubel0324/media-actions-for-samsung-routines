package dev.local.mediaactions

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],qualifiers="pt-rBR-w412dp-h892dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiagnosticsVisualTest {
    private fun export(name:String,width:Int=412,large:Boolean=false) {
        val app=RuntimeEnvironment.getApplication()
        if(large) app.resources.updateConfiguration(android.content.res.Configuration(app.resources.configuration).apply {fontScale=1.3f},app.resources.displayMetrics)
        app.getSharedPreferences("spotify_resume",0).edit().putString("status","REJECTED").putLong("completed_at_epoch_ms",1793372400000L).apply()
        Robolectric.buildActivity(MainActivity::class.java).setup().use {c ->
            val activity=c.get();activity.findViewById<View>(R.id.settings_help).performClick()
            val root=activity.window.decorView
            fun layout() {root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(892,View.MeasureSpec.EXACTLY));root.layout(0,0,width,892)}
            layout();shadowOf(android.os.Looper.getMainLooper()).idle();layout()
            val header=activity.findViewById<View>(R.id.settings_help)
            var ancestor=header.parent
            while(ancestor !is ScrollView) ancestor=ancestor.parent
            val scroll=ancestor
            val rect=Rect();header.getDrawingRect(rect);scroll.offsetDescendantRectToMyCoords(header,rect);scroll.scrollTo(0,(rect.top-12).coerceAtLeast(0))
            fun settle(v:View) {v.jumpDrawablesToCurrentState();if(v is ViewGroup)for(i in 0 until v.childCount)settle(v.getChildAt(i))}
            settle(root)
            assertTrue(activity.findViewById<TextView>(R.id.spotify_resume_status).isShown)
            assertTrue(activity.findViewById<View>(R.id.diagnostic_copy).isShown)
            val bitmap=Bitmap.createBitmap(width,892,Bitmap.Config.ARGB_8888);root.draw(Canvas(bitmap))
            val file=File("build/outputs/widget-previews/$name.png");file.parentFile!!.mkdirs();file.outputStream().use {assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}
        }
    }
    @Test fun lightDiagnostic()=export("diagnostico-claro")
    @Test @Config(qualifiers="pt-rBR-w360dp-h892dp-night-mdpi") fun darkNarrowLargeTextDiagnostic()=export("diagnostico-escuro-ampliado",360,true)
}
