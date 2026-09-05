package io.github.mzuhairkhan.pause

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 * Note there is no `Assume`/skip anywhere: the CI smoke script fails the build on skipped
 * instrumented tests, so each case asserts the behaviour appropriate to its API level instead.
 */
@RunWith(AndroidJUnit4::class)
class OverlayBackTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    /**
     * Grants the overlay app-op so a TYPE_APPLICATION_OVERLAY window can be added. Debug builds
     * do not hold it by default, and the test would otherwise fail inside addView.
     *
     * @return Unit
     */
    private fun allowOverlays() {
        instrumentation.uiAutomation
            .executeShellCommand("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
            .close()
    }

    /**
     * Adds a focusable full-screen overlay window, runs [block] against it on the main thread,
     * and always removes the window afterwards.
     *
     * @param block receives the attached root View.
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
     * the no-skip lock quietly degrades to the legacy path.
     */
    @Test
    fun overlayWindowExposesBackDispatcherOnApi33Plus() {
        withOverlayWindow { view ->
            val dispatcher = view.findOnBackInvokedDispatcher()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                assertNotNull(
                    "No OnBackInvokedDispatcher on a TYPE_APPLICATION_OVERLAY window: the " +
                        "overlay back callbacks would silently never register.",
                    dispatcher
                )
            } else {
                assertNull(
                    "Unexpected dispatcher below API 33; the OnKeyListener path is the only one.",
                    dispatcher
                )
            }
        }
    }

    /**
     * A registered callback fires when BACK is pressed, and -- the part that matters for the
     * lock -- the window survives it. A callback that registered but did not fire, or fired but
     * let the window be torn down, would both defeat the wind-down's no-skip lock.
     */
    @Test
    fun registeredCallbackConsumesBackAndLeavesTheWindowUp() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Below 33 there is no dispatcher to exercise; the OnKeyListener carries BACK and is
            // covered by the case above. Assert that explicitly rather than skipping.
            withOverlayWindow { view -> assertNull(view.findOnBackInvokedDispatcher()) }
            return
        }
        withOverlayWindow { view ->
            val dispatcher = view.findOnBackInvokedDispatcher()
            assertNotNull(dispatcher)
            val fired = CountDownLatch(1)
            val callback = OnBackInvokedCallback { fired.countDown() }
            dispatcher!!.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                callback
            )
            try {
                view.requestFocus()
                instrumentation.waitForIdleSync()
                instrumentation.uiAutomation.executeShellCommand("input keyevent 4").close()

                assertTrue(
                    "BACK did not reach the registered OnBackInvokedCallback.",
                    fired.await(5, TimeUnit.SECONDS)
                )
                instrumentation.waitForIdleSync()
                assertTrue(
                    "The overlay window was torn down by BACK; an empty callback must swallow it.",
                    view.isAttachedToWindow
                )
            } finally {
                dispatcher.unregisterOnBackInvokedCallback(callback)
            }
        }
    }
}
