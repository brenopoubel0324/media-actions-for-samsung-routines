package dev.local.mediaactions

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ClipboardManager
import android.content.ComponentName
import android.os.Bundle
import android.widget.TextView
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33,36],qualifiers="pt-rBR")
class LocalDiagnosticsTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private val prefs get()=app.getSharedPreferences("spotify_resume",0)
    @Test fun legacyAndMalformedValuesStayReadableWithoutInventingCompletionTime() {
        prefs.edit().putString("status","REJECTED").apply()
        assertEquals(SpotifyPlaybackResumer.Status.REJECTED,SpotifyPlaybackResumer.lastStatus(app))
        assertNull(SpotifyPlaybackResumer.lastCompletion(app).completedAtEpochMs)
        assertTrue(LocalDiagnostics.spotifyLine(app).contains("indisponível"))
        prefs.edit().putInt("status",7).putString("completed_at_epoch_ms","bad").apply()
        assertEquals(SpotifyPlaybackResumer.Status.IDLE,SpotifyPlaybackResumer.lastStatus(app))
        assertNull(SpotifyPlaybackResumer.lastCompletion(app).completedAtEpochMs)
        for (status in listOf("CONNECTING","BUSY","invalid")) {
            prefs.edit().putString("status",status).putLong("completed_at_epoch_ms",1000).apply()
            assertEquals(SpotifyPlaybackResumer.Status.IDLE,SpotifyPlaybackResumer.lastStatus(app))
            assertNull(SpotifyPlaybackResumer.lastCompletion(app).completedAtEpochMs)
        }
    }
    @Test fun calendarTimeUsesCurrentDeviceZoneAndLocale() {
        val previous=TimeZone.getDefault()
        try {
            val time=1793372400000L
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));val utc=LocalDiagnostics.completionTime(app,time)
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));val tokyo=LocalDiagnostics.completionTime(app,time)
            assertNotEquals(utc,tokyo)
            assertTrue(utc.contains("2026"));assertTrue(tokyo.contains("2026"))
        } finally { TimeZone.setDefault(previous) }
    }
    @Test fun copyIsExplicitAndIncludesOwnedWidgetTechnicalDataOnly() {
        val manager=AppWidgetManager.getInstance(app)
        val id=shadowOf(manager).createWidget(MediaWidgetProvider::class.java,R.layout.widget_media_controls)
        shadowOf(manager).addBoundWidget(id,AppWidgetProviderInfo().apply {provider=ComponentName(app,MediaWidgetProvider::class.java)})
        manager.updateAppWidgetOptions(id,Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY,AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,180);putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,56)
        })
        prefs.edit().putString("status","REJECTED").putLong("completed_at_epoch_ms",1793372400000L).apply()
        val clipboard=app.getSystemService(ClipboardManager::class.java)
        assertFalse(clipboard.hasPrimaryClip())
        Robolectric.buildActivity(MainActivity::class.java).setup().use { c ->
            assertFalse(clipboard.hasPrimaryClip())
            c.get().findViewById<View>(R.id.settings_help).performClick()
            c.get().findViewById<View>(R.id.diagnostic_copy).performClick()
            val text=clipboard.primaryClip!!.getItemAt(0).text.toString()
            assertTrue(text.contains("Android"));assertTrue(text.contains("REJECTED"))
            assertTrue(text.contains("Widget #$id"));assertTrue(text.contains("hostCategory=2"));assertTrue(text.contains("180.0 x 56.0"))
            assertTrue(text.contains("One UI"));assertFalse(text.contains("sessionToken"));assertFalse(text.contains("nodeId"))
            assertTrue(c.get().findViewById<TextView>(R.id.spotify_resume_status).text.contains("Última tentativa concluída"))
        }
    }
}
