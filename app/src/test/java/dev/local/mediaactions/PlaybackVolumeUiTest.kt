package dev.local.mediaactions

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 36], qualifiers = "pt-rBR-mdpi")
class PlaybackVolumeUiTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val manager get() = AppWidgetManager.getInstance(app)
    private val kind = WidgetKind.PLAYBACK_VOLUME
    private val controls = listOf(R.id.widget_previous, R.id.widget_toggle, R.id.widget_next)
    private fun options(vararg sizes: SizeF) = Bundle().apply {
        putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, ArrayList(sizes.toList()))
    }
    private fun layout(root: View, w: Int, h: Int) {
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        root.layout(0,0,w,h)
    }
    private fun build(w: Int, h: Int, style: WidgetAppearance = WidgetAppearance(), state: WidgetPlaybackState = WidgetPlaybackState.UNKNOWN): View =
        WidgetRenderer.build(app, 77, style, SizeF(w.toFloat(),h.toFloat()), false, state, kind = kind)
            .apply(app, FrameLayout(app)).also { layout(it,w,h) }
    private fun widget(): Int {
        val id = shadowOf(manager).createWidget(PlaybackVolumeWidgetProvider::class.java, R.layout.widget_playback_volume_controls)
        shadowOf(manager).addBoundWidget(id, AppWidgetProviderInfo().apply {
            provider = ComponentName(app, PlaybackVolumeWidgetProvider::class.java)
        })
        manager.updateAppWidgetOptions(id, options(SizeF(240f,64f)))
        return id
    }
    private fun request(id: Int) = Intent(app,WidgetConfigureActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id)

    @Test fun realLayoutsKeepThreeTargetsAndSemanticOrderAtMinimumAndWithExcessPadding() {
        for ((w,h) in listOf(144 to 48,48 to 144,320 to 96,96 to 320,160 to 160)) {
            val root = build(w,h,WidgetAppearance(spacingDp=144,widgetPaddingHorizontalDp=144,widgetPaddingVerticalDp=144))
            val row = root.findViewById<LinearLayout>(R.id.widget_controls_row)
            val minus = root.findViewById<View>(R.id.widget_previous)
            val center = root.findViewById<View>(R.id.widget_toggle)
            val plus = root.findViewById<View>(R.id.widget_next)
            assertEquals(if (h>w) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL,row.orientation)
            for (button in listOf(minus,center,plus)) {
                assertTrue("$w × $h: target ${button.width} × ${button.height}",button.width>=48 && button.height>=48)
            }
            if (h>w) assertTrue(plus.top<center.top && center.top<minus.top)
            else assertTrue(minus.left<center.left && center.left<plus.left)
            assertEquals(app.getString(R.string.volume_lower),minus.contentDescription)
            assertEquals(app.getString(R.string.volume_raise),plus.contentDescription)
        }
    }
    @Test fun negativeVerticalPaddingKeepsPhysicalAxesAndCompactVerticalEnclosesThreeControls() {
        val style = WidgetAppearance(spacingDp=24,widgetPaddingHorizontalDp=8,widgetPaddingVerticalDp=-12,
            widgetBorder=true,frameMode=WidgetFrameMode.COMPACT,frameHeightDp=16)
        val root = build(96,320,style)
        assertEquals(8,root.paddingLeft); assertEquals(-12,root.paddingTop)
        val row = root.findViewById<View>(R.id.widget_controls_row)
        val frame = root.findViewById<View>(R.id.widget_frame)
        assertTrue(row.height>=144); assertTrue(frame.height>=row.height)
        assertEquals(48,root.findViewById<View>(R.id.widget_toggle).height)
    }
    @Test fun negativePaddingNeverClipsTheCombinedTouchTargetsInFillOrCompactLayouts() {
        for (mode in WidgetFrameMode.entries) for ((w,h) in listOf(48 to 144,144 to 48,432 to 48,96 to 320,320 to 96)) {
            val style=WidgetAppearance(widgetBorder=true,frameMode=mode,frameHeightDp=16,fillWidth=true,
                widgetPaddingVerticalDp=-24)
            val root=build(w,h,style)
            for (id in controls) {
                val button=root.findViewById<View>(id)
                var current:View=button
                var left=0;var top=0
                while (current !== root) {
                    left+=current.left;top+=current.top
                    current=current.parent as View
                }
                val message="$mode $w × $h: target at $left,$top with ${button.width} × ${button.height}"
                assertTrue(message,button.width>=48 && button.height>=48)
                assertTrue(message,left>=0 && top>=0 && left+button.width<=w && top+button.height<=h)
            }
            assertEquals(-24,style.widgetPaddingVerticalDp)
            if (mode == WidgetFrameMode.FILL && (w==48 || (w==432 && h==48))) assertEquals(0,root.paddingTop)
        }
        // A negative setting that still fits remains effective; no blanket preference rewrite.
        val root=build(240,96,WidgetAppearance(widgetPaddingVerticalDp=-12))
        assertEquals(-12,root.paddingTop)
    }
    @Test fun insufficientAndUnknownHostSizesExposeOnlyInstructionWithoutHiddenClickTargets() {
        for (size in listOf(null,SizeF(143f,48f),SizeF(48f,143f),SizeF(100f,100f),SizeF(144f,47f))) {
            val packet=WidgetRenderer.build(app,77,WidgetAppearance(),size,kind=kind)
            assertEquals(R.layout.widget_playback_volume_resize,packet.layoutId)
            val root=packet.apply(app,FrameLayout(app))
            assertEquals(app.getString(R.string.playback_volume_resize),root.findViewById<TextView>(R.id.widget_resize_hint).text)
            for (id in controls) assertNull(root.findViewById<View>(id))
            assertFalse(root.hasOnClickListeners());assertFalse(root.findViewById<View>(R.id.widget_resize_hint).hasOnClickListeners())
        }
        assertFalse(WidgetRenderer.hasUsablePlaybackSize(Bundle.EMPTY,kind))
        assertFalse(WidgetRenderer.hasUsablePlaybackSize(options(SizeF(143f,48f),SizeF(0f,200f)),kind))
        assertTrue(WidgetRenderer.hasUsablePlaybackSize(options(SizeF(143f,48f),SizeF(48f,144f)),kind))
        assertTrue(WidgetRenderer.hasUsablePlaybackSize(Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,48);putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,144)
        },kind))
        assertTrue(WidgetRenderer.hasUsablePlaybackSize(Bundle.EMPTY,WidgetKind.MEDIA))
        assertFalse(WidgetRenderer.hasUsablePlaybackSize(options(SizeF(200f,64f)),WidgetKind.VOLUME))
    }
    @Test fun unknownPlaybackAffectsOnlyCenterDecorationAndConfirmedCenterRestoresOnReapply() {
        val style=WidgetAppearance(buttonBorder=true,buttonBorderColor=Color.YELLOW,progressRing=true)
        val root=build(240,64,style)
        assertEquals(Color.YELLOW,root.findViewById<View>(R.id.widget_previous).backgroundTintList!!.defaultColor)
        assertEquals(Color.YELLOW,root.findViewById<View>(R.id.widget_next).backgroundTintList!!.defaultColor)
        assertNull(root.findViewById<View>(R.id.widget_toggle).backgroundTintList)
        assertEquals(app.getString(R.string.widget_state_unknown),root.findViewById<View>(R.id.widget_toggle).contentDescription)
        WidgetRenderer.build(app,77,style,SizeF(240f,64f),false,WidgetPlaybackState.TRANSITIONING,kind=kind).reapply(app,root)
        assertEquals(150,root.findViewById<ImageButton>(R.id.widget_toggle).imageAlpha)
        WidgetRenderer.build(app,77,style,SizeF(240f,64f),false,WidgetPlaybackState.PAUSED,kind=kind).reapply(app,root)
        assertEquals(255,root.findViewById<ImageButton>(R.id.widget_toggle).imageAlpha)
        assertEquals(Color.YELLOW,root.findViewById<View>(R.id.widget_toggle).backgroundTintList!!.defaultColor)
        assertEquals(app.getString(R.string.widget_play_action),root.findViewById<View>(R.id.widget_toggle).contentDescription)
        WidgetRenderer.build(app,77,style,SizeF(240f,64f),false,WidgetPlaybackState.PLAYING,WidgetTrackProgress(0.4f),kind=kind).reapply(app,root)
        assertNull(root.findViewById<View>(R.id.widget_toggle).backgroundTintList)
        assertEquals(app.getString(R.string.widget_pause_action),root.findViewById<View>(R.id.widget_toggle).contentDescription)
        assertEquals(Color.YELLOW,root.findViewById<View>(R.id.widget_next).backgroundTintList!!.defaultColor)
    }
    @Test fun centerOnlyBordersNeverDecorateVolumeSidesAndPreviewNeverExecutesCommands() {
        val root=build(240,64,WidgetAppearance(buttonBorder=true,centerBorderOnly=true),WidgetPlaybackState.PAUSED)
        assertNull(root.findViewById<View>(R.id.widget_previous).backgroundTintList)
        assertNull(root.findViewById<View>(R.id.widget_next).backgroundTintList)
        assertNotNull(root.findViewById<View>(R.id.widget_toggle).backgroundTintList)
        for (id in controls) assertFalse(root.findViewById<View>(id).performClick())
    }
    @Test fun combinedEditorRetainsItsDraftAndSavesOnlyItsOwnInstance() {
        val id=widget();val other=widget()
        Robolectric.buildActivity(WidgetConfigureActivity::class.java,request(id)).setup().use { c ->
            val activity=c.get()
            assertNotNull(activity.findViewById<View>(R.id.widget_toggle))
            assertEquals(app.getString(R.string.widget_preview_action,app.getString(R.string.volume_lower)),activity.findViewById<View>(R.id.widget_previous).contentDescription)
            activity.findViewById<EditText>(R.id.widget_spacing_value).setText("37")
            assertNotEquals(37,WidgetStyleStore.load(app,id).spacingDp)
            c.recreate()
            assertEquals("37",c.get().findViewById<EditText>(R.id.widget_spacing_value).text.toString())
            c.get().findViewById<View>(R.id.widget_configuration_save).performClick()
            assertEquals(Activity.RESULT_OK,shadowOf(c.get()).resultCode)
            assertEquals(37,WidgetStyleStore.load(app,id).spacingDp)
            assertNotEquals(37,WidgetStyleStore.load(app,other).spacingDp)
        }
    }
    @Test fun editorRefusesReplacedProviderBeforeWritingAndCancelPreservesPreferences() {
        val id=widget();val original=WidgetStyleStore.load(app,id)
        val editor=WidgetAppearanceSection(app,id,original.copy(iconScalePercent=55))
        shadowOf(manager).addBoundWidget(id,AppWidgetProviderInfo().apply {provider=ComponentName(app,MediaWidgetProvider::class.java)})
        assertFalse(editor.applyChanges());assertEquals(original,WidgetStyleStore.load(app,id))
        val current=widget()
        Robolectric.buildActivity(WidgetConfigureActivity::class.java,request(current)).setup().use { c ->
            c.get().findViewById<EditText>(R.id.widget_spacing_value).setText("41")
            c.get().findViewById<View>(R.id.widget_configuration_cancel).performClick()
            assertEquals(Activity.RESULT_CANCELED,shadowOf(c.get()).resultCode)
            assertNotEquals(41,WidgetStyleStore.load(app,current).spacingDp)
        }
    }
}
