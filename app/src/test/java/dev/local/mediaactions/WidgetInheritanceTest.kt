package dev.local.mediaactions

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33,36],qualifiers="pt-rBR")
class WidgetInheritanceTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private val widgets get()=AppWidgetManager.getInstance(app)
    private fun widget():Int {
        val id=shadowOf(widgets).createWidget(MediaWidgetProvider::class.java,R.layout.widget_media_controls)
        shadowOf(widgets).addBoundWidget(id,AppWidgetProviderInfo().apply {provider=ComponentName(app,MediaWidgetProvider::class.java)})
        return id
    }
    private fun request(id:Int)=Intent(app,WidgetConfigureActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id)
    private fun restore(a:Activity)=a.findViewById<View>(R.id.widget_use_default_visual).performClick()
    private fun save(a:Activity)=a.findViewById<View>(R.id.widget_configuration_save).performClick()
    private fun mode(a:Activity)=a.findViewById<TextView>(R.id.widget_visual_mode).text.toString()
    private fun scale(a:Activity)=a.findViewById<EditText>(R.id.widget_scale_value)

    @Test fun explicitOverrideEqualToDefaultIsOwnAndRestoreAffectsOnlyAppearanceOfThatInstance() {
        val first=widget();val other=widget()
        val defaults=WidgetAppearance(iconScalePercent=65,spacingDp=20)
        val custom=WidgetAppearance(iconScalePercent=40,darkIcons=true)
        WidgetStyleStore.save(app,defaults);WidgetStyleStore.save(app,defaults,first);WidgetStyleStore.save(app,custom,other)
        WidgetLocationStore.save(app,first,WidgetLocation.LOCK_SCREEN)
        Robolectric.buildActivity(WidgetConfigureActivity::class.java,request(first)).setup().use {c ->
            assertEquals("Visual próprio",mode(c.get()))
            restore(c.get());assertEquals("Segue o visual padrão",mode(c.get()))
            assertTrue(WidgetStyleStore.hasOverride(app,first))
            save(c.get());assertFalse(WidgetStyleStore.hasOverride(app,first))
        }
        val newer=defaults.copy(iconScalePercent=80);WidgetStyleStore.save(app,newer)
        assertEquals(newer,WidgetStyleStore.load(app,first));assertEquals(custom,WidgetStyleStore.load(app,other))
        assertEquals(WidgetLocation.LOCK_SCREEN,WidgetLocationStore.load(app,first))
    }
    @Test fun cancelAfterRestoreAndRecreationDoesNotWritePreferences() {
        val id=widget();val original=WidgetAppearance(iconScalePercent=45)
        WidgetStyleStore.save(app,original,id)
        Robolectric.buildActivity(WidgetConfigureActivity::class.java,request(id)).setup().use {c ->
            restore(c.get());c.recreate()
            assertEquals("Segue o visual padrão",mode(c.get()))
            c.get().findViewById<View>(R.id.widget_configuration_cancel).performClick()
            assertEquals(Activity.RESULT_CANCELED,shadowOf(c.get()).resultCode)
        }
        assertTrue(WidgetStyleStore.hasOverride(app,id));assertEquals(original,WidgetStyleStore.load(app,id))
    }
    @Test fun restoreEditAndRevertKeepsExplicitCustomIntentEvenWhenAllValuesEqualOriginal() {
        for (own in listOf(false,true)) {
            val id=widget();WidgetStyleStore.save(app,WidgetAppearance())
            if(own) WidgetStyleStore.save(app,WidgetAppearance(),id)
            Robolectric.buildActivity(WidgetConfigureActivity::class.java,request(id)).setup().use {c ->
                restore(c.get());scale(c.get()).setText("75");scale(c.get()).setText("100");c.recreate()
                assertEquals("Visual próprio",mode(c.get()));save(c.get())
            }
            assertTrue(WidgetStyleStore.hasOverride(app,id))
            WidgetStyleStore.save(app,WidgetAppearance(iconScalePercent=60))
            assertEquals(100,WidgetStyleStore.load(app,id).iconScalePercent)
        }
    }
    @Test fun ordinaryRevertedEditsAndLocationOnlySavePreserveConcurrentAppearanceAndMode() {
        for (own in listOf(false,true)) {
            val id=widget();val original=WidgetAppearance(iconScalePercent=50)
            WidgetStyleStore.save(app,original)
            if(own) WidgetStyleStore.save(app,original,id)
            Robolectric.buildActivity(WidgetConfigureActivity::class.java,request(id)).setup().use {c ->
                scale(c.get()).setText("75");scale(c.get()).setText("50")
                c.get().findViewById<Spinner>(R.id.widget_location_choice).setSelection(WidgetLocation.LOCK_SCREEN.ordinal)
                shadowOf(Looper.getMainLooper()).idle()
                WidgetStyleStore.save(app,original.copy(iconScalePercent=80),if(own)id else null)
                c.recreate();save(c.get())
            }
            assertEquals(own,WidgetStyleStore.hasOverride(app,id))
            assertEquals(80,WidgetStyleStore.load(app,id).iconScalePercent)
            assertEquals(WidgetLocation.LOCK_SCREEN,WidgetLocationStore.load(app,id))
        }
    }
    @Test fun inheritUsesLatestGlobalAtResumeRecreationAndSaveWithoutImplicitCustomIntent() {
        val id=widget();WidgetStyleStore.save(app,WidgetAppearance(iconScalePercent=40),id)
        Robolectric.buildActivity(WidgetConfigureActivity::class.java,request(id)).setup().use {c ->
            restore(c.get());c.pause()
            WidgetStyleStore.save(app,WidgetAppearance(iconScalePercent=70))
            c.resume();assertEquals("70",scale(c.get()).text.toString())
            c.recreate();shadowOf(Looper.getMainLooper()).idle()
            assertEquals("Segue o visual padrão",mode(c.get()))
            WidgetStyleStore.save(app,WidgetAppearance(iconScalePercent=90))
            save(c.get())
        }
        assertFalse(WidgetStyleStore.hasOverride(app,id));assertEquals(90,WidgetStyleStore.load(app,id).iconScalePercent)
    }
    @Test fun restoreDiscardsInvalidCustomTextBeforeSave() {
        val id=widget();WidgetStyleStore.save(app,WidgetAppearance(buttonBorder=true),id)
        Robolectric.buildActivity(WidgetConfigureActivity::class.java,request(id)).setup().use {c ->
            scale(c.get()).setText("")
            c.get().findViewById<EditText>(R.id.widget_button_border_color_value).setText("#123")
            save(c.get());assertFalse(c.get().isFinishing)
            restore(c.get());assertEquals("100",scale(c.get()).text.toString())
            c.recreate();save(c.get());assertTrue(c.get().isFinishing)
        }
        assertFalse(WidgetStyleStore.hasOverride(app,id))
    }
    @Test fun invalidTextEnteredAfterRestoreIsCustomAndMustBeCorrectedAfterRecreation() {
        val id=widget();WidgetStyleStore.save(app,WidgetAppearance(iconScalePercent=40),id)
        Robolectric.buildActivity(WidgetConfigureActivity::class.java,request(id)).setup().use {c ->
            restore(c.get());scale(c.get()).setText("");c.recreate()
            assertEquals("Visual próprio",mode(c.get()));save(c.get())
            assertFalse(c.get().isFinishing);assertNotNull(scale(c.get()).error)
            scale(c.get()).setText("100");save(c.get())
        }
        assertTrue(WidgetStyleStore.hasOverride(app,id));assertEquals(100,WidgetStyleStore.load(app,id).iconScalePercent)
    }
    @Test fun removedWidgetCannotHaveItsOverrideClearedAndGlobalEditorHasNoRestoreAction() {
        Robolectric.buildActivity(WidgetDefaultsActivity::class.java).setup().use {c ->
            assertNull(c.get().findViewById<View>(R.id.widget_use_default_visual))
        }
        val id=widget();WidgetStyleStore.save(app,WidgetAppearance(iconScalePercent=40),id)
        Robolectric.buildActivity(WidgetConfigureActivity::class.java,request(id)).setup().use {c ->
            restore(c.get())
            org.robolectric.util.ReflectionHelpers.getField<MutableMap<Int,*>>(shadowOf(widgets),"widgetInfos").remove(id)
            save(c.get());assertFalse(c.get().isFinishing)
        }
        assertTrue(WidgetStyleStore.hasOverride(app,id));assertEquals(40,WidgetStyleStore.load(app,id).iconScalePercent)
    }
    @Test fun reusableEditorAdvancesOriginalModeAfterEachApply() {
        val id=widget();WidgetStyleStore.save(app,WidgetAppearance(iconScalePercent=40),id)
        val editor=WidgetAppearanceSection(app,id);val root=editor.create()
        root.findViewById<View>(R.id.widget_use_default_visual).performClick();assertTrue(editor.applyChanges())
        assertFalse(WidgetStyleStore.hasOverride(app,id))
        var state=Bundle();editor.saveUiState(state)
        assertFalse(state.getBoolean("widget_original_override"));assertEquals("KEEP",state.getString("widget_appearance_intent"))
        root.findViewById<EditText>(R.id.widget_scale_value).setText("70");assertTrue(editor.applyChanges())
        assertTrue(WidgetStyleStore.hasOverride(app,id))
        state=Bundle();editor.saveUiState(state);assertTrue(state.getBoolean("widget_original_override"))
    }
    @Test fun keepDoesNotReimposeOriginalModeAfterExternalRemovalOrNewOverride() {
        for (own in listOf(false,true)) {
            val id=widget();val original=WidgetAppearance(iconScalePercent=60)
            WidgetStyleStore.save(app,original)
            if(own) WidgetStyleStore.save(app,original,id)
            Robolectric.buildActivity(WidgetConfigureActivity::class.java,request(id)).setup().use {c ->
                if(own) WidgetStyleStore.remove(app,id) else WidgetStyleStore.save(app,original.copy(spacingDp=25),id)
                c.recreate();save(c.get())
            }
            assertEquals(!own,WidgetStyleStore.hasOverride(app,id))
        }
    }
}
