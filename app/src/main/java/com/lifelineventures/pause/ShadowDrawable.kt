package com.lifelineventures.pause

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import kotlin.math.ceil

/**
 * Wraps an icon and paints a soft, blurred drop shadow of its silhouette beneath it, so a
 * pure-white glyph stays legible over light backgrounds the way Instagram's overlay icons
 * do. The shadow follows the icon's alpha — around the outline and through any hollows.
 *
 * The blur is baked into a bitmap whenever the bounds change, so [draw] is just two bitmap
 * blits and works regardless of hardware acceleration (a [BlurMaskFilter] applied live to a
 * path on a hardware canvas is unreliable). The wrapped drawable's invalidations are
 * forwarded, so animated content (the draining hourglass) still updates live; only the
 * shadow itself is snapshotted, which is fine because the outer silhouette barely moves.
 */
class ShadowDrawable(
    private val content: Drawable,
    private val blurRadiusPx: Float,
    private val shadowColor: Int,
    private val dyPx: Float = 0f
) : Drawable() {

    /** Margin reserved inside the bounds so the blurred shadow isn't clipped at the edges. */
    private val inset: Int = ceil(blurRadiusPx + dyPx).toInt()

    private var shadow: Bitmap? = null
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = shadowColor }

    init {
        content.callback = object : Callback {
            // Rebuild the shadow from the content's *current* silhouette so it tracks animated
            // content (the draining hourglass): emptied regions have no alpha, hence no shadow,
            // instead of leaving a stale dark blob where the sand used to be.
            override fun invalidateDrawable(who: Drawable) {
                rebuildShadow(bounds.width(), bounds.height())
                invalidateSelf()
            }
            override fun scheduleDrawable(who: Drawable, what: Runnable, time: Long) =
                scheduleSelf(what, time)
            override fun unscheduleDrawable(who: Drawable, what: Runnable) =
                unscheduleSelf(what)
        }
    }

    override fun onBoundsChange(bounds: Rect) {
        // Lay the icon out in local (0-based) coordinates, inset so its shadow has room.
        content.setBounds(inset, inset, bounds.width() - inset, bounds.height() - inset)
        rebuildShadow(bounds.width(), bounds.height())
    }

    private fun rebuildShadow(width: Int, height: Int) {
        shadow?.recycle()
        shadow = null
        if (width <= 0 || height <= 0 || content.bounds.isEmpty) return

        val base = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        content.draw(Canvas(base))
        val alpha = base.extractAlpha()
        val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            maskFilter = BlurMaskFilter(blurRadiusPx, BlurMaskFilter.Blur.NORMAL)
        }
        val offset = IntArray(2)
        val blurred = alpha.extractAlpha(blurPaint, offset)

        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(blurred, offset[0].toFloat(), offset[1].toFloat() + dyPx, shadowPaint)

        base.recycle()
        alpha.recycle()
        blurred.recycle()
        shadow = out
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        canvas.save()
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        shadow?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        content.draw(canvas)
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {
        content.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        content.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
