package dev.local.mediaactions

import android.util.SizeF

/** Real host space must fit three independent 48dp targets before controls are exposed. */
internal object PlaybackVolumeLayout {
    fun isUsable(size: SizeF?): Boolean = size != null && size.width.isFinite() && size.height.isFinite() &&
        ((size.width >= 144f && size.height >= 48f) || (size.height >= 144f && size.width >= 48f))
}
