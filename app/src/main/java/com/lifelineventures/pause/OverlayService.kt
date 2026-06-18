package com.lifelineventures.pause

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.NumberPicker
import android.widget.Switch
import android.widget.TextView
import android.widget.TimePicker
import android.Manifest
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager

    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var bubbleIcon: ImageView? = null
    private var bubbleCountdown: TextView? = null
    private var pickerView: View? = null

    /** Live "time remaining" label inside the picker's active panel, when shown. */
    private var pickerRemaining: TextView? = null

    /** Drag-to-dismiss target shown while the bubble is being dragged. */
    private var dismissTargetView: View? = null
    private var overDismiss = false
    private var snapAnimator: ValueAnimator? = null

    /** Full-screen breathing wind-down shown when a timer fires (the default stop mode). */
    private var breathingView: View? = null
    private var breathingAnimator: ValueAnimator? = null

    /** Held while the wind-down is up, to pause other apps' media; null when not muting. */
    private var audioFocusRequest: AudioFocusRequest? = null

    /** Media-stream volume saved before we zero it; -1 when we aren't hard-muting. */
    private var savedMusicVolume = -1

    /** Full-screen cover shown over a blocked app during a "Stop for now" break. */
    private var blockView: View? = null
    private var blockRemaining: TextView? = null
    private var blockSubtitle: TextView? = null

    /** Wall-clock end of the active app-blocking break, or 0 when no break is running. */
    private var blockUntilMillis = 0L

    /** Packages covered for the duration of the current break. */
    private var blockedPackages: Set<String> = emptySet()

    /** Package currently shown on the cover, to avoid relabelling it every poll. */
    private var coveredPackage: String? = null

    private val blockHandler = Handler(Looper.getMainLooper())

    /** Background looper for the usage-stats query, which can be slow enough to jank the UI. */
    private var pollThread: HandlerThread? = null
    private var pollHandler: Handler? = null

    private val blockRunnable = object : Runnable {
        override fun run() {
            val remaining = blockUntilMillis - System.currentTimeMillis()
            if (remaining <= 0L) {
                stopBreak()
                return
            }
            updateBlockCountdown(remaining)
            // Resolve the foreground app off the main thread (the usage query can take tens
            // of ms), then apply the cover/uncover decision back on the main thread, where
            // all WindowManager work must happen.
            val poll = pollHandler
            if (poll != null) {
                poll.post {
                    val foreground = currentForegroundApp()
                    blockHandler.post { applyForeground(foreground) }
                }
            } else {
                applyForeground(currentForegroundApp())
            }
            blockHandler.postDelayed(this, BLOCK_POLL_MS)
        }
    }

    /** Covers a blocked foreground app or removes the cover; main-thread only. */
    private fun applyForeground(foreground: String?) {
        // The break may have ended while the background query was in flight.
        if (blockUntilMillis == 0L) return
        if (foreground != null && foreground in blockedPackages) {
            showBlockOverlay(foreground)
        } else {
            hideBlockOverlay()
        }
    }

    /** Wall-clock end time of the active timer, or 0 when idle. */
    private var endTimeMillis = 0L

    /** Wall-clock start of the active timer; with [endTimeMillis] it gives the drain fraction. */
    private var startTimeMillis = 0L

    /** The draining-hourglass glyph shown on the bubble while a timer runs (when the number is off). */
    private var hourglass: HourglassDrawable? = null

    /** Last minutes-remaining value pushed to the notification, to avoid per-second reposts. */
    private var lastNotifiedMinute = -1

    /** Last duration chosen on the custom scroll wheel. */
    private var customMinutes = 20

    /**
     * Bubble position as a fraction (0..1) of the draggable area. Storing it relative to
     * the screen — rather than as absolute pixels — keeps it at the same on-screen spot
     * in every orientation (e.g. right-edge-middle stays right-edge-middle) instead of
     * drifting to a different edge depending on which way you rotate.
     */
    private var posFractionX = DEFAULT_X_FRACTION
    private var posFractionY = DEFAULT_Y_FRACTION

    private val tickHandler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            val rawRemaining = endTimeMillis - System.currentTimeMillis()
            val remaining = rawRemaining.coerceAtLeast(0)
            updateCountdown(remaining)
            val minutesLeft = ceil(remaining / 60000.0).toInt()
            if (minutesLeft != lastNotifiedMinute) {
                lastNotifiedMinute = minutesLeft
                updateNotification()
            }
            when {
                rawRemaining > 0 -> tickHandler.postDelayed(this, 1000L)
                // The scheduled alarm should have fired the wind-down by now. If it didn't
                // (some OEMs silently drop alarms under battery management), fire it here so
                // the timer never just expires unnoticed. resetToIdle() clears the timer the
                // same way the real fired path does, and showBreathing() no-ops if it's up.
                endTimeMillis > 0L && breathingView == null && blockUntilMillis == 0L -> {
                    resetToIdle()
                    showBreathing()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        ensureChannel(this)
        restoreStrandedVolume()
        _running.value = true
    }

    /**
     * If a previous session muted media and was killed before it could restore the volume
     * (force-stop, low memory), the pre-mute level is still recorded — put it back now so
     * the user isn't left at zero. Runs before any new mute, so it can't clobber a fresh one.
     */
    private fun restoreStrandedVolume() {
        val stranded = SettingsStore.mutedVolume(this)
        if (stranded < 0) return
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, stranded, 0)
        } catch (e: SecurityException) {
            // Leave the volume as-is; the user can adjust it manually.
        }
        SettingsStore.setMutedVolume(this, -1)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Some OEMs (or a stricter Android 14+ state) can reject the foreground start even
            // from a notification action. Degrade gracefully instead of crashing: stop the
            // service — onDestroy restores the persistent "Start" notification to retry from.
            stopSelf()
            return START_NOT_STICKY
        }

        // A bubble-metrics refresh (alignment preset changed in the app) just re-applies size
        // and padding to the live bubble, without disturbing a running timer.
        if (intent?.action == ACTION_REFRESH_BUBBLE && bubbleView != null) {
            applyBubbleMetrics()
            return START_STICKY
        }

        showBubble()
        // A new (or restarted) service session always starts idle — a prior timer is not
        // resumed, and any leftover alarm is cancelled. Only the bubble location persists.
        hidePicker()
        resetToIdle()
        if (intent?.action == ACTION_TIMER_FIRED) {
            showBreathing()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val view = bubbleView ?: return
        val params = bubbleParams ?: return
        // Re-clamp on the next frame, once the display metrics have settled for the new
        // orientation. The bubble keeps the same relative spot rather than drifting.
        view.post {
            if (bubbleView !== view) return@post
            applyBubblePosition(params)
            safeUpdateViewLayout(view, params)
        }
    }

    override fun onDestroy() {
        _running.value = false
        // A manual stop cancels any pending timer so it can't fire after the overlay is gone.
        cancelPendingAlarm()
        snapAnimator?.cancel()
        stopTicker()
        stopBreak()
        hidePicker()
        hideDismissTarget()
        hideBreathing()
        removeBubble()
        pollThread?.quitSafely()
        pollThread = null
        pollHandler = null
        // Detach (don't remove) the foreground notification, then turn it into the persistent
        // "Start Pause" notification so the overlay can be relaunched from the shade.
        stopForeground(STOP_FOREGROUND_DETACH)
        showStartNotification(this)
        super.onDestroy()
    }

    // --- Overlay view helpers ---

    /**
     * Adds an overlay view, returning false (instead of crashing the service) if the
     * overlay permission was revoked between the caller's check and this call, or the
     * window token is otherwise rejected. Callers must not retain the view on false.
     */
    private fun safeAddView(view: View, params: WindowManager.LayoutParams): Boolean {
        return try {
            windowManager.addView(view, params)
            true
        } catch (e: WindowManager.BadTokenException) {
            false
        } catch (e: IllegalStateException) {
            // View was already added to a window manager.
            false
        }
    }

    /** Removes an overlay view, tolerating the case where it was already detached. */
    private fun safeRemoveView(view: View) {
        try {
            windowManager.removeView(view)
        } catch (e: IllegalArgumentException) {
            // View not attached — a teardown likely raced a delayed runnable; nothing to do.
        }
    }

    /** Updates an overlay view's layout, tolerating a revoked permission or a detached view. */
    private fun safeUpdateViewLayout(view: View, params: WindowManager.LayoutParams) {
        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: IllegalArgumentException) {
            // View no longer attached (teardown raced a drag/animation frame); ignore.
        } catch (e: WindowManager.BadTokenException) {
            // Overlay permission revoked mid-gesture; ignore rather than crash.
        }
    }

    // --- Overlay bubble ---

    @SuppressLint("ClickableViewAccessibility")
    private fun showBubble() {
        if (bubbleView != null) return
        // Defensive: the activity gates the service on this permission, but it can be
        // revoked while the service is alive. Adding the view without it would crash.
        if (!Settings.canDrawOverlays(this)) return

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_bubble, null)
        // No inner padding: the glyph fills the window (its own shadow/cap insets give margin).
        // The gap from the screen edge is the snap margin, not padding — see applyBubblePosition.
        val params = WindowManager.LayoutParams(
            bubbleSizePx(),
            bubbleSizePx(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        posFractionX = SettingsStore.bubbleFractionX(this)
        posFractionY = SettingsStore.bubbleFractionY(this)
        applyBubblePosition(params)

        view.setOnTouchListener(DragTouchListener(params))
        view.setOnClickListener {
            Haptics.tap(it)
            showPicker()
        }

        if (!safeAddView(view, params)) return
        bubbleView = view
        bubbleParams = params
        bubbleIcon = view.findViewById(R.id.bubble_icon)
        bubbleCountdown = view.findViewById(R.id.bubble_countdown)
    }

    private fun removeBubble() {
        bubbleView?.let { safeRemoveView(it) }
        bubbleView = null
        bubbleParams = null
        bubbleIcon = null
        bubbleCountdown = null
    }

    /** Bubble window size: a fraction of the screen's shorter side (per the chosen app preset),
     *  clamped to a sane dp range. */
    private fun bubbleSizePx(): Int {
        val (w, h) = screenSize()
        val basis = minOf(w, h).toFloat()
        val d = resources.displayMetrics.density
        return (SettingsStore.bubbleMetrics(this).sizeFraction * basis)
            .coerceIn(BUBBLE_MIN_DP * d, BUBBLE_MAX_DP * d).roundToInt()
    }

    /**
     * Margin between the bubble window and the screen edge when snapped — proportional to the
     * screen. Independent of [bubbleSizePx]: raising it moves the whole bubble inward without
     * resizing it (the glyph fills the window).
     */
    private fun bubbleEdgeMarginPx(): Int {
        val (w, h) = screenSize()
        val basis = minOf(w, h).toFloat()
        return (SettingsStore.bubbleMetrics(this).edgeFraction * basis).coerceAtLeast(0f).roundToInt()
    }

    /** Re-applies size + edge offset to a live bubble after the user changes the alignment. */
    private fun applyBubbleMetrics() {
        val view = bubbleView ?: return
        val params = bubbleParams ?: return
        params.width = bubbleSizePx()
        params.height = bubbleSizePx()
        applyBubblePosition(params)   // re-snaps to the edge with the new size + margin
        safeUpdateViewLayout(view, params)
        saveBubblePosition()
    }

    /**
     * Places the bubble at its stored fractional position against the current screen,
     * so it occupies the same relative spot regardless of orientation.
     */
    private fun applyBubblePosition(params: WindowManager.LayoutParams) {
        val (screenW, screenH) = screenSize()
        val size = bubbleSizePx()
        val maxX = (screenW - size).coerceAtLeast(0)
        val margin = bubbleEdgeMarginPx().coerceAtMost(maxX / 2)
        // The bubble always rests against the left or right edge (which side is kept in the
        // stored fraction), inset by the edge margin. Vertical stays a free fraction.
        params.x = if (posFractionX < 0.5f) margin else maxX - margin
        params.y = BubblePosition.toPixels(posFractionY, (screenH - size).coerceAtLeast(0))
    }

    private fun screenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val dm = resources.displayMetrics
            dm.widthPixels to dm.heightPixels
        }
    }

    private fun setBubbleActive() {
        if (SettingsStore.showCountdown(this)) {
            bubbleIcon?.visibility = View.GONE
            bubbleCountdown?.visibility = View.VISIBLE
        } else {
            // Countdown number is off: show the draining hourglass that cycles through
            // its fill levels as the timer runs down.
            val glyph = hourglass ?: HourglassDrawable().also { hourglass = it }
            bubbleIcon?.setImageDrawable(withIconShadow(glyph))
            bubbleIcon?.visibility = View.VISIBLE
            bubbleCountdown?.visibility = View.GONE
        }
        updateCountdown((endTimeMillis - System.currentTimeMillis()).coerceAtLeast(0))
        refreshTicker()
    }

    private fun setBubbleIdle() {
        bubbleCountdown?.visibility = View.GONE
        hourglass = null
        ContextCompat.getDrawable(this, R.drawable.ic_stopwatch)?.let {
            bubbleIcon?.setImageDrawable(withIconShadow(it))
        }
        bubbleIcon?.visibility = View.VISIBLE
        refreshTicker()
    }

    /** Wraps a bubble glyph in the soft white-icon drop shadow that keeps it legible on any background. */
    private fun withIconShadow(drawable: Drawable): ShadowDrawable {
        val density = resources.displayMetrics.density
        return ShadowDrawable(
            content = drawable,
            blurRadiusPx = ICON_SHADOW_BLUR_DP * density,
            shadowColor = ICON_SHADOW_COLOR,
            dyPx = ICON_SHADOW_DY_DP * density
        )
    }

    /** Fraction of the active timer still remaining (1 at the start, 0 at the end). */
    private fun progressRemaining(): Float {
        val span = endTimeMillis - startTimeMillis
        if (span <= 0L) return 0f
        val left = (endTimeMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        return (left.toFloat() / span).coerceIn(0f, 1f)
    }

    /** Runs the per-second ticker only while it has something to update. */
    private fun refreshTicker() {
        // Run while a timer is active so the notification countdown stays current, even
        // when the bubble shows the static icon and the picker is closed.
        if (endTimeMillis > System.currentTimeMillis()) {
            startTicker()
        } else {
            stopTicker()
        }
    }

    private fun updateCountdown(remainingMillis: Long) {
        hourglass?.setProgress(progressRemaining())
        val totalSeconds = (remainingMillis / 1000L).toInt()
        bubbleCountdown?.text = when {
            totalSeconds >= 3600 -> "${(totalSeconds + 3599) / 3600}h"
            totalSeconds >= 60 -> "${(totalSeconds + 59) / 60}m"
            else -> "${totalSeconds}s"
        }
        pickerRemaining?.text = formatRemainingLong(totalSeconds)
    }

    private fun formatRemainingLong(totalSeconds: Int): String = TimeFormat.remainingLong(totalSeconds)

    /**
     * Lets the user drag the bubble anywhere on screen. A press that moves less than
     * the platform touch slop is treated as a tap (so [View.performClick] still fires);
     * anything larger becomes a drag, the click is suppressed, and the resting position
     * is saved so it persists across rotations.
     */
    private inner class DragTouchListener(
        private val params: WindowManager.LayoutParams
    ) : View.OnTouchListener {
        private val touchSlop = ViewConfiguration.get(this@OverlayService).scaledTouchSlop
        private var startX = 0
        private var startY = 0
        private var startTouchX = 0f
        private var startTouchY = 0f
        private var dragging = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    snapAnimator?.cancel()
                    startX = params.x
                    startY = params.y
                    startTouchX = event.rawX
                    startTouchY = event.rawY
                    dragging = false
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startTouchX
                    val dy = event.rawY - startTouchY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                        showDismissTarget()
                    }
                    if (dragging) {
                        val (screenW, screenH) = screenSize()
                        val maxX = (screenW - view.width).coerceAtLeast(0)
                        val maxY = (screenH - view.height).coerceAtLeast(0)
                        params.x = (startX + dx.roundToInt()).coerceIn(0, maxX)
                        params.y = (startY + dy.roundToInt()).coerceIn(0, maxY)
                        safeUpdateViewLayout(view, params)
                        updateDismissProximity()
                    }
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        val dismiss = overDismiss
                        // Evaluate the spring-back zone while the target is still on-screen.
                        val nearZone = isNearDismissZone()
                        hideDismissTarget()
                        when {
                            dismiss -> stopSelf()
                            // A near-miss springs back home rather than snapping to a low edge,
                            // so "home" never drifts to the bottom.
                            nearZone -> springBackToHome(startX, startY)
                            else -> snapToEdge()
                        }
                    } else {
                        view.performClick()
                    }
                    return true
                }
            }
            return false
        }
    }

    private fun saveBubblePosition() {
        val params = bubbleParams ?: return
        val (screenW, screenH) = screenSize()
        val size = bubbleSizePx()
        posFractionX = BubblePosition.toFraction(params.x, screenW - size)
        posFractionY = BubblePosition.toFraction(params.y, screenH - size)
        SettingsStore.saveBubbleFraction(this, posFractionX, posFractionY)
    }

    // --- Drag-to-dismiss target + edge snap ---

    @SuppressLint("InflateParams")
    private fun showDismissTarget() {
        if (dismissTargetView != null) return
        val view = LayoutInflater.from(this).inflate(R.layout.dismiss_target, null)
        val size = dismissTargetSizePx()
        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dismissMarginPx()
        }
        if (!safeAddView(view, params)) return
        view.alpha = 0f
        view.animate().alpha(1f).setDuration(120L).start()
        dismissTargetView = view
        overDismiss = false
    }

    private fun hideDismissTarget() {
        dismissTargetView?.let { safeRemoveView(it) }
        dismissTargetView = null
        overDismiss = false
    }

    /**
     * Distance in screen pixels between the bubble's center and the dismiss target's center,
     * using each view's actual on-screen location so the activation circle is centered on the
     * ✕ exactly as drawn (independent of system-bar insets and window gravity).
     */
    private fun bubbleToDismissDistance(): Float {
        val target = dismissTargetView ?: return Float.MAX_VALUE
        val bubble = bubbleView ?: return Float.MAX_VALUE
        if (target.width == 0 || bubble.width == 0) return Float.MAX_VALUE
        val targetLoc = IntArray(2)
        val bubbleLoc = IntArray(2)
        target.getLocationOnScreen(targetLoc)
        bubble.getLocationOnScreen(bubbleLoc)
        val targetCenterX = targetLoc[0] + target.width / 2f
        val targetCenterY = targetLoc[1] + target.height / 2f
        val bubbleCenterX = bubbleLoc[0] + bubble.width / 2f
        val bubbleCenterY = bubbleLoc[1] + bubble.height / 2f
        return hypot(
            (bubbleCenterX - targetCenterX).toDouble(),
            (bubbleCenterY - targetCenterY).toDouble()
        ).toFloat()
    }

    /** Highlights the target (red) when the bubble is close enough to drop on it. */
    private fun updateDismissProximity() {
        val target = dismissTargetView ?: return
        val near = bubbleToDismissDistance() < dismissActivationPx()
        if (near != overDismiss) {
            overDismiss = near
            target.backgroundTintList =
                if (near) ColorStateList.valueOf(0xE0F44336.toInt()) else null
        }
    }

    /** Glides the bubble to the nearest left/right edge, then persists the resting spot. */
    private fun snapToEdge() {
        val params = bubbleParams ?: return
        val view = bubbleView ?: return
        val (screenW, _) = screenSize()
        val size = bubbleSizePx()
        val maxX = (screenW - size).coerceAtLeast(0)
        val margin = bubbleEdgeMarginPx().coerceAtMost(maxX / 2)
        val targetX = if (params.x + size / 2 < screenW / 2) margin else maxX - margin

        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofInt(params.x, targetX).apply {
            duration = 180L
            addUpdateListener { animation ->
                if (bubbleView !== view) return@addUpdateListener
                params.x = animation.animatedValue as Int
                safeUpdateViewLayout(view, params)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    saveBubblePosition()
                }
            })
            start()
        }
    }

    private fun dismissTargetSizePx(): Int =
        (DISMISS_SIZE_DP * resources.displayMetrics.density).roundToInt()

    private fun dismissMarginPx(): Int =
        (DISMISS_MARGIN_DP * resources.displayMetrics.density).roundToInt()

    private fun dismissActivationPx(): Float =
        dismissTargetSizePx() / 2f + bubbleSizePx() / 2f + DISMISS_SLOP_DP * resources.displayMetrics.density

    /** A wider zone around the ✕ that counts as "aiming to dismiss"; a miss here springs home. */
    private fun dismissCancelPx(): Float =
        dismissActivationPx() + DISMISS_CANCEL_EXTRA_DP * resources.displayMetrics.density

    private fun isNearDismissZone(): Boolean = bubbleToDismissDistance() < dismissCancelPx()

    /** Animates the bubble back to where it was grabbed (used for a missed dismiss). */
    private fun springBackToHome(targetX: Int, targetY: Int) {
        val params = bubbleParams ?: return
        val view = bubbleView ?: return
        val fromX = params.x
        val fromY = params.y
        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 180L
            addUpdateListener { animation ->
                if (bubbleView !== view) return@addUpdateListener
                val fraction = animation.animatedValue as Float
                params.x = (fromX + (targetX - fromX) * fraction).roundToInt()
                params.y = (fromY + (targetY - fromY) * fraction).roundToInt()
                safeUpdateViewLayout(view, params)
            }
            start()
        }
    }

    private fun tintSwitch(switch: Switch) {
        val accent = accentColor()
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        switch.thumbTintList = ColorStateList(states, intArrayOf(accent, 0xFFECECEC.toInt()))
        switch.trackTintList = ColorStateList(
            states,
            intArrayOf((accent and 0x00FFFFFF) or 0x99000000.toInt(), 0x61FFFFFF)
        )
    }

    /** True when the overlay surfaces should render dark, honouring the in-app theme choice. */
    private fun overlayNight(): Boolean = when (SettingsStore.themeMode(this)) {
        1 -> false
        2 -> true
        else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * A context whose night mode is pinned to the app's theme choice, so the picker, breathing
     * and break-cover overlays resolve the light or dark `overlay_*` colors (and the picker's
     * day/night theme) accordingly. The floating bubble does NOT use this — it stays universal.
     */
    private fun overlayContext(): Context {
        val config = Configuration(resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                (if (overlayNight()) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        }
        return createConfigurationContext(config)
    }

    // --- Timer picker ---

    @SuppressLint("InflateParams")
    private fun showPicker() {
        if (pickerView != null) return
        if (!Settings.canDrawOverlays(this)) return

        // Inflate with the day/night overlay theme so the platform spinners and card colors
        // match the app's chosen light/dark mode.
        val themed = ContextThemeWrapper(overlayContext(), R.style.Theme_Pause_Overlay)
        val view = LayoutInflater.from(themed).inflate(R.layout.timer_picker, null)

        val title = view.findViewById<TextView>(R.id.picker_title)
        val sectionActive = view.findViewById<View>(R.id.section_active)
        val sectionSetup = view.findViewById<View>(R.id.section_setup)

        if (endTimeMillis > System.currentTimeMillis()) {
            // A timer is already running: only show the remaining time and a cancel
            // action — there is intentionally no way to start a second timer.
            title.text = getString(R.string.picker_active_title)
            sectionActive.visibility = View.VISIBLE
            sectionSetup.visibility = View.GONE
            pickerRemaining = view.findViewById(R.id.active_remaining)
            updateCountdown((endTimeMillis - System.currentTimeMillis()).coerceAtLeast(0))

            val countdownSwitch = view.findViewById<Switch>(R.id.show_countdown_switch)
            tintSwitch(countdownSwitch)
            countdownSwitch.isChecked = SettingsStore.showCountdown(this)
            countdownSwitch.setOnCheckedChangeListener { _, checked ->
                SettingsStore.setShowCountdown(this, checked)
                setBubbleActive()
            }

            view.findViewById<TextView>(R.id.btn_cancel).setOnClickListener {
                Haptics.tap(it)
                resetToIdle()
                hidePicker()
            }
        } else {
            title.text = getString(R.string.picker_title)
            sectionActive.visibility = View.GONE
            sectionSetup.visibility = View.VISIBLE
            wirePickerModes(view)
            wireDurationMode(view)
            wireAlarmMode(view)
            view.findViewById<TextView>(R.id.btn_stop_overlay).setOnClickListener {
                Haptics.tap(it)
                hidePicker()
                stopSelf()
            }
        }

        wirePickerDismiss(view)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT
        ).apply { dimAmount = 0.5f }

        if (!safeAddView(view, params)) return
        view.requestFocus()
        pickerView = view
        refreshTicker()
    }

    private fun wirePickerModes(view: View) {
        val sectionDuration = view.findViewById<View>(R.id.section_duration)
        val sectionAlarm = view.findViewById<View>(R.id.section_alarm)
        val tabDuration = view.findViewById<TextView>(R.id.tab_duration)
        val tabAlarm = view.findViewById<TextView>(R.id.tab_alarm)
        tabDuration.text = getString(R.string.picker_mode_duration)
        tabAlarm.text = getString(R.string.picker_mode_alarm)

        val accentTint = ColorStateList.valueOf(accentColor())
        // White reads on the accent-tinted (selected) tab; the unselected tab sits on the
        // neutral chip, so its text must follow the theme to stay legible in light mode.
        val onAccent = 0xFFFFFFFF.toInt()
        val onChip = ContextCompat.getColor(view.context, R.color.overlay_on_chip)
        fun selectMode(duration: Boolean) {
            sectionDuration.visibility = if (duration) View.VISIBLE else View.GONE
            sectionAlarm.visibility = if (duration) View.GONE else View.VISIBLE
            tabDuration.setBackgroundResource(if (duration) R.drawable.chip_accent_bg else R.drawable.chip_bg)
            tabAlarm.setBackgroundResource(if (duration) R.drawable.chip_bg else R.drawable.chip_accent_bg)
            tabDuration.backgroundTintList = if (duration) accentTint else null
            tabAlarm.backgroundTintList = if (duration) null else accentTint
            tabDuration.setTextColor(if (duration) onAccent else onChip)
            tabAlarm.setTextColor(if (duration) onChip else onAccent)
        }

        tabDuration.setOnClickListener {
            Haptics.tap(it)
            selectMode(true)
        }
        tabAlarm.setOnClickListener {
            Haptics.tap(it)
            selectMode(false)
        }
        selectMode(true)
    }

    private fun wireDurationMode(view: View) {
        listOf(R.id.chip_5 to 5, R.id.chip_10 to 10, R.id.chip_15 to 15).forEach { (id, minutes) ->
            view.findViewById<TextView>(id).apply {
                text = minutes.toString()
                setOnClickListener {
                    Haptics.tap(it)
                    startDurationAndClose(minutes)
                }
            }
        }

        val wheel = view.findViewById<NumberPicker>(R.id.custom_wheel).apply {
            minValue = CUSTOM_MIN
            maxValue = CUSTOM_MAX
            wrapSelectorWheel = false
            value = customMinutes.coerceIn(CUSTOM_MIN, CUSTOM_MAX)
            // Block the inner EditText from grabbing focus, which would pop a soft
            // keyboard inside the overlay; the wheel still scrolls by touch.
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            setOnValueChangedListener { _, _, newVal -> customMinutes = newVal }
        }
        view.findViewById<TextView>(R.id.btn_start_duration).apply {
            backgroundTintList = ColorStateList.valueOf(accentColor())
            setOnClickListener {
                Haptics.tap(it)
                startDurationAndClose(wheel.value)
            }
        }
    }

    private fun wireAlarmMode(view: View) {
        val timePicker = view.findViewById<TimePicker>(R.id.time_picker).apply {
            setIs24HourView(false)
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
        view.findViewById<TextView>(R.id.btn_start_alarm).apply {
            backgroundTintList = ColorStateList.valueOf(accentColor())
            setOnClickListener {
                Haptics.tap(it)
                startAlarmAndClose(timePicker.hour, timePicker.minute)
            }
        }
    }

    private fun accentColor(): Int = SettingsStore.accentColor(this)

    private fun wirePickerDismiss(view: View) {
        // Tap outside the card (the card consumes its own touches) or press BACK.
        view.setOnClickListener { hidePicker() }
        view.isFocusableInTouchMode = true
        view.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                hidePicker()
                true
            } else {
                false
            }
        }
    }

    private fun hidePicker() {
        pickerView?.let { safeRemoveView(it) }
        pickerView = null
        pickerRemaining = null
        refreshTicker()
    }

    private fun startDurationAndClose(minutes: Int) {
        scheduleTimer(System.currentTimeMillis() + minutes * 60_000L)
        hidePicker()
    }

    private fun startAlarmAndClose(hour: Int, minute: Int) {
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // If the chosen clock time has already passed today, fire tomorrow.
        if (target.timeInMillis <= System.currentTimeMillis()) {
            target.add(Calendar.DAY_OF_MONTH, 1)
        }
        scheduleTimer(target.timeInMillis)
        hidePicker()
    }

    // --- Timer scheduling ---

    private fun scheduleTimer(endMillis: Long) {
        startTimeMillis = System.currentTimeMillis()
        endTimeMillis = endMillis

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // setAlarmClock() is treated as exact without needing the SCHEDULE_EXACT_ALARM
        // permission, and it briefly allowlists us so the broadcast delivers on time.
        try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(endMillis, showActivityIntent()),
                alarmOperation()
            )
        } catch (e: SecurityException) {
            // Some OEMs restrict alarm scheduling; the per-second ticker fallback fires instead.
        }

        setBubbleActive()
        updateNotification()
    }

    /** Cancels any active timer and returns the bubble to its idle glyph. */
    private fun resetToIdle() {
        cancelPendingAlarm()
        endTimeMillis = 0L
        startTimeMillis = 0L
        lastNotifiedMinute = -1
        setBubbleIdle()
        updateNotification()
    }

    private fun cancelPendingAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(alarmOperation())
    }

    private fun startTicker() {
        tickHandler.removeCallbacks(tickRunnable)
        tickHandler.post(tickRunnable)
    }

    private fun stopTicker() {
        tickHandler.removeCallbacks(tickRunnable)
    }

    private fun alarmOperation(): PendingIntent {
        val intent = Intent(this, TimerReceiver::class.java).apply {
            action = TimerReceiver.ACTION_FIRE
        }
        return PendingIntent.getBroadcast(
            this,
            REQ_ALARM,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun showActivityIntent(): PendingIntent {
        return PendingIntent.getActivity(
            this,
            REQ_SHOW,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    // --- Breathing wind-down (default stop mode) ---

    @SuppressLint("InflateParams")
    private fun showBreathing() {
        if (breathingView != null) return
        if (!Settings.canDrawOverlays(this)) return

        val view = LayoutInflater.from(overlayContext()).inflate(R.layout.breathing, null)
        val circle = view.findViewById<View>(R.id.breathing_circle)
        circle.backgroundTintList = ColorStateList.valueOf(accentColor())
        val phase = view.findViewById<TextView>(R.id.breathing_phase)

        val actions = view.findViewById<View>(R.id.breathing_actions)
        view.findViewById<TextView>(R.id.breathing_keep).setOnClickListener {
            Haptics.tap(it)
            hideBreathing()
        }
        view.findViewById<TextView>(R.id.breathing_stop).apply {
            backgroundTintList = ColorStateList.valueOf(accentColor())
            setOnClickListener {
                Haptics.tap(it)
                stopForNow()
            }
        }
        val snoozeMinutes = SettingsStore.snoozeMinutes(this)
        view.findViewById<TextView>(R.id.breathing_snooze).apply {
            text = getString(R.string.breathing_snooze, snoozeMinutes)
            setOnClickListener {
                Haptics.tap(it)
                snoozeTimer(snoozeMinutes)
            }
        }

        // Not skippable: the full-screen view swallows taps and BACK is consumed. The
        // action buttons, revealed after the lock window, are the only way out.
        view.isFocusableInTouchMode = true
        view.setOnKeyListener { _, keyCode, _ -> keyCode == KeyEvent.KEYCODE_BACK }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )
        if (!safeAddView(view, params)) return
        view.requestFocus()
        breathingView = view
        // Silence whatever was playing (a video, music) so the wind-down isn't competing
        // with background media; focus is handed back when the wind-down closes.
        muteMedia()
        startBreathingAnimator(circle, phase)

        val lockMs = SettingsStore.lockSeconds(this).toLong() * 1000L
        view.postDelayed({
            if (breathingView === view) {
                actions.alpha = 0f
                actions.visibility = View.VISIBLE
                actions.animate().alpha(1f).setDuration(250L).start()
            }
        }, lockMs)
    }

    private fun hideBreathing() {
        breathingAnimator?.cancel()
        breathingAnimator = null
        breathingView?.let { safeRemoveView(it) }
        breathingView = null
        unmuteMedia()
    }

    /** Dismisses the wind-down and re-arms the timer so it fires again in [minutes]. */
    private fun snoozeTimer(minutes: Int) {
        hideBreathing()
        scheduleTimer(System.currentTimeMillis() + minutes * 60_000L)
    }

    /**
     * Requests exclusive transient audio focus so well-behaved apps pause their media for
     * the duration of the wind-down, and also zeroes the media stream directly. The latter
     * is what silences apps that ignore audio-focus loss (TikTok, Instagram Reels) and keep
     * playing regardless — the saved volume is restored when the wind-down closes.
     */
    private fun muteMedia() {
        if (audioFocusRequest != null) return
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { }
            .build()
        audioManager.requestAudioFocus(request)
        audioFocusRequest = request

        if (savedMusicVolume < 0) {
            try {
                savedMusicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                // Persist the pre-mute volume so a process kill mid-pause can't strand
                // the user at zero — onCreate() restores it on the next launch.
                SettingsStore.setMutedVolume(this, savedMusicVolume)
            } catch (e: SecurityException) {
                savedMusicVolume = -1
            }
        }
    }

    /** Restores media volume and hands audio focus back so media can resume after the wind-down. */
    private fun unmuteMedia() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (savedMusicVolume >= 0) {
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, savedMusicVolume, 0)
            } catch (e: SecurityException) {
                // Leave the volume as-is; the user can adjust it manually.
            }
            savedMusicVolume = -1
            SettingsStore.setMutedVolume(this, -1)
        }
        val request = audioFocusRequest ?: return
        audioManager.abandonAudioFocusRequest(request)
        audioFocusRequest = null
    }

    // --- "Stop for now" app-blocking break ---

    /**
     * The "Stop for now" action. If the user has chosen apps to block and granted usage
     * access, this starts a timed break that covers those apps when opened; otherwise it
     * just tears the overlay down (the original behaviour).
     */
    private fun stopForNow() {
        val apps = SettingsStore.blockedApps(this)
        if (apps.isEmpty() || !hasUsageAccess()) {
            stopSelf()
            return
        }
        hideBreathing()
        // The fired timer is done; return the bubble to idle while the break runs.
        resetToIdle()
        blockedPackages = apps
        blockUntilMillis = System.currentTimeMillis() + SettingsStore.blockMinutes(this) * 60_000L
        coveredPackage = null
        ensurePollThread()
        blockHandler.removeCallbacks(blockRunnable)
        blockHandler.post(blockRunnable)
        // Leave the app you were in right away. Foreground detection relies on a fresh
        // MOVE_TO_FOREGROUND event, which a long-running app (mid-scroll) won't have emitted
        // recently — so without this the cover wouldn't appear until you navigated away and
        // back. Going home stops playback now and makes re-opening a blocked app detectable.
        goHome()
    }

    /** Spins up the background looper for usage queries on first use; reused across breaks. */
    private fun ensurePollThread() {
        if (pollThread != null) return
        pollThread = HandlerThread("pause-fg-poll").also {
            it.start()
            pollHandler = Handler(it.looper)
        }
    }

    private fun stopBreak() {
        blockHandler.removeCallbacks(blockRunnable)
        blockUntilMillis = 0L
        blockedPackages = emptySet()
        hideBlockOverlay()
    }

    /** The package of the app currently in the foreground, via usage events (null if unknown). */
    @Suppress("DEPRECATION") // MOVE_TO_FOREGROUND is the constant that works back to API 26.
    private fun currentForegroundApp(): String? {
        val usage = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val now = System.currentTimeMillis()
        val events = usage.queryEvents(now - FOREGROUND_LOOKBACK_MS, now)
        val event = UsageEvents.Event()
        var latest: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                latest = event.packageName
            }
        }
        return latest
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    @SuppressLint("InflateParams")
    private fun showBlockOverlay(pkg: String) {
        // Cover the app (and mute it) if not already, then keep the subtitle current.
        if (blockView == null) {
            if (!Settings.canDrawOverlays(this)) return
            val view = LayoutInflater.from(overlayContext()).inflate(R.layout.block_overlay, null)
            val actions = view.findViewById<View>(R.id.block_actions)
            val confirm = view.findViewById<View>(R.id.block_confirm)
            view.findViewById<TextView>(R.id.block_home).apply {
                backgroundTintList = ColorStateList.valueOf(accentColor())
                setOnClickListener {
                    Haptics.tap(it)
                    goHome()
                    hideBlockOverlay()
                }
            }
            // Exitable, but gated by an "Are you sure?" so it isn't a one-tap escape.
            view.findViewById<TextView>(R.id.block_end).setOnClickListener {
                Haptics.tap(it)
                actions.visibility = View.GONE
                confirm.visibility = View.VISIBLE
            }
            view.findViewById<TextView>(R.id.block_confirm_keep).setOnClickListener {
                Haptics.tap(it)
                confirm.visibility = View.GONE
                actions.visibility = View.VISIBLE
            }
            view.findViewById<TextView>(R.id.block_confirm_end).apply {
                backgroundTintList = ColorStateList.valueOf(accentColor())
                setOnClickListener {
                    Haptics.tap(it)
                    stopBreak()
                }
            }
            view.isFocusableInTouchMode = true
            view.setOnKeyListener { _, keyCode, _ -> keyCode == KeyEvent.KEYCODE_BACK }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                0,
                PixelFormat.OPAQUE
            )
            if (!safeAddView(view, params)) return
            view.requestFocus()
            blockView = view
            blockRemaining = view.findViewById(R.id.block_remaining)
            blockSubtitle = view.findViewById(R.id.block_subtitle)
            muteMedia()
        }
        if (coveredPackage != pkg) {
            coveredPackage = pkg
            blockSubtitle?.text = getString(R.string.block_subtitle, appLabel(pkg))
        }
    }

    private fun hideBlockOverlay() {
        blockView?.let { safeRemoveView(it) }
        blockView = null
        blockRemaining = null
        blockSubtitle = null
        coveredPackage = null
        unmuteMedia()
    }

    private fun updateBlockCountdown(remainingMillis: Long) {
        val totalSeconds = (remainingMillis / 1000L).toInt()
        blockRemaining?.text = formatRemainingLong(totalSeconds)
    }

    /** A human label for [pkg], falling back to a generic string if it can't be resolved. */
    private fun appLabel(pkg: String): String {
        return try {
            val info = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            getString(R.string.block_subtitle_generic)
        }
    }

    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(home)
    }

    /** Loops a 4-7-8 cycle: grow on inhale (4s), hold (7s), shrink on exhale (8s). No numbers. */
    private fun startBreathingAnimator(circle: View, phase: TextView) {
        breathingAnimator?.cancel()
        val inhaleMs = SettingsStore.inhaleSeconds(this) * 1000f
        val holdMs = SettingsStore.holdSeconds(this) * 1000f
        val exhaleMs = SettingsStore.exhaleSeconds(this) * 1000f
        val cycle = inhaleMs + holdMs + exhaleMs
        breathingAnimator = ValueAnimator.ofFloat(0f, cycle).apply {
            duration = cycle.toLong()
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animation ->
                val t = animation.animatedValue as Float
                val (scale, label) = when {
                    t < inhaleMs ->
                        (BREATH_MIN + (1f - BREATH_MIN) * (t / inhaleMs)) to getString(R.string.breathing_in)
                    t < inhaleMs + holdMs ->
                        1f to getString(R.string.breathing_hold)
                    else ->
                        (1f - (1f - BREATH_MIN) * ((t - inhaleMs - holdMs) / exhaleMs)) to getString(R.string.breathing_out)
                }
                circle.scaleX = scale
                circle.scaleY = scale
                if (phase.text != label) phase.text = label
            }
            start()
        }
    }

    // --- Notification / foreground service ---

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    /** Formats the remaining time as e.g. "Alarm in 25 min" / "Alarm in 2h 5m". */
    private fun formatAlarmIn(remainingMillis: Long): String {
        if (remainingMillis / 1000L < 60) {
            return getString(R.string.overlay_notification_active_soon)
        }
        val totalMinutes = ceil(remainingMillis / 60000.0).toInt()
        return when {
            totalMinutes < 60 ->
                getString(R.string.overlay_notification_active_minutes, totalMinutes)
            totalMinutes % 60 == 0 ->
                getString(R.string.overlay_notification_active_hours, totalMinutes / 60)
            else ->
                getString(R.string.overlay_notification_active_hm, totalMinutes / 60, totalMinutes % 60)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Notification follows the timer: a "tap the button" hint while idle, and a live
        // "Alarm in X" countdown while a timer is active.
        val active = endTimeMillis > System.currentTimeMillis()
        val title = if (active) {
            getString(R.string.overlay_notification_title_running)
        } else {
            getString(R.string.overlay_notification_title)
        }
        val text = if (active) {
            formatAlarmIn((endTimeMillis - System.currentTimeMillis()).coerceAtLeast(0))
        } else {
            getString(R.string.overlay_notification_text)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_hourglass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        /** Whether the overlay service is currently running; observed by the setup screen. */
        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running

        private const val CHANNEL_ID = "pause_status"
        private val LEGACY_CHANNELS = listOf("overlay_service", "overlay_service_min")
        private const val NOTIFICATION_ID = 1
        // The floating bubble is sized and spaced as a fraction of the screen's shorter side
        // (the exact fractions come from the chosen app preset in SettingsStore), so it holds
        // the same look across devices. These dp bounds just keep it sane on extreme screens.
        private const val BUBBLE_MIN_DP = 40f
        private const val BUBBLE_MAX_DP = 96f

        /** Soft drop shadow that keeps the pure-white bubble glyph legible on any background. */
        private const val ICON_SHADOW_BLUR_DP = 4f
        private const val ICON_SHADOW_DY_DP = 1f
        private val ICON_SHADOW_COLOR = 0xB3000000.toInt()
        private const val DISMISS_SIZE_DP = 64f
        private const val DISMISS_MARGIN_DP = 48f
        private const val DISMISS_SLOP_DP = 16f
        private const val DISMISS_CANCEL_EXTRA_DP = 48f
        private const val DEFAULT_X_FRACTION = 1f
        private const val DEFAULT_Y_FRACTION = 0.234f
        private const val CUSTOM_MIN = 1
        private const val CUSTOM_MAX = 120
        private const val REQ_ALARM = 100
        private const val REQ_SHOW = 101
        private const val REQ_START = 102
        private const val REQ_OPEN = 103
        private const val BREATH_MIN = 0.35f

        /** How often the active break checks the foreground app. */
        private const val BLOCK_POLL_MS = 1000L
        /** How far back to scan usage events when resolving the foreground app. */
        private const val FOREGROUND_LOOKBACK_MS = 10_000L

        const val ACTION_TIMER_FIRED = "com.lifelineventures.pause.action.TIMER_FIRED"
        const val ACTION_REFRESH_BUBBLE = "com.lifelineventures.pause.action.REFRESH_BUBBLE"

        /** Re-applies the bubble's size/offset to a running overlay (no-op if not running). */
        fun refreshBubble(context: Context) {
            if (!_running.value) return
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_REFRESH_BUBBLE
            }
            context.startForegroundService(intent)
        }

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            context.stopService(intent)
        }

        fun timerFired(context: Context) {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_TIMER_FIRED
            }
            context.startForegroundService(intent)
        }

        /** Creates the LOW-importance status channel and clears out the earlier channels. */
        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            LEGACY_CHANNELS.forEach { manager.deleteNotificationChannel(it) }
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.overlay_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.overlay_channel_description)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        /**
         * Posts the persistent "Start Pause" notification shown whenever the overlay isn't
         * running, so the service can be launched from the shade — including after a reboot.
         * Tapping Start launches the foreground service, whose own notification replaces this
         * one. No-op while the overlay is running, or while notifications aren't permitted.
         */
        @SuppressLint("MissingPermission") // guarded by canPostNotifications below
        fun showStartNotification(context: Context) {
            if (_running.value || !canPostNotifications(context)) return
            ensureChannel(context)
            val startIntent = Intent(context, OverlayService::class.java)
            val startPi = PendingIntent.getForegroundService(
                context, REQ_START, startIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val openPi = PendingIntent.getActivity(
                context, REQ_OPEN, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(R.string.overlay_notification_off_title))
                .setContentText(context.getString(R.string.overlay_notification_off_text))
                .setSmallIcon(R.drawable.ic_hourglass)
                .setContentIntent(openPi)
                .addAction(0, context.getString(R.string.overlay_notification_start), startPi)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }

        private fun canPostNotifications(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
            return ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
