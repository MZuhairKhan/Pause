package io.github.mzuhairkhan.pause

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.view.animation.PathInterpolator
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
 * It now fades in and vibrates once; setting a timer gives a firmer confirm tap than the light tick
 * that navigation keeps, so the two stay distinguishable.
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
    fun `the fade follows One UI's motion rules`() {
        ShadowSettings.setCanDrawOverlays(true)
        fireTimer(newService())
        val root = windowRoot(R.id.breathing_circle)
        assertNotNull("precondition: the wind-down came up", root)

        // Asserted on the animator itself: Robolectric's clock settles an animation too eagerly
        // for a "finished by 500 ms" check to tell 500 from 600.
        val animator = root!!.animate()
        assertTrue("Samsung's maximum is 500 ms, was ${animator.duration}", animator.duration <= 500L)
        val actual = animator.interpolator
        assertNotNull("the fade needs an explicit curve", actual)
        val oneUiBasic = PathInterpolator(0.22f, 0.25f, 0f, 1f)
        listOf(0.1f, 0.25f, 0.5f, 0.75f).forEach { t ->
            assertEquals(
                "not One UI's basic curve at t=$t",
                oneUiBasic.getInterpolation(t),
                actual!!.getInterpolation(t),
                0.001f
            )
        }
    }

    private val vibrator: Vibrator get() = app.getSystemService(Vibrator::class.java)

    @Test
    fun `the wind-down vibrates as it arrives, with no touch involved`() {
        // Found on a phone: View.performHapticFeedback is for touches, and Android drops it when
        // nobody is touching, so the arrival never buzzed. A real vibration needs the permission.
        ShadowSettings.setCanDrawOverlays(true)
        fireTimer(newService())
        shadowOf(Looper.getMainLooper()).idle()

        // isVibrating is time-based and never true for a predefined click, so the recorded
        // attributes are what show vibrate() was called. EFFECT_CLICK is 0, the field's default.
        assertNotNull("it never vibrated", shadowOf(vibrator).vibrationAttributesFromLastVibration)
        assertEquals(VibrationEffect.EFFECT_CLICK, shadowOf(vibrator).effectId)
    }

    @Test
    fun `the arrival vibration is an alarm, so a silenced ringer does not swallow it`() {
        ShadowSettings.setCanDrawOverlays(true)
        fireTimer(newService())
        shadowOf(Looper.getMainLooper()).idle()

        val attributes = shadowOf(vibrator).vibrationAttributesFromLastVibration as VibrationAttributes
        assertEquals(VibrationAttributes.USAGE_ALARM, attributes.usage)
    }

    @Test
    fun `the arrival vibrates once and no longer asks the window for touch feedback`() {
        ShadowSettings.setCanDrawOverlays(true)
        fireTimer(newService())
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("touch feedback is for touches", emptyList<Int>(), haptics)
    }

    @Test
    fun `a phone with no vibrator still shows the wind-down`() {
        shadowOf(vibrator).setHasVibrator(false)
        ShadowSettings.setCanDrawOverlays(true)
        fireTimer(newService())
        shadowOf(Looper.getMainLooper()).idle()

        assertNotNull(windowRoot(R.id.breathing_circle))
        assertEquals(null, shadowOf(vibrator).vibrationAttributesFromLastVibration)
    }

    @Test
    fun `the app asks for the vibrate permission`() {
        @Suppress("DEPRECATION")
        val requested = app.packageManager
            .getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()

        assertTrue(Manifest.permission.VIBRATE in requested)
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
