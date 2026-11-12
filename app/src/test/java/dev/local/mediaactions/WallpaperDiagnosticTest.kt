package dev.local.mediaactions

import android.app.WallpaperColors
import android.content.ClipboardManager
import android.graphics.Color
import android.os.Looper
import android.view.View
import android.widget.TextView
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33,36],shadows=[ObservedWallpaperShadow::class,CountingSessionsShadow::class])
class WallpaperDiagnosticTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private val engine get()=WidgetWallpaperRuntime.get(app)!!.coordinator
    private val automatic=WidgetAppearance(colorMode=WidgetColorMode.WALLPAPER,wallpaperTarget=WidgetWallpaperTarget.LOCK)
    @Before fun before() {
        WallpaperProbe.reads.set(0);WallpaperProbe.colors=null;WallpaperProbe.failRead=false
        EnergyCounters.clear();engine.screen(true)
    }
    @After fun after(){engine.close();WallpaperProbe.failRead=false}
    private fun idle()=shadowOf(Looper.getMainLooper()).idle()
    private fun settle(expected:Int) {
        val deadline=System.nanoTime()+2_000_000_000L
        idle()
        while((engine.isReading(WallpaperDestination.LOCK)||WallpaperProbe.reads.get()<expected)&&System.nanoTime()<deadline){Thread.sleep(5);idle()}
        idle();assertEquals(expected,WallpaperProbe.reads.get());assertFalse(engine.isReading(WallpaperDestination.LOCK))
    }
    private fun editor(body:(WidgetDefaultsActivity)->Unit) {
        WidgetStyleStore.save(app,automatic)
        Robolectric.buildActivity(WidgetDefaultsActivity::class.java).setup().use{controller -> body(controller.get())}
    }
    @Test fun unavailableStatusIsVisibleWithoutOpeningAnySettingsSection()=editor{activity ->
        settle(1)
        assertEquals(View.VISIBLE,activity.findViewById<View>(R.id.widget_wallpaper_diagnostic_card).visibility)
        assertEquals(app.getString(R.string.widget_wallpaper_fallback),activity.findViewById<TextView>(R.id.widget_wallpaper_status).text)
        assertTrue(activity.findViewById<View>(R.id.widget_wallpaper_refresh).isEnabled)
    }
    @Test fun exceptionsAreDifferentFromUnavailableAndOnlyTheClassIsCopied() {
        WallpaperProbe.failRead=true
        editor{activity ->
            settle(1)
            assertEquals(app.getString(R.string.widget_wallpaper_error),activity.findViewById<TextView>(R.id.widget_wallpaper_status).text)
            activity.findViewById<View>(R.id.widget_wallpaper_copy).performClick()
            val text=app.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()
            assertTrue(text.contains("SecurityException"));assertFalse(text.contains("private detail"))
            assertTrue(text.contains("FALHA"));assertEquals(0,EnergyCounters.sessions)
        }
    }
    @Test fun explicitRefreshRecoversMissingColorsWithoutSavingOrStartingATimer()=editor{activity ->
        settle(1);WallpaperProbe.colors=WallpaperColors(Color.valueOf(Color.WHITE),null,null)
        repeat(10){activity.findViewById<View>(R.id.widget_wallpaper_refresh).performClick()}
        settle(2)
        assertEquals(app.getString(R.string.widget_wallpaper_dark_active),activity.findViewById<TextView>(R.id.widget_wallpaper_status).text)
        assertEquals(automatic,WidgetStyleStore.load(app));assertEquals(0,EnergyCounters.sessions)
    }
    @Test fun copiedDiagnosticDescribesTheChosenLockSourceAndActualPaletteWithoutRequerying() {
        WallpaperProbe.colors=WallpaperColors(Color.valueOf(Color.BLUE),null,null)
        editor{activity ->
            settle(1);activity.findViewById<View>(R.id.widget_wallpaper_copy).performClick()
            val text=app.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()
            assertTrue(text.contains("Destino: LOCK"));assertTrue(text.contains("#FF0000FF"));assertTrue(text.contains("Hints: 0"))
            assertTrue(text.contains("Ícones detectados: BRANCOS"));assertEquals(1,WallpaperProbe.reads.get());assertEquals(0,EnergyCounters.sessions)
        }
    }
    @Test fun manualEditorHidesDiagnosticAndDoesNotReadWallpaper() {
        WidgetStyleStore.save(app,WidgetAppearance())
        Robolectric.buildActivity(WidgetDefaultsActivity::class.java).setup().use{controller ->
            idle();assertEquals(View.GONE,controller.get().findViewById<View>(R.id.widget_wallpaper_diagnostic_card).visibility)
            assertEquals(0,WallpaperProbe.reads.get())
        }
    }
    @Test fun diagnosticCacheRejectsOldQueryMetadataAndPublishesUnavailableCompletion() {
        val queue=ArrayDeque<()->Unit>();val reads=mutableListOf<(WallpaperColorReading)->Unit>()
        lateinit var callback:(WallpaperDestination,WallpaperColorReading)->Unit
        val coordinator=WallpaperColorCoordinator({callback=it;true},{},{_,done->reads+=done},{queue+=it},{emptySet()})
        var updates=0
        fun drain(){var steps=0;while(queue.isNotEmpty()){check(++steps<30);queue.removeFirst()()}}
        coordinator.preview(Any(),automatic,WallpaperDestination.LOCK){updates++};drain()
        reads.single()(WallpaperColorReading(null));drain();assertEquals(2,updates)
        coordinator.recheck();coordinator.recheck();assertEquals(2,reads.size)
        val fresh=WallpaperColorReading(true,Color.WHITE,WallpaperColors.HINT_SUPPORTS_DARK_TEXT)
        callback(WallpaperDestination.LOCK,fresh);reads.last()(WallpaperColorReading(null,errorClass="OldFailure"));drain()
        assertEquals(fresh,coordinator.reading(WallpaperDestination.LOCK));coordinator.close()
    }
    @Test fun lockRefreshDoesNotRequeryOrWaitForPendingHomeRead() {
        val reads=mutableListOf<Pair<WallpaperDestination,(WallpaperColorReading)->Unit>>()
        val queue=ArrayDeque<()->Unit>()
        val coordinator=WallpaperColorCoordinator({true},{},{source,done->reads+=source to done},{queue+=it},{emptySet()})
        coordinator.reconcile(listOf(WallpaperColorCoordinator.Target(1,false,automatic,WallpaperDestination.HOME)))
        coordinator.preview(Any(),automatic,WallpaperDestination.LOCK){}
        reads.last().second(WallpaperColorReading(null))
        val before=reads.size
        coordinator.recheck(WallpaperDestination.LOCK)
        assertEquals(before+1,reads.size);assertEquals(WallpaperDestination.LOCK,reads.last().first)
        assertTrue(coordinator.isReading(WallpaperDestination.HOME))
        coordinator.recheck(WallpaperDestination.LOCK);assertEquals(before+1,reads.size)
        coordinator.close()
    }

    @Test fun automaticBordersUseTheFallbackInkAndKeepTheirSavedAlpha() {
        val style=automatic.copy(darkIcons=true,autoBorderColors=true,borderColor=0x44FFFFFF,buttonBorderColor=Color.WHITE)
        val resolved=WidgetWallpaperColors.resolve(style,null)
        assertTrue(resolved.darkIcons);assertEquals(0x441A1D22,resolved.borderColor);assertEquals(WidgetWallpaperColors.darkInk,resolved.buttonBorderColor)
        val light=WidgetWallpaperColors.resolve(style.copy(darkIcons=false),null)
        assertEquals(0x44FFFFFF,light.borderColor);assertEquals(Color.WHITE,light.buttonBorderColor)
        assertEquals(style.copy(colorMode=WidgetColorMode.FIXED),WidgetWallpaperColors.resolve(style.copy(colorMode=WidgetColorMode.FIXED),null))
    }
    @Test fun reportSeparatesTheSavedScopeFromAnUnsavedColorDraft() {
        WidgetStyleStore.save(app,WidgetAppearance())
        val report=WidgetWallpaperDiagnostic.report(app,automatic,WallpaperDestination.LOCK,null,false,false)
        assertTrue(report.contains("Escopo: PADRÃO GLOBAL"));assertTrue(report.contains("Salvo: FIXED / FOLLOW_LOCATION"))
        assertTrue(report.contains("Modo: WALLPAPER"));assertTrue(report.contains("Origem escolhida: LOCK"))
        assertEquals(WidgetColorMode.FIXED,WidgetStyleStore.load(app).colorMode)
    }

}
