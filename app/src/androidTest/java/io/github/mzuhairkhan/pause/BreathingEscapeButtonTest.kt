package io.github.mzuhairkhan.pause

import android.content.Context
import android.graphics.PixelFormat
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the breathing wind-down's "Go home" escape is reachable immediately -- unlike the three
 * buttons inside `breathing_actions`, which stay hidden until the non-skippable lock window
 * elapses, this one has no visibility gating at all in the layout, and this pins that.
 *
 * Inflates the real `breathing.xml` into a throwaway overlay window, the same technique
 * OverlayBackTest uses and for the same reason: starting a specialUse foreground service from a
 * test hits background-start restrictions and would be flaky rather than informative, but the
 * button's visibility and tap-target size don't depend on anything OverlayService itself does.
 *
 * What this does NOT prove: that tapping it actually calls goHome() against a real launcher.
 * That wiring only exists inside OverlayService.showBreathing(), and proving it fires needs the
 * same heavy real-timer flow as BubbleHideTest -- and, notably, none of this screen's other three
 * buttons (Keep/Stop/Snooze) have that kind of behavioral coverage either. Matched to that
 * precedent: a manual on-device check before shipping, not a new heavy automated test.
 */
@RunWith(AndroidJUnit4::class)
class BreathingEscapeButtonTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun shell(command: String) {
        val fd: ParcelFileDescriptor = instrumentation.uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
    }

    private fun allowOverlays() {
        shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (!Settings.canDrawOverlays(context) && SystemClock.uptimeMillis() < deadline) {
            Thread.sleep(100)
        }
    }

    @Test
    fun goHomeButtonIsVisibleImmediatelyWithA48dpTapTarget() {
        allowOverlays()
        val wm = context.getSystemService(WindowManager::class.java)
        val view = LayoutInflater.from(context).inflate(R.layout.breathing, null)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )
        instrumentation.runOnMainSync { wm.addView(view, params) }
        instrumentation.waitForIdleSync()
        try {
            val home = view.findViewById<TextView>(R.id.breathing_home)
            assertEquals(
                "The escape button must not be gated behind the lock window like the other " +
                    "three breathing_actions buttons are.",
                View.VISIBLE,
                home.visibility
            )
            val minPx = (48 * context.resources.displayMetrics.density).toInt()
            assertTrue(
                "breathing_home is ${home.width}x${home.height}px, below the ${minPx}px " +
                    "(48dp) minimum tap target this app requires elsewhere.",
                home.width >= minPx && home.height >= minPx
            )
        } finally {
            instrumentation.runOnMainSync { runCatching { wm.removeView(view) } }
            instrumentation.waitForIdleSync()
        }
    }
}
