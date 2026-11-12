package dev.local.mediaactions

import android.app.WallpaperColors
import android.graphics.Color
import android.os.Bundle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33,36])
class WallpaperColorCoordinatorTest {
    private val automatic = WidgetAppearance(colorMode=WidgetColorMode.WALLPAPER)
    private class Harness {
        val queue = ArrayDeque<() -> Unit>()
        val reads = mutableListOf<Pair<WallpaperDestination, (Boolean?) -> Unit>>()
        val updates = mutableListOf<Pair<Int, WidgetAppearance>>()
        val callbacks = mutableListOf<(WallpaperDestination, Boolean?) -> Unit>()
        var subscriptions=0; var removals=0; var success=true; var canSubscribe=true
        val engine = WallpaperColorCoordinator(
            { cb -> subscriptions++; callbacks+=cb; canSubscribe }, { removals++ },
            { source, done -> reads+=source to done }, { queue+=it },
            { changes -> changes.forEach { (target, style) -> updates+=target.id to style }; if(success) changes.map { it.first.id }.toSet() else emptySet() })
        fun drain() { var steps=0; while(queue.isNotEmpty()) { check(++steps<30); queue.removeFirst()() } }
        fun event(value:Boolean?,source:WallpaperDestination=WallpaperDestination.HOME) { callbacks.last()(source,value) }
    }
    private fun target(id:Int=1,source:WallpaperDestination?=WallpaperDestination.HOME,style:WidgetAppearance=automatic) =
        WallpaperColorCoordinator.Target(id,false,style,source)

