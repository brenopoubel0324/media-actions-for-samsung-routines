package dev.local.mediaactions

import android.content.Context
import android.media.AudioManager
import android.widget.Toast

/** Exactly one local music-stream adjustment per gesture; no media lookup or retry. */
internal object VolumeActions {
    fun step(context: Context, raise: Boolean) {
        try {
            val audio = context.getSystemService(AudioManager::class.java)
            if (audio.isVolumeFixed) {
                Toast.makeText(context, R.string.volume_fixed, Toast.LENGTH_SHORT).show()
                return
            }
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                if (raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0)
        } catch (_: RuntimeException) {
            Toast.makeText(context, R.string.volume_failed, Toast.LENGTH_SHORT).show()
        }
    }
}
