package com.lifelineventures.pause

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.min

/**
 * A thin circular outline tracing the bubble's footprint, shown behind the countdown
 * number so the digits read as sitting inside the bubble rather than floating loose.
 *
 * Drawn pure white to match the rest of the bubble icon set; legibility over light
 * backgrounds comes from the soft drop shadow [ShadowDrawable] paints beneath it, exactly
 * as for the stopwatch and hourglass glyphs. The stroke is a fraction of the bubble's side
 * so the ring stays equally thin at every bubble size.
 */
class RingDrawable(ringColor: Int = 0xFFFFFFFF.toInt()) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ringColor
    }
    private val oval = RectF()

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        super.onBoundsChange(bounds)
        rebuild()
    }

    private fun rebuild() {
        val b = bounds
        if (b.isEmpty) return
        val side = min(b.width(), b.height()).toFloat()
        paint.strokeWidth = side * RING_STROKE_FRACTION
        // Inset by half the stroke so the outline sits fully inside the bounds, keeping the
        // margin ShadowDrawable reserves for the blur unclipped.
        val half = paint.strokeWidth / 2f
        oval.set(b.left + half, b.top + half, b.right - half, b.bottom - half)
    }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        canvas.drawOval(oval, paint)
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        /** Stroke thickness as a fraction of the bubble's side — thin, matched to the icon set. */
        const val RING_STROKE_FRACTION = 0.05f
    }
}