    @Test fun fixedWidgetsNeverSubscribeOrReadAndUnknownHostsUseFallback() {
        val h=Harness();h.engine.reconcile(listOf(target(style=WidgetAppearance())));h.drain()
        assertEquals(0,h.subscriptions);assertTrue(h.reads.isEmpty());assertTrue(h.updates.isEmpty())
        h.engine.reconcile(listOf(target(source=null,style=automatic.copy(darkIcons=true))));h.drain()
        assertEquals(0,h.subscriptions);assertTrue(h.reads.isEmpty());assertTrue(h.updates.single().second.darkIcons)
    }
    @Test fun payloadEventsCoalesceAndSameContrastDoesNoWorkOrQueries() {
        val h=Harness();h.engine.reconcile(listOf(target()));h.drain();assertTrue(h.updates.isEmpty())
        h.reads.single().second(false);h.drain();h.updates.clear()
        repeat(20){h.event(true)};h.drain()
        assertEquals(1,h.reads.size);assertEquals(1,h.subscriptions);assertEquals(1,h.updates.size);assertTrue(h.updates.single().second.darkIcons)
        repeat(20){h.event(true);assertTrue(h.queue.isEmpty());h.drain()};assertEquals(1,h.updates.size);assertEquals(1,h.reads.size)
    }
    @Test fun lateInitialQueryCannotOverwriteNewerEvent() {
        val h=Harness();h.engine.reconcile(listOf(target()));h.event(true);h.reads.single().second(false);h.drain()
        assertTrue(h.updates.single().second.darkIcons);assertEquals(true,h.engine.detected(WallpaperDestination.HOME))
    }
    @Test fun homeAndLockAreIndependentAndNullRestoresSavedFallback() {
        val h=Harness();h.engine.reconcile(listOf(target(),target(2,WallpaperDestination.LOCK,automatic.copy(darkIcons=true))))
        h.reads[0].second(true);h.reads[1].second(false);h.drain()
        assertTrue(h.updates.first{it.first==1}.second.darkIcons);assertFalse(h.updates.first{it.first==2}.second.darkIcons)
        h.updates.clear();h.event(null,WallpaperDestination.LOCK);h.drain()
        assertEquals(2,h.updates.single().first);assertTrue(h.updates.single().second.darkIcons)
    }
    @Test fun screenOffCachesWithoutUpdatesAndScreenOnResynchronizesWithoutTimer() {
        val h=Harness();h.engine.reconcile(listOf(target()));h.reads.single().second(false);h.drain();h.updates.clear()
        h.engine.screen(false);repeat(20){h.event(true)};h.drain();assertTrue(h.updates.isEmpty());assertEquals(1,h.reads.size)
        h.engine.screen(true);assertEquals(2,h.reads.size);h.reads.last().second(true);h.drain()
        assertEquals(1,h.updates.size);assertTrue(h.updates.single().second.darkIcons)
    }
    @Test fun deletionAndManualSwitchRejectLateCallbacksAndQueries() {
        val h=Harness();h.engine.reconcile(listOf(target()));val callback=h.callbacks.single();val result=h.reads.single().second
        h.engine.reconcile(listOf(target(style=WidgetAppearance())));callback(WallpaperDestination.HOME,true);result(true);h.drain()
        assertTrue(h.updates.isEmpty());assertEquals(1,h.removals);assertNull(h.engine.detected(WallpaperDestination.HOME))
        h.engine.reconcile(listOf(target()));h.reads.last().second(true);h.drain();assertTrue(h.updates.single().second.darkIcons)
    }
    @Test fun failedDeliveryRetriesOnNextUnchangedEventAndNotOnATimer() {
        val h=Harness();h.success=false;h.engine.reconcile(listOf(target()));h.reads.single().second(true);h.drain()
        assertEquals(1,h.updates.size);assertTrue(h.queue.isEmpty());h.success=true;h.event(true);h.drain()
        assertEquals(2,h.updates.size);h.event(true);h.drain();assertEquals(2,h.updates.size)
    }
    @Test fun previewConsumersReleaseObservationAndUnchangedDataDoesNotRedraw() {
        val h=Harness();val token=Any();var changes=0
        h.engine.preview(token,automatic,WallpaperDestination.HOME){changes++};h.reads.single().second(true);h.drain()
        assertEquals(1,changes);repeat(5){h.event(true);h.drain()};assertEquals(1,changes)
        h.engine.preview(token,null,null);h.event(false);h.drain();assertEquals(1,changes);assertEquals(1,h.removals)
    }
    @Test fun failedRegistrationCanRecoverOnAConfigurationEvent() {
        val h=Harness();h.canSubscribe=false;h.engine.reconcile(listOf(target()));h.reads.single().second(null);h.drain()
        assertFalse(h.updates.single().second.darkIcons)
        h.canSubscribe=true;h.engine.reconcile(listOf(target()));assertEquals(2,h.reads.size);h.reads.last().second(true);h.drain()
        assertEquals(2,h.subscriptions);assertTrue(h.updates.last().second.darkIcons)
    }
    @Test fun pendingReadFromOldConfigurationDoesNotWinAfterRetargeting() {
        val h=Harness();h.engine.reconcile(listOf(target()));val old=h.reads.single().second
        h.engine.reconcile(listOf(target(source=WallpaperDestination.LOCK)));old(true);h.reads.last().second(false);h.drain()
        assertEquals(1,h.updates.size);assertFalse(h.updates.single().second.darkIcons)
    }
    @Test fun returningToAnUnusedSourceReadsAgainAndIgnoresOldCallbacks() {
        val h=Harness();h.engine.reconcile(listOf(target()));h.reads.single().second(true);h.drain()
        h.engine.reconcile(listOf(target(source=WallpaperDestination.LOCK)))
        h.reads.last().second(false);h.drain();h.event(false,WallpaperDestination.HOME);h.drain()
        h.engine.reconcile(listOf(target()));assertEquals(3,h.reads.size)
        h.reads.last().second(false);h.drain();assertFalse(h.updates.last().second.darkIcons)
    }
    @Test fun screenOnRejectsAReadStartedBeforeTheScreenTurnedOff() {
        val h=Harness();h.engine.reconcile(listOf(target()));val old=h.reads.single().second
        h.engine.screen(false);h.engine.screen(true);assertEquals(2,h.reads.size)
        old(true);h.reads.last().second(false);h.drain();assertFalse(h.updates.single().second.darkIcons)
    }
    @Test fun paletteHonorsHintsAndHandlesLightDarkAndMissingColors() {
        fun colors(color:Int,hints:Int=0)=WallpaperColors(Color.valueOf(color),null,null,hints)
        assertNull(WidgetWallpaperColors.darkIcons(null))
        assertEquals(true,WidgetWallpaperColors.darkIcons(colors(Color.WHITE)))
        assertEquals(false,WidgetWallpaperColors.darkIcons(colors(Color.BLACK)))
        assertEquals(true,WidgetWallpaperColors.darkIcons(colors(Color.BLACK,WallpaperColors.HINT_SUPPORTS_DARK_TEXT)))
        assertEquals(false,WidgetWallpaperColors.darkIcons(colors(Color.WHITE,WallpaperColors.HINT_SUPPORTS_DARK_THEME)))
    }
    @Test fun manualColorsAndBorderAlphaSurviveAndAutomaticBordersAreOptional() {
        val style=automatic.copy(borderColor=0x44FF0000,buttonBorderColor=Color.BLUE)
        assertEquals(style,WidgetWallpaperColors.resolve(style,null))
        assertEquals(style.copy(darkIcons=true),WidgetWallpaperColors.resolve(style,true))
        val resolved=WidgetWallpaperColors.resolve(style.copy(autoBorderColors=true),true)
        assertEquals(0x441A1D22,resolved.borderColor);assertEquals(WidgetWallpaperColors.darkInk,resolved.buttonBorderColor)
        assertEquals(style.copy(colorMode=WidgetColorMode.FIXED),WidgetWallpaperColors.resolve(style.copy(colorMode=WidgetColorMode.FIXED),true))
    }
    @Test fun explicitSourceAndUserLocationWinOverHostHintsAndUnknownIsNotGuessed() {
        val home=Bundle().apply{putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY,android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)}
        assertEquals(WallpaperDestination.LOCK,WidgetWallpaperColors.destination(automatic,WidgetLocation.LOCK_SCREEN,home))
        assertEquals(WallpaperDestination.HOME,WidgetWallpaperColors.destination(automatic,WidgetLocation.AUTOMATIC,home))
        assertNull(WidgetWallpaperColors.destination(automatic,WidgetLocation.AUTOMATIC,Bundle()))
        assertEquals(WallpaperDestination.LOCK,WidgetWallpaperColors.destination(automatic.copy(wallpaperTarget=WidgetWallpaperTarget.LOCK),WidgetLocation.HOME_SCREEN,home))
    }
}
