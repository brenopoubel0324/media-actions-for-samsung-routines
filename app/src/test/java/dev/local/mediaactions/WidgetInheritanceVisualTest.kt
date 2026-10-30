package dev.local.mediaactions

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Insets
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.EditText
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
class WidgetInheritanceVisualTest {
    private fun export(name:String,restore:Boolean,width:Int=412,font:Float=1f,keyboard:Boolean=false) {
        val app=RuntimeEnvironment.getApplication()
        if(font!=1f) app.resources.updateConfiguration(android.content.res.Configuration(app.resources.configuration).apply {fontScale=font},app.resources.displayMetrics)
        val manager=AppWidgetManager.getInstance(app)
        val id=shadowOf(manager).createWidget(MediaWidgetProvider::class.java,R.layout.widget_media_controls)
        shadowOf(manager).addBoundWidget(id,AppWidgetProviderInfo().apply {provider=ComponentName(app,MediaWidgetProvider::class.java)})
        manager.updateAppWidgetOptions(id,android.os.Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,280);putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,64)
        })
        WidgetLocationStore.save(app,id,WidgetLocation.LOCK_SCREEN)
        WidgetStyleStore.save(app,WidgetAppearance(iconStyle=WidgetIconStyle.LUCIDE,iconScalePercent=75,spacingDp=24,progressRing=true,buttonBorder=true,centerBorderOnly=true))
        WidgetStyleStore.save(app,WidgetAppearance(iconStyle=WidgetIconStyle.CIRCULAR,iconScalePercent=50,spacingDp=12),id)
        val intent=Intent(app,WidgetConfigureActivity::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id)
        Robolectric.buildActivity(WidgetConfigureActivity::class.java,intent).setup().use {c ->
            val activity=c.get()
            if(restore)activity.findViewById<View>(R.id.widget_use_default_visual).performClick()
            val content=activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            if(keyboard) {
                activity.findViewById<View>(R.id.settings_spacing).performClick()
                activity.findViewById<EditText>(R.id.widget_padding_vertical_value).requestFocus()
            }
            content.dispatchApplyWindowInsets(WindowInsets.Builder().setInsets(WindowInsets.Type.ime(),Insets.of(0,0,0,if(keyboard)300 else 0)).build())
            val root=activity.window.decorView
            fun layout() {root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(892,View.MeasureSpec.EXACTLY));root.layout(0,0,width,892)}
            layout();shadowOf(Looper.getMainLooper()).idle();layout()
            if(keyboard) {
                val field=activity.findViewById<EditText>(R.id.widget_padding_vertical_value)
                field.requestRectangleOnScreen(android.graphics.Rect(0,0,field.width,field.height),true)
            }
            fun settle(v:View) {v.jumpDrawablesToCurrentState();if(v is ViewGroup)for(i in 0 until v.childCount)settle(v.getChildAt(i))}
            settle(root)
            assertTrue(activity.findViewById<View>(R.id.widget_configuration_save).isShown)
            assertTrue(activity.findViewById<View>(R.id.widget_configuration_cancel).isShown)
            assertTrue(WidgetStyleStore.hasOverride(app,id))
            val bitmap=Bitmap.createBitmap(width,892,Bitmap.Config.ARGB_8888);root.draw(Canvas(bitmap))
            val file=File("build/outputs/widget-previews/$name.png");file.parentFile!!.mkdirs();file.outputStream().use {assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}
        }
    }
    @Test fun originalOwnAppearance()=export("visual-proprio",false)
    @Test fun inheritedDraftBeforeSaving()=export("visual-padrao-rascunho",true)
    @Test @Config(qualifiers="pt-rBR-w360dp-h892dp-night-mdpi") fun narrowDarkAndLargeFonts()=export("visual-padrao-escuro-ampliado",true,360,1.3f)
    @Test @Config(qualifiers="pt-rBR-w320dp-h892dp-mdpi") fun enlargedFontsAndSimulatedKeyboardInsets()=export("visual-padrao-insets-teclado",true,320,1.3f,true)
}
