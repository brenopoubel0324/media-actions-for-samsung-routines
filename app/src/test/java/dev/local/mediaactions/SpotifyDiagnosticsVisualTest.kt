package dev.local.mediaactions

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.TextView
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
@Config(sdk = [33], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SpotifyDiagnosticsVisualTest {
    @Test fun actualSettingsShowTheLastTerminalReasonWithoutClaimingPlaybackAndExportPreview() {
        val app = RuntimeEnvironment.getApplication()
        SpotifyPlaybackResumer().resume(app, { PlayerStatus.NoActiveSession }) { }
        Robolectric.buildActivity(MainActivity::class.java).setup().use { activity ->
            val label = activity.get().findViewById<TextView>(R.id.spotify_resume_status)
            assertEquals(View.VISIBLE, label.visibility)
            assertEquals(app.getString(R.string.spotify_resume_last, app.getString(R.string.spotify_resume_unavailable)), label.text.toString())
            val root = activity.get().window.decorView
            root.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 480, 960)
            val image = Bitmap.createBitmap(480, 960, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(image))
            val out = File("build/outputs/widget-previews/spotify-diagnostico.png")
            requireNotNull(out.parentFile).mkdirs()
            out.outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        }
    }
}
