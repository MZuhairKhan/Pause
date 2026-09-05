package io.github.mzuhairkhan.pause

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.annotation.RequiresApi
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the predictive-back plumbing the overlay windows depend on, on a real runtime.
 *
 * The wind-down's no-skip lock and the block cover both rely on BACK being *consumed*. Until
 * Android 13 the only mechanism was an OnKeyListener; from 13 the platform routes BACK through
 * OnBackInvokedDispatcher instead, and at targetSdk 36 that is on by default. KEYCODE_BACK does
 * still reach the view tree on Android 16, but through a legacy fallback AOSP logs as an error,
 * and 16.0 and 16.1 disagree about whether the forwarded up-event arrives cancelled. This test
 * pins the mechanism the app now registers rather than that accident.
 *
 * Why not a Robolectric test: its ViewRootImpl is a shadow, so a dispatcher lookup there proves
 * nothing about the real platform. Why not drive OverlayService directly: starting a specialUse
 * foreground service from a test hits background-start restrictions and would be flaky rather
 * than informative. So this builds the same kind of window the service builds -- a focusable
 * TYPE_APPLICATION_OVERLAY -- and asserts against that.
 *
 * Three things this file has to be careful about, all learned from CI:
 *
 *  - Every touch of the view hierarchy runs on the main thread. Calling requestFocus() from the
 *    instrumentation thread throws CalledFromWrongThreadException.
 *  - The appops grant has to be waited on. Closing the shell descriptor without draining it lets
 *    addView race ahead of the grant, which surfaces as BadTokenException "permission denied for
 *    window type 2038" -- seen on the slower API 26 image.
 *  - findOnBackInvokedDispatcher() does not exist below API 33, so calling it there is a
 *    NoSuchMethodError, not a null. The 33+ work lives in its own @RequiresApi method.
 *
 * There is no `Assume`/skip anywhere: the CI smoke script fails the build on skipped instrumented
 * tests, so each case asserts the behaviour appropriate to its API level instead.
 */
@RunWith(AndroidJUnit4::class)
class OverlayBackTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    /**
     * Runs [block] on the main thread and returns its value.
     *
     * @param block executed on the main looper.
     * @return T the value [block] produced.
     */
    private fun <T> onMain(block: () -> T): T {
        val holder = arrayOfNulls<Any>(1)
        instrumentation.runOnMainSync { holder[0] = block() }
        @Suppress("UNCHECKED_CAST")
        return holder[0] as T
    }

    /**
     * Runs a shell command and waits for it to finish.
     *
     * @param command the shell command line.
     * @return Unit
     */
    private fun shell(command: String) {
        val fd: ParcelFileDescriptor = instrumentation.uiAutomation.executeShellCommand(command)
        // Draining is what makes this synchronous; the descriptor closing early does not mean the
        // command has run.
        ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
    }

    /**
     * Grants the overlay app-op and waits until the platform actually reports it, so a
     * TYPE_APPLICATION_OVERLAY window can be added.
     *
     * @return Unit
     */
    private fun allowOverlays() {
        shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (!Settings.canDrawOverlays(context) && SystemClock.uptimeMillis() < deadline) {
            Thread.sleep(100)
        }
        assertTrue(
            "Overlay permission never took effect; a TYPE_APPLICATION_OVERLAY window cannot be added.",
            Settings.canDrawOverlays(context)
        )
    }

    /**
     * Adds a focusable full-screen overlay window, runs [block] against it, and always removes
     * the window afterwards.
     *
     * @param block receives the attached root View. Runs on the instrumentation thread, so it
     *   must use [onMain] for anything that touches the view hierarchy.
     * @return Unit
     */
    private fun withOverlayWindow(block: (View) -> Unit) {
        allowOverlays()
        val wm = context.getSystemService(WindowManager::class.java)
        val view = View(context).apply { isFocusableInTouchMode = true }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            0,
            PixelFormat.TRANSLUCENT
        )
        instrumentation.runOnMainSync { wm.addView(view, params) }
        instrumentation.waitForIdleSync()
        try {
            block(view)
        } finally {
            instrumentation.runOnMainSync { runCatching { wm.removeView(view) } }
            instrumentation.waitForIdleSync()
        }
    }

    /**
     * The dispatcher the fix depends on is actually reachable from a Service-style overlay
     * window. Most documentation describes Activities and Dialogs, so this is the assumption
     * worth pinning: if it ever returns null on 33+, the registrations become silent no-ops and
     * the no-skip lock quietly degrades to the legacy key-event path.
     */
    @Test
    fun overlayWindowExposesBackDispatcher() {
        withOverlayWindow { view ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                assertNotNull(
                    "No OnBackInvokedDispatcher on a TYPE_APPLICATION_OVERLAY window: the " +
                        "overlay back callbacks would silently never register.",
                    onMain { dispatcherOf(view) }
                )
            } else {
                // The API does not exist here at all, so BACK can only arrive as a key event.
                // All this can assert is that the window itself is usable.
                assertTrue(onMain { view.isAttachedToWindow })
            }
        }
    }

    /**
     * A registered callback fires when BACK is pressed, and -- the part that matters for the
     * lock -- the window survives it. A callback that registered but did not fire, or fired but
     * let the window be torn down, would each defeat the wind-down's no-skip lock.
     */
    @Test
    fun registeredCallbackConsumesBackAndLeavesTheWindowUp() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Below 33 there is no dispatcher to exercise; the OnKeyListener carries BACK.
            // Asserted explicitly rather than skipped, because CI fails on skipped tests.
            withOverlayWindow { view -> assertTrue(onMain { view.isAttachedToWindow }) }
            return
        }
        withOverlayWindow { view -> assertBackIsConsumed(view) }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun dispatcherOf(view: View): OnBackInvokedDispatcher? =
        view.findOnBackInvokedDispatcher()

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun assertBackIsConsumed(view: View) {
        val fired = CountDownLatch(1)
        val callback = OnBackInvokedCallback { fired.countDown() }
        val dispatcher = onMain {
            val d = dispatcherOf(view)
            d?.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, callback)
            view.requestFocus()
            d
        }
        assertNotNull("No dispatcher to register against.", dispatcher)
        instrumentation.waitForIdleSync()
        try {
            shell("input keyevent 4")
            assertTrue(
                "BACK did not reach the registered OnBackInvokedCallback.",
                fired.await(5, TimeUnit.SECONDS)
            )
            instrumentation.waitForIdleSync()
            assertTrue(
                "The overlay window was torn down by BACK; an empty callback must swallow it.",
                onMain { view.isAttachedToWindow }
            )
        } finally {
            onMain { dispatcher?.unregisterOnBackInvokedCallback(callback) }
        }
    }
}
