package dev.local.mediaactions

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

/** Bounded raster for RemoteViews, redrawn only on state/progress updates. No blur or animation loop. */
object WidgetProgressArtwork {
    fun draw(context: Context, icon: Int, color: Int, style: WidgetAppearance, progress: WidgetTrackProgress?): Bitmap {
        val edge = (128 * context.resources.displayMetrics.density).toInt().coerceIn(128, 512)
        val bitmap = Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(edge / 128f, edge / 128f)
        val width = (style.buttonBorderWidthDp * 2f + 2f).coerceIn(4f, 18f)
        val inset = maxOf(10f, width + 4f)
        val radius = 64f - inset
        val oval = RectF(inset, inset, 128f - inset, 128f - inset)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = 0x55FFFFFF; strokeWidth = width; this.style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        }
        canvas.drawOval(oval, paint)
        val fraction = progress?.fraction?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)
        if (fraction != null) {
            paint.color = Color.WHITE
            canvas.drawArc(oval, -90f, 360f * fraction, false, paint)
            val angle = Math.toRadians((fraction * 360 - 90).toDouble())
            val x = 64f + radius * cos(angle).toFloat(); val y = 64f + radius * sin(angle).toFloat()
            paint.style = Paint.Style.FILL; paint.color = Color.WHITE
            canvas.drawCircle(x, y, width + 3f, paint)
            paint.color = 0xFF26313B.toInt()
            canvas.drawCircle(x, y, width + 1f, paint)
        }
        val halfIcon = (50f * (style.iconScalePercent / 100f)).coerceIn(10f, 46f)
        if (style.iconShadow) {
            canvas.drawBitmap(WidgetIconArtwork.shadowed(context, icon, color), null,
                RectF(64 - halfIcon, 64 - halfIcon, 64 + halfIcon, 64 + halfIcon), null)
        } else context.getDrawable(icon)?.mutate()?.apply {
            setTint(color)
            setBounds((64 - halfIcon).toInt(), (64 - halfIcon).toInt(), (64 + halfIcon).toInt(), (64 + halfIcon).toInt())
            draw(canvas)
        }
        return bitmap
    }
}
