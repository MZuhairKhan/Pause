package io.github.mzuhairkhan.pause

import android.app.Application
import android.content.Intent
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowWindowManagerImpl

/**
 * The wind-down used to appear at full opacity with no feedback, which designers called abrupt.
 * It now fades in and gives one confirm haptic; setting a timer gives the same confirm, while
 * navigation taps keep the lighter tick so the two stay distinguishable.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class WindDownArrivalTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private var createdService: OverlayService? = null
    private val haptics = mutableListOf<Int>()

    @Before
    fun recordHaptics() {
        Haptics.performer = { _, constant -> haptics += constant }
    }

    @After
    fun tearDown() {
        createdService?.onDestroy()
        Haptics.performer = { view, constant -> view.performHapticFeedback(constant) }
    }

    private fun newService(): OverlayService =
        Robolectric.buildService(OverlayService::class.java).create().get().also { createdService = it }

    private fun windowRoot(markerId: Int): View? {
        val shadowWm = Shadow.extract<ShadowWindowManagerImpl>(
            app.getSystemService(WindowManager::class.java)
        )
        return shadowWm.views.firstOrNull { it.findViewById<View>(markerId) != null }
    }

    private fun fireTimer(service: OverlayService) {
        service.onStartCommand(Intent(app, OverlayService::class.java).apply {
            action = OverlayService.ACTION_TIMER_FIRED
        }, 0, 1)
    }

    @Test
    fun `the wind-down starts transparent and fades to opaque`() {
        ShadowSettings.setCanDrawOverlays(true)
        fireTimer(newService())

        val root = windowRoot(R.id.breathing_circle)
        assertNotNull("precondition: the wind-down came up", root)
        assertEquals("it must not pop in at full opacity", 0f, root!!.alpha, 0.01f)

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(900))

        assertEquals(1f, root.alpha, 0.01f)
    }

    @Test
    fun `the wind-down gives one confirm haptic as it arrives`() {
        ShadowSettings.setCanDrawOverlays(true)
        fireTimer(newService())
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(HapticFeedbackConstants.CONFIRM), haptics)
    }

    @Test
    fun `setting a timer from a chip confirms, while switching tabs keeps the light tick`() {
        ShadowSettings.setCanDrawOverlays(true)
        val service = newService()
        service.onStartCommand(Intent(app, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SHOW_PICKER
        }, 0, 1)
        shadowOf(Looper.getMainLooper()).idle()
        val picker = windowRoot(R.id.tab_alarm)
        assertNotNull("precondition: the picker came up", picker)

        picker!!.findViewById<View>(R.id.tab_alarm).performClick()
        assertEquals(listOf(HapticFeedbackConstants.CONTEXT_CLICK), haptics)

        haptics.clear()
        picker.findViewById<View>(R.id.chip_5).performClick()
        assertEquals(listOf(HapticFeedbackConstants.CONFIRM), haptics)
    }

    @Test
    fun `below API 30 the confirm falls back to a virtual key press`() {
        assertEquals(HapticFeedbackConstants.VIRTUAL_KEY, Haptics.confirmConstant(29))
        assertEquals(HapticFeedbackConstants.CONFIRM, Haptics.confirmConstant(30))
    }
}
