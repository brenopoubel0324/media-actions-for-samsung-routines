package dev.local.mediaactions

import android.graphics.Color
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Spinner
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33,36],qualifiers="mdpi")
class CompactFrameHeightTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private val style=WidgetAppearance(widgetBorder=true,frameMode=WidgetFrameMode.COMPACT,frameHeightDp=32)
    @Test fun smallerHeightKeepsFullWidthAndTouchTargetsWithAndWithoutHostDimensions() {
        for(height in listOf(16,24,32,40,48,56))for(known in listOf(true,false)) {
            val root=WidgetRenderer.build(app,42,style.copy(frameHeightDp=height),if(known)SizeF(280f,160f)else null,bindControls=false).apply(app,FrameLayout(app))
            root.measure(View.MeasureSpec.makeMeasureSpec(280,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(160,View.MeasureSpec.EXACTLY));root.layout(0,0,280,160)
            val frame=root.findViewById<View>(R.id.widget_frame)
            assertEquals(height,frame.height);assertEquals(280,frame.width);assertEquals((160-height)/2,frame.top)
            assertEquals(48,root.findViewById<View>(R.id.widget_toggle).height)
        }
    }
    @Test fun compactEditorHasOnlyOriginalModesAndSaves32dp() {
        WidgetStyleStore.save(app,style.copy(frameHeightDp=64))
        Robolectric.buildActivity(WidgetDefaultsActivity::class.java).setup().use{controller ->
            val activity=controller.get()
            assertEquals(2,activity.findViewById<Spinner>(R.id.widget_frame_mode).adapter.count)
            activity.findViewById<EditText>(R.id.widget_frame_height_value).setText("32")
            activity.findViewById<View>(R.id.widget_configuration_save).performClick()
            assertEquals(style,WidgetStyleStore.load(app))
        }
    }
    @Test fun oldFittedSettingsAndBackupsAreReadableAsCompactWithoutOtherChanges() {
        val old=style.copy(frameHeightDp=64,borderColor=Color.RED)
        WidgetStyleStore.save(app,old)
        app.getSharedPreferences("widget_appearance",0).edit().putString("frame_mode","FIT_BUTTONS").apply()
        assertEquals(old,WidgetStyleStore.load(app))
        val state=WidgetAppearanceState.write(old).apply{putString("frame_mode","FIT_BUTTONS")}
        assertEquals(old,WidgetAppearanceState.read(state))
        val json=JSONObject(SettingsBackupCodec.encode(SettingsBackup(old,15,emptyList())))
        json.getJSONObject("defaults").put("frame_mode","FIT_BUTTONS")
        assertEquals(old,SettingsBackupCodec.decode(json.toString()).defaults)
    }
    @Test fun lowerHeightsRoundTripThroughStoreDraftAndBackupWithoutChangingOtherFields() {
        WidgetStyleStore.save(app,style);assertEquals(style,WidgetStyleStore.load(app))
        assertEquals(style,WidgetAppearanceState.read(WidgetAppearanceState.write(style)))
        val data=SettingsBackup(style,15,emptyList())
        assertEquals(data,SettingsBackupCodec.decode(SettingsBackupCodec.encode(data)))
        assertEquals(16,style.copy(frameHeightDp=0).normalized().frameHeightDp)
    }
}
