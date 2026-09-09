package io.github.mzuhairkhan.pause

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.roundToInt

/**
 * Builds the widget's [RemoteViews] from a [WidgetSize] and a [PauseSnapshot]. State/size
 * combinations render as:
 *
 * | State | glyph | label | chrono | static | chips (large) | cancel (large) |
 * |---|---|---|---|---|---|---|
 * | Idle | idle icon | gone | gone | headline | visible | gone |
 * | Running, countdown on | idle icon | "Time remaining" | armed | gone | gone | visible |
 * | Running, countdown off | draining bitmap | "Time remaining" | gone | gone | gone | visible |
 * | Break | idle icon | gone | gone | "Taking a break" | gone | gone |
 *
 * The small layout has none of label/chrono/static/chips/cancel; it is glyph-only regardless
 * of [SettingsStore.showCountdown], matching [WidgetSize.SMALL]'s own contract.
 */
object PauseWidgetRenderer {
    private const val GLYPH_SIZE_DP = 28
    private const val MAX_GLYPH_PX = 256

    private const val REQ_WIDGET_OPEN = 110
    private const val REQ_QUICK_START_5 = 111
    private const val REQ_QUICK_START_10 = 112
    private const val REQ_QUICK_START_15 = 113
    private const val REQ_CANCEL = 114

    fun build(context: Context, size: WidgetSize, snapshot: PauseSnapshot): RemoteViews {
        val now = System.currentTimeMillis()
        val state = WidgetModel.state(now, snapshot.timerEndMillis, snapshot.breakUntilMillis)
        val layout = when (size) {
            WidgetSize.SMALL -> R.layout.widget_pause_small
            WidgetSize.MEDIUM -> R.layout.widget_pause_medium
            WidgetSize.LARGE -> R.layout.widget_pause_large
        }
        val rv = RemoteViews(context.packageName, layout)
        val accent = SettingsStore.accentColor(context)
        val ctx = LocaleSupport.wrap(context)

        rv.setOnClickPendingIntent(R.id.widget_root, openPickerIntent(context))
        renderGlyph(context, rv, size, state, snapshot, now, accent)
        if (size != WidgetSize.SMALL) {
            renderText(context, ctx, rv, state, snapshot, now, accent)
        }
        if (size == WidgetSize.LARGE) {
            renderActions(context, ctx, rv, state)
        }
        return rv
    }

    private fun renderGlyph(
        context: Context,
        rv: RemoteViews,
        size: WidgetSize,
        state: PauseUiState,
        snapshot: PauseSnapshot,
        now: Long,
        accent: Int
    ) {
        // Small carries no text at all, so the glyph is the only thing that can say a timer is
        // running: it draws the draining hourglass whatever showCountdown says, or it would be
        // pixel-identical to idle. Bigger sizes hand that job to the Chronometer when it's on.
        val drainingGlyph = state == PauseUiState.RUNNING &&
            (size == WidgetSize.SMALL || !SettingsStore.showCountdown(context))
        if (drainingGlyph) {
            val progress = progressRemaining(snapshot, now)
            rv.setImageViewBitmap(R.id.widget_glyph, hourglassBitmap(context, progress, accent))
        } else {
            rv.setImageViewResource(R.id.widget_glyph, R.drawable.ic_stopwatch)
            rv.setInt(R.id.widget_glyph, "setColorFilter", accent)
        }
    }

    private fun renderText(
        context: Context,
        localizedContext: Context,
        rv: RemoteViews,
        state: PauseUiState,
        snapshot: PauseSnapshot,
        now: Long,
        accent: Int
    ) {
        rv.setTextColor(R.id.widget_chrono, accent)
        when (state) {
            PauseUiState.IDLE -> {
                setLabelVisible(rv, false, "")
                setChronoArmed(rv, false, 0L)
                setStaticVisible(rv, true, localizedContext.getString(R.string.picker_title))
            }
            PauseUiState.RUNNING -> {
                setLabelVisible(rv, true, localizedContext.getString(R.string.picker_remaining_label))
                setStaticVisible(rv, false, "")
                if (SettingsStore.showCountdown(context)) {
                    val base = ChronometerBase.forDeadline(
                        snapshot.timerEndMillis,
                        now,
                        SystemClock.elapsedRealtime()
                    )
                    setChronoArmed(rv, true, base)
                } else {
                    setChronoArmed(rv, false, 0L)
                }
            }
            PauseUiState.BREAK -> {
                setLabelVisible(rv, false, "")
                setChronoArmed(rv, false, 0L)
                setStaticVisible(rv, true, localizedContext.getString(R.string.block_title))
            }
        }
    }

