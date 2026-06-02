package com.lifelineventures.pause

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlin.math.abs
import kotlin.math.roundToInt

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null

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
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
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
        val sizePx = (BUBBLE_SIZE_DP * resources.displayMetrics.density).roundToInt()
        val marginPx = (EDGE_MARGIN_DP * resources.displayMetrics.density).roundToInt()

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - sizePx - marginPx
            y = resources.displayMetrics.heightPixels / 3
        }

        view.setOnTouchListener(DragTouchListener(params))
        view.setOnClickListener {
            Toast.makeText(this, R.string.overlay_tap_hint, Toast.LENGTH_SHORT).show()
        }

        windowManager.addView(view, params)
        bubbleView = view
    }

    private fun removeBubble() {
        bubbleView?.let { windowManager.removeView(it) }
        bubbleView = null
    }

    /**
     * Lets the user drag the bubble anywhere on screen. A press that moves less than
     * the platform touch slop is treated as a tap (so [View.performClick] still fires);
     * anything larger becomes a drag and the click is suppressed.
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
                        val maxX = (resources.displayMetrics.widthPixels - view.width).coerceAtLeast(0)
                        val maxY = (resources.displayMetrics.heightPixels - view.height).coerceAtLeast(0)
                        params.x = (startX + dx.roundToInt()).coerceIn(0, maxX)
                        params.y = (startY + dy.roundToInt()).coerceIn(0, maxY)
                        windowManager.updateViewLayout(view, params)
                    }
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    if (!dragging) view.performClick()
                    return true
                }
            }
            return false
        }
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
        private const val EDGE_MARGIN_DP = 16f

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            context.stopService(intent)
        }
    }
}
