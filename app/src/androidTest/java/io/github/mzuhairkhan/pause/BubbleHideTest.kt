package io.github.mzuhairkhan.pause

import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves that hiding the bubble while a timer is running does NOT cancel the timer -- the bug
 * this whole feature exists to fix. Both tests start the service directly (a public companion
 * call, same tier as what MainActivity itself calls) rather than driving Settings' UI to reach
 * it, since only the hide/show/stop behavior itself is under test here. Both do drive the real
 * timer picker to schedule the timer, though: that is the only way to put a genuine future
 * `endTimeMillis` in place, which is what hide() actually keys its decision on.
 *
 * UiAutomator, not Espresso: the overlay's bubble and picker are TYPE_APPLICATION_OVERLAY
 * windows, a separate top-level window from any host Activity, which Espresso's root matching
 * cannot reliably target. UiAutomator walks the whole on-screen tree regardless of owning
 * process (see OverlayBackTest for the same appops-grant gotcha with overlay windows).
 */
@RunWith(AndroidJUnit4::class)
class BubbleHideTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(instrumentation)

    private fun shell(command: String) {
        val fd: ParcelFileDescriptor = instrumentation.uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
    }

    private fun allowOverlays() {
        shell("appops set ${app.packageName} SYSTEM_ALERT_WINDOW allow")
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (!Settings.canDrawOverlays(app) && SystemClock.uptimeMillis() < deadline) {
            Thread.sleep(100)
        }
    }

    private fun awaitState(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (!condition() && SystemClock.uptimeMillis() < deadline) {
            Thread.sleep(50)
        }
    }

    /** Opens the bubble and taps the 5-minute preset, exactly as a user would. */
    private fun scheduleFiveMinuteTimer() {
        val bubble = device.findObject(By.desc(app.getString(R.string.overlay_bubble_description)))
        checkNotNull(bubble) { "Bubble not found on screen after starting the service." }
        bubble.click()
        assertTrue(
            "Timer picker's 5-minute chip never appeared.",
            device.wait(Until.hasObject(By.res(app.packageName, "chip_5")), 5_000)
        )
        val chip = device.findObject(By.res(app.packageName, "chip_5"))
        checkNotNull(chip) { "5-minute chip disappeared before it could be tapped." }
        chip.click()
        device.waitForIdle()
    }

    /** Sends ACTION_STOP directly -- the same routed path the notification's Stop action uses. */
    private fun stopService() {
        app.startForegroundService(Intent(app, OverlayService::class.java).apply {
            action = OverlayService.ACTION_STOP
        })
        awaitState { !OverlayService.running.value }
    }

    /**
     * Fast: hiding while a timer is scheduled leaves the service running and flips
     * [OverlayService.bubbleHidden], and the notification gains exactly the Show/Stop actions
     * that are the only remaining control surface once hidden. This doesn't wait for the timer
     * to fire, only that the state machine reacts correctly to being hidden.
     */
    @Test
    fun hidingWhileRunningKeepsTheServiceAliveAndAddsNotificationActions() {
        allowOverlays()
        try {
            OverlayService.start(app)
            awaitState { OverlayService.running.value }

            scheduleFiveMinuteTimer()

            OverlayService.hide(app)
            awaitState { OverlayService.bubbleHidden.value }

            assertTrue("Hiding a running timer must not stop the service.", OverlayService.running.value)
            assertTrue(
                "bubbleHidden should be true once hidden with a timer active.",
                OverlayService.bubbleHidden.value
            )

            val nm = app.getSystemService(NotificationManager::class.java)
            val posted = nm.activeNotifications.singleOrNull()
            checkNotNull(posted) { "Expected exactly one posted notification." }
            val actionTitles = posted.notification.actions?.map { it.title.toString() }.orEmpty()
            assertEquals(
                listOf(
                    app.getString(R.string.overlay_notification_show),
                    app.getString(R.string.overlay_notification_stop)
                ),
                actionTitles
            )
        } finally {
            stopService()
        }
    }

    /**
     * Slow: proves the OS-level AlarmManager registration itself survives a hide, not just
     * in-memory state -- the fast test above would still pass even if cancelPendingAlarm() were
     * mistakenly still being called on hide. Waits for the real 5-minute preset to fire and the
     * breathing wind-down to appear.
     *
     * Costed and accepted deliberately: ~5-6 minutes of real wait, on top of the emulator
     * workflow's step budget, per the project plan.
     */
    @Test
    fun hiddenTimerStillFiresTheWindDown() {
        allowOverlays()
        try {
            OverlayService.start(app)
            awaitState { OverlayService.running.value }

            scheduleFiveMinuteTimer()

            OverlayService.hide(app)
            awaitState { OverlayService.bubbleHidden.value }
            assertTrue(
                "Expected the bubble to be hidden before waiting for the alarm.",
                OverlayService.bubbleHidden.value
            )

            // Search for the always-visible "Go home" button rather than the animated phase
            // label: its text is set directly in XML and present from the first frame, unlike
            // the phase label, which cycles through three strings and starts empty.
            assertTrue(
                "The wind-down never appeared after the hidden timer's scheduled fire time.",
                device.wait(Until.hasObject(By.text(app.getString(R.string.breathing_home))), 6 * 60_000L)
            )
            assertTrue(
                "The bubble should be un-hidden once its timer fires.",
                !OverlayService.bubbleHidden.value
            )
        } finally {
            stopService()
        }
    }
}
