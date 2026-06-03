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
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
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
import androidx.core.app.NotificationCompat
import java.util.Calendar
import java.util.Locale
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
    private val blockRunnable = object : Runnable {
        override fun run() {
            val remaining = blockUntilMillis - System.currentTimeMillis()
            if (remaining <= 0L) {
                stopBreak()
                return
            }
            val foreground = currentForegroundApp()
            if (foreground != null && foreground in blockedPackages) {
                showBlockOverlay(foreground)
            } else {
                hideBlockOverlay()
            }
            updateBlockCountdown(remaining)
            blockHandler.postDelayed(this, BLOCK_POLL_MS)
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
            val remaining = (endTimeMillis - System.currentTimeMillis()).coerceAtLeast(0)
            updateCountdown(remaining)
            val minutesLeft = ceil(remaining / 60000.0).toInt()
            if (minutesLeft != lastNotifiedMinute) {
                lastNotifiedMinute = minutesLeft
                updateNotification()
            }
            if (remaining > 0) tickHandler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        _running.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
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
            windowManager.updateViewLayout(view, params)
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
        super.onDestroy()
    }

    // --- Overlay bubble ---

    @SuppressLint("ClickableViewAccessibility")
    private fun showBubble() {
        if (bubbleView != null) return
        // Defensive: the activity gates the service on this permission, but it can be
        // revoked while the service is alive. Adding the view without it would crash.
        if (!Settings.canDrawOverlays(this)) return

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_bubble, null)
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

        windowManager.addView(view, params)
        bubbleView = view
        bubbleParams = params
        bubbleIcon = view.findViewById(R.id.bubble_icon)
        bubbleCountdown = view.findViewById(R.id.bubble_countdown)
    }

    private fun removeBubble() {
        bubbleView?.let { windowManager.removeView(it) }
        bubbleView = null
        bubbleParams = null
        bubbleIcon = null
        bubbleCountdown = null
    }

    private fun bubbleSizePx(): Int = (BUBBLE_SIZE_DP * resources.displayMetrics.density).roundToInt()

    /**
     * Places the bubble at its stored fractional position against the current screen,
     * so it occupies the same relative spot regardless of orientation.
     */
    private fun applyBubblePosition(params: WindowManager.LayoutParams) {
        val (screenW, screenH) = screenSize()
        val size = bubbleSizePx()
        val maxX = (screenW - size).coerceAtLeast(0)
        val maxY = (screenH - size).coerceAtLeast(0)
        params.x = (posFractionX * maxX).roundToInt().coerceIn(0, maxX)
        params.y = (posFractionY * maxY).roundToInt().coerceIn(0, maxY)
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
            bubbleIcon?.setImageDrawable(glyph)
            bubbleIcon?.visibility = View.VISIBLE
            bubbleCountdown?.visibility = View.GONE
        }
        updateCountdown((endTimeMillis - System.currentTimeMillis()).coerceAtLeast(0))
        refreshTicker()
    }

    private fun setBubbleIdle() {
        bubbleCountdown?.visibility = View.GONE
        hourglass = null
        bubbleIcon?.setImageResource(R.drawable.ic_stopwatch)
        bubbleIcon?.visibility = View.VISIBLE
        refreshTicker()
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

    private fun formatRemainingLong(totalSeconds: Int): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%d:%02d", m, s)
        }
    }

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
                        windowManager.updateViewLayout(view, params)
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
        val maxX = (screenW - size).coerceAtLeast(1)
        val maxY = (screenH - size).coerceAtLeast(1)
        posFractionX = params.x.toFloat() / maxX
        posFractionY = params.y.toFloat() / maxY
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
        windowManager.addView(view, params)
        view.alpha = 0f
        view.animate().alpha(1f).setDuration(120L).start()
        dismissTargetView = view
        overDismiss = false
    }

    private fun hideDismissTarget() {
        dismissTargetView?.let { windowManager.removeView(it) }
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
        val maxX = (screenW - bubbleSizePx()).coerceAtLeast(0)
        val targetX = if (params.x + bubbleSizePx() / 2 < screenW / 2) 0 else maxX

        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofInt(params.x, targetX).apply {
            duration = 180L
            addUpdateListener { animation ->
                if (bubbleView !== view) return@addUpdateListener
                params.x = animation.animatedValue as Int
                windowManager.updateViewLayout(view, params)
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
                windowManager.updateViewLayout(view, params)
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

    // --- Timer picker ---

    @SuppressLint("InflateParams")
    private fun showPicker() {
        if (pickerView != null) return
        if (!Settings.canDrawOverlays(this)) return

        // Inflate with a dark theme so the platform spinners are legible on the card.
        val themed = ContextThemeWrapper(this, R.style.Theme_Pause_Overlay)
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

        windowManager.addView(view, params)
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
        fun selectMode(duration: Boolean) {
            sectionDuration.visibility = if (duration) View.VISIBLE else View.GONE
            sectionAlarm.visibility = if (duration) View.GONE else View.VISIBLE
            tabDuration.setBackgroundResource(if (duration) R.drawable.chip_accent_bg else R.drawable.chip_bg)
            tabAlarm.setBackgroundResource(if (duration) R.drawable.chip_bg else R.drawable.chip_accent_bg)
            tabDuration.backgroundTintList = if (duration) accentTint else null
            tabAlarm.backgroundTintList = if (duration) null else accentTint
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
        pickerView?.let { windowManager.removeView(it) }
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
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(endMillis, showActivityIntent()),
            alarmOperation()
        )

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

        val view = LayoutInflater.from(this).inflate(R.layout.breathing, null)
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
        windowManager.addView(view, params)
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
        breathingView?.let { windowManager.removeView(it) }
        breathingView = null
        unmuteMedia()
    }

    /**
     * Requests exclusive transient audio focus so other apps pause their media for the
     * duration of the wind-down — any video or music in the background goes quiet.
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
    }

    /** Hands audio focus back so paused media can resume after the wind-down. */
    private fun unmuteMedia() {
        val request = audioFocusRequest ?: return
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
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
        blockHandler.removeCallbacks(blockRunnable)
        blockHandler.post(blockRunnable)
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
            val view = LayoutInflater.from(this).inflate(R.layout.block_overlay, null)
            view.findViewById<TextView>(R.id.block_home).apply {
                backgroundTintList = ColorStateList.valueOf(accentColor())
                setOnClickListener {
                    Haptics.tap(it)
                    goHome()
                    hideBlockOverlay()
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
            windowManager.addView(view, params)
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
        blockView?.let { windowManager.removeView(it) }
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

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Drop the louder LOW-importance channel from earlier builds.
        manager.deleteNotificationChannel(OLD_CHANNEL_ID)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.overlay_channel_name),
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = getString(R.string.overlay_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

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
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        /** Whether the overlay service is currently running; observed by the setup screen. */
        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running

        private const val CHANNEL_ID = "overlay_service_min"
        private const val OLD_CHANNEL_ID = "overlay_service"
        private const val NOTIFICATION_ID = 1
        private const val BUBBLE_SIZE_DP = 56f
        private const val DISMISS_SIZE_DP = 64f
        private const val DISMISS_MARGIN_DP = 48f
        private const val DISMISS_SLOP_DP = 16f
        private const val DISMISS_CANCEL_EXTRA_DP = 48f
        private const val DEFAULT_X_FRACTION = 1f
        private const val DEFAULT_Y_FRACTION = 0.33f
        private const val CUSTOM_MIN = 1
        private const val CUSTOM_MAX = 120
        private const val REQ_ALARM = 100
        private const val REQ_SHOW = 101
        private const val BREATH_MIN = 0.35f

        /** How often the active break checks the foreground app. */
        private const val BLOCK_POLL_MS = 1000L
        /** How far back to scan usage events when resolving the foreground app. */
        private const val FOREGROUND_LOOKBACK_MS = 10_000L

        const val ACTION_TIMER_FIRED = "com.lifelineventures.pause.action.TIMER_FIRED"

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
    }
}
