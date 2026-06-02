package com.lifelineventures.pause

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
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
import android.widget.ImageView
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.TimePicker
import androidx.core.app.NotificationCompat
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager

    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var bubbleIcon: ImageView? = null
    private var bubbleCountdown: TextView? = null
    private var pickerView: View? = null

    /** Wall-clock end time of the active timer, or 0 when idle. */
    private var endTimeMillis = 0L

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
            val remaining = endTimeMillis - System.currentTimeMillis()
            updateCountdown(remaining.coerceAtLeast(0))
            if (remaining > 0) tickHandler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
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
        if (intent?.action == ACTION_RESET) {
            endTimeMillis = 0L
            setBubbleIdle()
        } else {
            restoreTimerIfAny()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val view = bubbleView ?: return
        val params = bubbleParams ?: return
        // Re-clamp on the next frame, once the display metrics have settled for the new
        // orientation. The bubble keeps its coordinates (or snaps to the nearest edge if
        // they'd now be off-screen) rather than moving to a proportional spot.
        view.post {
            if (bubbleView !== view) return@post
            applyBubblePosition(params)
            windowManager.updateViewLayout(view, params)
        }
    }

    override fun onDestroy() {
        stopTicker()
        hidePicker()
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
        applyBubblePosition(params)

        view.setOnTouchListener(DragTouchListener(params))
        view.setOnClickListener { showPicker() }

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
            updateCountdown((endTimeMillis - System.currentTimeMillis()).coerceAtLeast(0))
            startTicker()
        } else {
            // User prefers the static glyph even while a timer runs.
            stopTicker()
            bubbleCountdown?.visibility = View.GONE
            bubbleIcon?.visibility = View.VISIBLE
        }
    }

    private fun setBubbleIdle() {
        stopTicker()
        bubbleCountdown?.visibility = View.GONE
        bubbleIcon?.visibility = View.VISIBLE
    }

    private fun updateCountdown(remainingMillis: Long) {
        val totalSeconds = (remainingMillis / 1000L).toInt()
        bubbleCountdown?.text = when {
            totalSeconds >= 3600 -> "${(totalSeconds + 3599) / 3600}h"
            totalSeconds >= 60 -> "${(totalSeconds + 59) / 60}m"
            else -> "${totalSeconds}s"
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
                    }
                    if (dragging) {
                        val (screenW, screenH) = screenSize()
                        val maxX = (screenW - view.width).coerceAtLeast(0)
                        val maxY = (screenH - view.height).coerceAtLeast(0)
                        params.x = (startX + dx.roundToInt()).coerceIn(0, maxX)
                        params.y = (startY + dy.roundToInt()).coerceIn(0, maxY)
                        windowManager.updateViewLayout(view, params)
                    }
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        saveBubblePosition()
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
    }

    // --- Timer picker ---

    @SuppressLint("InflateParams")
    private fun showPicker() {
        if (pickerView != null) return
        if (!Settings.canDrawOverlays(this)) return

        // Inflate with a dark theme so the platform spinners are legible on the card.
        val themed = ContextThemeWrapper(this, R.style.Theme_Pause_Overlay)
        val view = LayoutInflater.from(themed).inflate(R.layout.timer_picker, null)

        wirePickerModes(view)
        wireDurationMode(view)
        wireAlarmMode(view)
        wireCancel(view)
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
    }

    private fun wirePickerModes(view: View) {
        val sectionDuration = view.findViewById<View>(R.id.section_duration)
        val sectionAlarm = view.findViewById<View>(R.id.section_alarm)
        val tabDuration = view.findViewById<TextView>(R.id.tab_duration)
        val tabAlarm = view.findViewById<TextView>(R.id.tab_alarm)
        tabDuration.text = getString(R.string.picker_mode_duration)
        tabAlarm.text = getString(R.string.picker_mode_alarm)

        fun selectMode(duration: Boolean) {
            sectionDuration.visibility = if (duration) View.VISIBLE else View.GONE
            sectionAlarm.visibility = if (duration) View.GONE else View.VISIBLE
            tabDuration.setBackgroundResource(if (duration) R.drawable.chip_accent_bg else R.drawable.chip_bg)
            tabAlarm.setBackgroundResource(if (duration) R.drawable.chip_bg else R.drawable.chip_accent_bg)
        }

        tabDuration.setOnClickListener { selectMode(true) }
        tabAlarm.setOnClickListener { selectMode(false) }
        selectMode(true)
    }

    private fun wireDurationMode(view: View) {
        listOf(R.id.chip_5 to 5, R.id.chip_10 to 10, R.id.chip_15 to 15).forEach { (id, minutes) ->
            view.findViewById<TextView>(id).apply {
                text = minutes.toString()
                setOnClickListener { startDurationAndClose(minutes) }
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
        view.findViewById<TextView>(R.id.btn_start_duration).setOnClickListener {
            startDurationAndClose(wheel.value)
        }
    }

    private fun wireAlarmMode(view: View) {
        val timePicker = view.findViewById<TimePicker>(R.id.time_picker).apply {
            setIs24HourView(false)
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
        view.findViewById<TextView>(R.id.btn_start_alarm).setOnClickListener {
            startAlarmAndClose(timePicker.hour, timePicker.minute)
        }
    }

    private fun wireCancel(view: View) {
        if (endTimeMillis > System.currentTimeMillis()) {
            view.findViewById<TextView>(R.id.btn_cancel).apply {
                visibility = View.VISIBLE
                setOnClickListener {
                    cancelTimer()
                    hidePicker()
                }
            }
        }
    }

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
        endTimeMillis = endMillis

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // setAlarmClock() is treated as exact without needing the SCHEDULE_EXACT_ALARM
        // permission, and it briefly allowlists us so the broadcast delivers on time.
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(endMillis, showActivityIntent()),
            alarmOperation()
        )

        TimerStore.save(this, endMillis)
        setBubbleActive()
    }

    private fun cancelTimer() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(alarmOperation())
        endTimeMillis = 0L
        TimerStore.clear(this)
        setBubbleIdle()
    }

    private fun restoreTimerIfAny() {
        val saved = TimerStore.endTime(this)
        if (saved > System.currentTimeMillis()) {
            endTimeMillis = saved
            setBubbleActive()
        } else {
            if (saved != 0L) TimerStore.clear(this)
            endTimeMillis = 0L
            setBubbleIdle()
        }
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

    // --- Notification / foreground service ---

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.overlay_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.overlay_channel_description)
            setShowBadge(false)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "overlay_service"
        private const val NOTIFICATION_ID = 1
        private const val BUBBLE_SIZE_DP = 56f
        private const val DEFAULT_X_FRACTION = 1f
        private const val DEFAULT_Y_FRACTION = 0.33f
        private const val CUSTOM_MIN = 1
        private const val CUSTOM_MAX = 120
        private const val REQ_ALARM = 100
        private const val REQ_SHOW = 101

        const val ACTION_RESET = "com.lifelineventures.pause.action.RESET"

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            context.stopService(intent)
        }

        fun reset(context: Context) {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_RESET
            }
            context.startForegroundService(intent)
        }
    }
}
