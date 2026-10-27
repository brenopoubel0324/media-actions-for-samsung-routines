package dev.local.mediaactions

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.LruCache
import kotlin.math.roundToInt

/** Software-rendered shadows work in RemoteViews without an opaque button background. */
object WidgetIconArtwork {
    private val shadows = LruCache<String, Bitmap>(30)

    fun shadowed(context: Context, resource: Int, color: Int): Bitmap {
        val key = "$resource:$color"
        shadows.get(key)?.let { return it }
        val drawable = context.getDrawable(resource)!!.mutate()
        val factor = 160f / maxOf(drawable.intrinsicWidth, drawable.intrinsicHeight).coerceAtLeast(1)
        val width = (drawable.intrinsicWidth * factor).roundToInt().coerceAtLeast(1)
        val height = (drawable.intrinsicHeight * factor).roundToInt().coerceAtLeast(1)
        val artwork = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawable.apply {
            setTint(color)
            setBounds(0, 0, artwork.width, artwork.height)
            draw(Canvas(artwork))
        }
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = PorterDuffColorFilter(Color.argb(150, 0, 0, 0), PorterDuff.Mode.SRC_IN)
            maskFilter = BlurMaskFilter(7f, BlurMaskFilter.Blur.NORMAL)
        }
        Canvas(result).apply {
            drawBitmap(artwork, 0f, 4f, shadow)
            drawBitmap(artwork, 0f, 0f, null)
        }
        shadows.put(key, result)
        return result
    }
}
