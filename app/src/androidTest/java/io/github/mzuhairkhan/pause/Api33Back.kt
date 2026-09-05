package io.github.mzuhairkhan.pause

import android.os.Build
import android.view.View
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.annotation.RequiresApi
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/**
 * The API 33+ half of [OverlayBackTest], kept in its own class on purpose.
 *
 * `OnBackInvokedDispatcher` and `OnBackInvokedCallback` do not exist below Android 13. If they
 * appear anywhere in [OverlayBackTest]'s own signatures, ART on API 26 fails to verify that class
 * and the whole test file dies with "Failed to instantiate test runner class" before a single
 * assertion runs. Annotating the methods `@RequiresApi` is a lint contract, not a runtime one --
 * only moving the types out of the test class keeps it loadable. Nothing here is touched unless
 * `Build.VERSION.SDK_INT` says it is safe.
 */
@RequiresApi(33)
internal object Api33Back {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    /**
     * @param view an attached overlay root View.
     * @return Boolean true when the window exposes an OnBackInvokedDispatcher.
     */
    fun hasDispatcher(view: View): Boolean = view.findOnBackInvokedDispatcher() != null

    /**
     * Registers a callback on [view], presses BACK, and asserts the outcome that this device's
     * back regime implies.
     *
     * On Android 16 the callback MUST fire: targetSdk 36 opts the app into predictive back, and
     * that callback is what the overlay locks now rest on. Below 16 the app is not opted in by
     * default -- the device's package parser decides -- so the registration is expected to be
     * inert and the OnKeyListener carries BACK. That is deliberately observed and reported rather
     * than asserted, because the default is Google's to change and a test asserting a negative
     * would fail on the day it does.
     *
     * What is asserted on every version: the window is still standing afterwards. A lock that
     * lets BACK tear its own window down is not a lock.
     *
     * (`ApplicationInfo.isOnBackInvokedCallbackEnabled()` would be the direct question to ask,
     * but it is @hide -- absent from the public android.jar -- so the behaviour is observed.)
     *
     * @param view an attached, focusable overlay root View.
     * @param onMain runs a block on the main thread and returns its value.
     * @param sendBack delivers a BACK press to the focused window.
     * @return Unit
     */
    fun assertBackBehaviour(view: View, onMain: (() -> Any?) -> Any?, sendBack: () -> Unit) {
        val fired = CountDownLatch(1)
        val callback = OnBackInvokedCallback { fired.countDown() }
        val dispatcher = onMain {
            val d: OnBackInvokedDispatcher? = view.findOnBackInvokedDispatcher()
            d?.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, callback)
            view.requestFocus()
            d
        } as OnBackInvokedDispatcher?
        assertNotNull("No dispatcher to register against.", dispatcher)
        instrumentation.waitForIdleSync()
        try {
            sendBack()
            val invoked = fired.await(5, TimeUnit.SECONDS)
            println(
                "OverlayBackTest: SDK ${Build.VERSION.SDK_INT} -- OnBackInvokedCallback " +
                    (if (invoked) "fired (predictive back is the live path)"
                    else "did not fire (the OnKeyListener fallback carries BACK)")
            )
            if (Build.VERSION.SDK_INT >= 36) {
                assertTrue(
                    "On Android 16 targetSdk 36 opts this app into predictive back, so BACK must " +
                        "reach the registered OnBackInvokedCallback. It did not -- the overlay " +
                        "locks would have nothing holding them.",
                    invoked
                )
            }
            instrumentation.waitForIdleSync()
            assertTrue(
                "The overlay window was torn down by BACK; an empty callback must swallow it.",
                onMain { view.isAttachedToWindow } as Boolean
            )
        } finally {
            onMain { dispatcher?.unregisterOnBackInvokedCallback(callback) }
        }
    }
}
