package io.github.mzuhairkhan.pause

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
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

    /**
     * Ensures notifications can actually be posted before anything asserts on them.
     *
     * Both the permission and the appop are needed. `SetupSmokeTest` revokes POST_NOTIFICATIONS
     * to prove the bubble starts without it, and revoking also denies the POST_NOTIFICATION
     * appop; restoring only the permission leaves `notify()` silently dropping everything, which
     * this test saw as an empty shade -- on API 33+ only, since `canPostNotifications()`
     * short-circuits to true below it. That is exactly why API 26 passed while 35 and 36 failed.
     * That class restores both now, but doing it here too keeps this test independent of whether
     * another class ran first, mirroring how [allowOverlays] grants rather than assumes.
     */
    private fun allowNotifications() {
        // UiAutomation rather than `pm grant`, matching SetupSmokeTest: the shell form kills the
        // owning process when it really changes a runtime permission, and that process is this
        // test's own. See setNotificationPermission() there for the logcat evidence.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(
                app.packageName, Manifest.permission.POST_NOTIFICATIONS
            )
        }
        shell("appops set ${app.packageName} POST_NOTIFICATION allow")
        val nm = app.getSystemService(NotificationManager::class.java)
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (!nm.areNotificationsEnabled() && SystemClock.uptimeMillis() < deadline) {
            Thread.sleep(100)
        }
        assertTrue(
            "Notifications are still disabled for ${app.packageName}; nothing could be asserted.",
            nm.areNotificationsEnabled()
        )
    }

    private fun awaitState(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (!condition() && SystemClock.uptimeMillis() < deadline) {
            Thread.sleep(50)
        }
    }

    /**
     * Opens the bubble and taps the 5-minute preset, exactly as a user would.
     *
     * Waits for the bubble to actually appear on screen rather than trusting
     * [OverlayService.running] alone: that flips true in `onCreate()`, which runs before
     * `onStartCommand()` gets to `showBubble()` -- so a caller can observe `running == true`
     * while the bubble view has not been added yet. This raced and failed intermittently in CI
     * before this wait was added.
     */
    private fun scheduleFiveMinuteTimer() {
        val desc = app.getString(R.string.overlay_bubble_description)
        assertTrue(
            "Bubble never appeared on screen after starting the service.",
            device.wait(Until.hasObject(By.desc(desc)), 10_000)
        )
        val bubble = device.findObject(By.desc(desc))
        checkNotNull(bubble) { "Bubble matched by wait() but not by the immediate findObject()." }
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

    /**
     * Polls for the posted notification to carry the given action titles, rather than reading
     * [NotificationManager.getActiveNotifications] once. `hideBubbleOrStop()` flips
     * [OverlayService.bubbleHidden] one line before it calls `updateNotification()` -- both
     * happen on the service's main thread, but this test observes them from the instrumentation
     * thread, so awaiting `bubbleHidden` alone does not guarantee the notify() call has landed
     * yet. This raced and failed intermittently in CI before this wait replaced a single read.
     */
    private fun awaitNotificationActions(expected: List<String>, timeoutMs: Long = 5_000) {
        val nm = app.getSystemService(NotificationManager::class.java)
        var lastSeen: List<List<String>> = emptyList()
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            lastSeen = nm.activeNotifications.map { it.notification.actions?.map { a -> a.title.toString() }.orEmpty() }
            // Matching among whatever is posted rather than demanding exactly one notification:
            // a previous test's onDestroy() flips _running.value false as its first line but
            // posts the idle notification several statements later, so a second notification can
            // still be resolving when this one is read. What this test needs is proof that the
            // hidden-state notification carrying these two actions exists, not a count.
            if (lastSeen.any { it == expected }) return
            Thread.sleep(50)
        }
        assertTrue(
            "Notification actions never matched. expected: $expected, active notifications' " +
                "actions: $lastSeen",
            lastSeen.any { it == expected }
        )
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
        allowNotifications()
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

            awaitNotificationActions(
                listOf(
                    app.getString(R.string.overlay_notification_show),
                    app.getString(R.string.overlay_notification_stop)
                )
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
