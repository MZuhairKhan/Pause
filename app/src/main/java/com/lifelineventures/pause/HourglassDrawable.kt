package com.lifelineventures.pause

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.min

/**
 * An hourglass that drains as a timer runs down. [progress] is the fraction of time
 * *remaining* (1 = just started, 0 = finished). Driving it from the per-second ticker
 * makes the bubble cycle smoothly through every fill level without a frame for each.
 *
 * Two touches make it read as a real hourglass rather than a progress bar:
 *  - The visible fill is remapped to [[END_FILL], [START_FILL]] — it starts a touch
 *    below full and stops just shy of empty, so it never looks like a static glyph.
 *  - The bulbs are conical (wide at the cap, narrow at the neck), so for a steady flow
 *    the sand *surface* tracks the square root of the remaining volume: it falls slowly
 *    while the wide part drains, then rushes as it nears the neck.
 *
 * Drawn pure white to match the rest of the bubble icon set; legibility over light
 * backgrounds comes from the soft drop shadow [ShadowDrawable] paints beneath it.
 */
class HourglassDrawable(glyphColor: Int = 0xFFFFFFFF.toInt()) : Drawable() {

    private var progress = 1f

    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = glyphColor
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val sandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = glyphColor
    }
    private val streamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = glyphColor
        strokeCap = Paint.Cap.ROUND
    }

    private val topGlass = Path()
    private val bottomGlass = Path()
    private val clip = Path()

    /** Sets the remaining-time fraction (0..1); redraws only when it actually moves. */
    fun setProgress(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        if (clamped != progress) {
            progress = clamped
            invalidateSelf()
        }
    }

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        super.onBoundsChange(bounds)
        rebuildGlass()
    }

    private fun rebuildGlass() {
        val b = bounds
        if (b.isEmpty) return
        val side = min(b.width(), b.height()).toFloat()
        glassPaint.strokeWidth = side * 0.06f
        streamPaint.strokeWidth = side * 0.045f

        val cx = b.exactCenterX()
        val capInset = side * 0.14f
        val sideInset = side * 0.20f
        val glassTop = b.top + capInset
        val glassBottom = b.bottom - capInset
        val neckY = (glassTop + glassBottom) / 2f
        val halfW = side / 2f - sideInset
        val neckHalf = side * 0.045f

        topGlass.reset()
        topGlass.moveTo(cx - halfW, glassTop)
        topGlass.lineTo(cx + halfW, glassTop)
        topGlass.lineTo(cx + neckHalf, neckY)
        topGlass.lineTo(cx - neckHalf, neckY)
        topGlass.close()

        bottomGlass.reset()
        bottomGlass.moveTo(cx - neckHalf, neckY)
        bottomGlass.lineTo(cx + neckHalf, neckY)
        bottomGlass.lineTo(cx + halfW, glassBottom)
        bottomGlass.lineTo(cx - halfW, glassBottom)
        bottomGlass.close()
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val side = min(b.width(), b.height()).toFloat()
        val cx = b.exactCenterX()
        val capInset = side * 0.14f
        val glassTop = b.top + capInset
        val glassBottom = b.bottom - capInset
        val neckY = (glassTop + glassBottom) / 2f

        // Remaining volume in the top bulb, remapped so it never reads as fully
        // full or fully empty; the surface tracks its square root for the conical taper.
        val surface = HourglassMath.surface(progress)

        // Top sand settles against the neck; its surface descends toward it.
        val topSurfaceY = neckY - surface * (neckY - glassTop)
        fillBulb(canvas, topGlass, topSurfaceY, neckY)

        // Bottom sand piles up from the base as the top empties (its void is the mirror).
        val bottomSurfaceY = neckY + surface * (glassBottom - neckY)
        fillBulb(canvas, bottomGlass, bottomSurfaceY, glassBottom)

        // A falling stream — the glyph is always mid-flow now, so always draw it.
        canvas.drawLine(cx, neckY, cx, bottomSurfaceY, streamPaint)

        // Glass outline + caps on top so the sand reads as contained.
        canvas.drawPath(topGlass, glassPaint)
        canvas.drawPath(bottomGlass, glassPaint)
        val halfCap = side / 2f - side * 0.14f
        canvas.drawLine(cx - halfCap, glassTop, cx + halfCap, glassTop, glassPaint)
        canvas.drawLine(cx - halfCap, glassBottom, cx + halfCap, glassBottom, glassPaint)
    }

    /** Clips to a bulb path and paints the sand band between [top] and [bottom]. */
    private fun fillBulb(canvas: Canvas, bulb: Path, top: Float, bottom: Float) {
        if (bottom - top <= 0.5f) return
        canvas.save()
        clip.reset()
        clip.addPath(bulb)
        canvas.clipPath(clip)
        val rect = RectF(bounds.left.toFloat(), top, bounds.right.toFloat(), bottom)
        canvas.drawRect(rect, sandPaint)
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