    private fun setLabelVisible(rv: RemoteViews, visible: Boolean, text: String) {
        rv.setViewVisibility(R.id.widget_label, if (visible) View.VISIBLE else View.GONE)
        rv.setTextViewText(R.id.widget_label, text)
    }

    /** [base] is only meaningful when [armed]; order matters, see [ChronometerBase]'s KDoc. */
    private fun setChronoArmed(rv: RemoteViews, armed: Boolean, base: Long) {
        rv.setChronometerCountDown(R.id.widget_chrono, true)
        if (armed) {
            rv.setChronometer(R.id.widget_chrono, base, null, true)
        } else {
            rv.setChronometer(R.id.widget_chrono, SystemClock.elapsedRealtime(), null, false)
        }
        rv.setViewVisibility(R.id.widget_chrono, if (armed) View.VISIBLE else View.GONE)
    }

    private fun setStaticVisible(rv: RemoteViews, visible: Boolean, text: String) {
        rv.setViewVisibility(R.id.widget_static, if (visible) View.VISIBLE else View.GONE)
        rv.setTextViewText(R.id.widget_static, text)
    }

    private fun renderActions(context: Context, localizedContext: Context, rv: RemoteViews, state: PauseUiState) {
        val idle = state == PauseUiState.IDLE
        val running = state == PauseUiState.RUNNING
        rv.setViewVisibility(R.id.widget_chips, if (idle) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.widget_cancel, if (running) View.VISIBLE else View.GONE)
        if (idle) {
            rv.setTextViewText(R.id.widget_chip_5, localizedContext.getString(R.string.unit_minutes_short, 5))
            rv.setTextViewText(R.id.widget_chip_10, localizedContext.getString(R.string.unit_minutes_short, 10))
            rv.setTextViewText(R.id.widget_chip_15, localizedContext.getString(R.string.unit_minutes_short, 15))
            rv.setOnClickPendingIntent(R.id.widget_chip_5, quickStartIntent(context, 5, REQ_QUICK_START_5))
            rv.setOnClickPendingIntent(R.id.widget_chip_10, quickStartIntent(context, 10, REQ_QUICK_START_10))
            rv.setOnClickPendingIntent(R.id.widget_chip_15, quickStartIntent(context, 15, REQ_QUICK_START_15))
        }
        if (running) {
            rv.setTextViewText(R.id.widget_cancel, localizedContext.getString(R.string.picker_cancel))
            rv.setOnClickPendingIntent(R.id.widget_cancel, cancelIntent(context))
        }
    }

    private fun progressRemaining(snapshot: PauseSnapshot, now: Long): Float {
        val span = snapshot.timerEndMillis - snapshot.timerStartMillis
        if (span <= 0L) return 0f
        val left = (snapshot.timerEndMillis - now).coerceAtLeast(0L)
        return (left.toFloat() / span).coerceIn(0f, 1f)
    }

    /**
     * Rasterizes a fresh [HourglassDrawable] pre-tinted with [accent]. Never shares
     * `OverlayService`'s own hourglass instance -- its bounds are owned by the live bubble.
     */
    private fun hourglassBitmap(context: Context, progress: Float, accent: Int): Bitmap {
        val density = context.resources.displayMetrics.density
        val px = (GLYPH_SIZE_DP * density).roundToInt().coerceIn(24, MAX_GLYPH_PX)
        return HourglassDrawable(accent).apply { setProgress(progress) }.toBitmap(px, px, Bitmap.Config.ARGB_8888)
    }

    private fun openPickerIntent(context: Context): PendingIntent {
        return PendingIntent.getActivity(
            context,
            REQ_WIDGET_OPEN,
            Intent(context, PauseLaunchActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun quickStartIntent(context: Context, minutes: Int, requestCode: Int): PendingIntent {
        val intent = Intent(context, PauseActionReceiver::class.java).apply {
            action = PauseActionReceiver.ACTION_QUICK_START
            // A distinct data Uri per chip, so three chips sharing similar extras can never
            // collapse into one PendingIntent identity -- filterEquals ignores extras entirely,
            // only the Intent's own filter fields (data included) count.
            data = Uri.parse("pause://quick-start/$minutes")
            putExtra(PauseActionReceiver.EXTRA_MINUTES, minutes)
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun cancelIntent(context: Context): PendingIntent {
        val intent = Intent(context, PauseActionReceiver::class.java).apply {
            action = PauseActionReceiver.ACTION_CANCEL
        }
        return PendingIntent.getBroadcast(
            context, REQ_CANCEL, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
