package io.github.mzuhairkhan.pause

import android.app.Application
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Renders each [PauseWidgetRenderer] output through `RemoteViews#apply`, not just a plain
 * `LayoutInflater` -- `apply()` runs the real `INFLATER_FILTER` remotability guard and every
 * `setInt`/`setChronometer` reflection lookup, so a typo'd method name or a disallowed view
 * type fails here rather than silently on a device. State/size combinations not covered here
 * (e.g. small at every state) are the least surprising ones; see the state table in the KDoc
 * on [PauseWidgetRenderer.build].
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PauseWidgetRendererTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private fun render(size: WidgetSize, snapshot: PauseSnapshot): View {
        val rv = PauseWidgetRenderer.build(app, size, snapshot)
        return rv.apply(app, FrameLayout(app))
    }

    private val idle = PauseSnapshot(0L, 0L, 0L)

    @Test
    fun `idle on medium shows the headline and hides the countdown row`() {
        SettingsStore.setShowCountdown(app, true)
        val view = render(WidgetSize.MEDIUM, idle)

        assertEquals(View.GONE, view.findViewById<TextView>(R.id.widget_label).visibility)
        assertEquals(View.GONE, view.findViewById<Chronometer>(R.id.widget_chrono).visibility)
        val static = view.findViewById<TextView>(R.id.widget_static)
        assertEquals(View.VISIBLE, static.visibility)
        assertEquals(app.getString(R.string.picker_title), static.text.toString())
    }

    @Test
    fun `a running timer with countdown on arms a count-down Chronometer`() {
        SettingsStore.setShowCountdown(app, true)
        val now = System.currentTimeMillis()
        val end = now + 90_000L
        val view = render(WidgetSize.MEDIUM, PauseSnapshot(now, end, 0L))

        val label = view.findViewById<TextView>(R.id.widget_label)
        assertEquals(View.VISIBLE, label.visibility)
        assertEquals(app.getString(R.string.picker_remaining_label), label.text.toString())

        val chrono = view.findViewById<Chronometer>(R.id.widget_chrono)
        assertEquals(View.VISIBLE, chrono.visibility)
        assertTrue("must count down, not up", chrono.isCountDown)
        // base is elapsed-realtime-referenced (see ChronometerBaseTest); assert the offset from
        // "now" it encodes matches the wall-clock deadline offset, within a tight tolerance.
        val impliedRemaining = chrono.base - android.os.SystemClock.elapsedRealtime()
        val expectedRemaining = end - System.currentTimeMillis()
        assertTrue(kotlin.math.abs(impliedRemaining - expectedRemaining) < 2_000L)

        assertEquals(View.GONE, view.findViewById<TextView>(R.id.widget_static).visibility)
    }

    @Test
    fun `a running timer with countdown off hides the Chronometer and pushes a bitmap glyph`() {
        SettingsStore.setShowCountdown(app, false)
        val now = System.currentTimeMillis()
        val view = render(WidgetSize.MEDIUM, PauseSnapshot(now, now + 90_000L, 0L))

        assertEquals(View.GONE, view.findViewById<Chronometer>(R.id.widget_chrono).visibility)
        assertEquals(View.GONE, view.findViewById<TextView>(R.id.widget_static).visibility)
        val glyph = view.findViewById<ImageView>(R.id.widget_glyph)
        assertTrue(
            "a draining timer with the countdown off must rasterize the live hourglass",
            glyph.drawable is BitmapDrawable
        )
    }

    @Test
    fun `an active break shows the break label and no countdown row`() {
        val now = System.currentTimeMillis()
        val view = render(WidgetSize.MEDIUM, PauseSnapshot(0L, 0L, now + 60_000L))

        assertEquals(View.GONE, view.findViewById<TextView>(R.id.widget_label).visibility)
        assertEquals(View.GONE, view.findViewById<Chronometer>(R.id.widget_chrono).visibility)
        val static = view.findViewById<TextView>(R.id.widget_static)
        assertEquals(View.VISIBLE, static.visibility)
        assertEquals(app.getString(R.string.block_title), static.text.toString())
    }

    @Test
    fun `large idle shows quick-start chips and hides cancel`() {
        val view = render(WidgetSize.LARGE, idle)

        assertEquals(View.VISIBLE, view.findViewById<LinearLayout>(R.id.widget_chips).visibility)
        assertEquals(View.GONE, view.findViewById<TextView>(R.id.widget_cancel).visibility)
    }

    @Test
    fun `large running shows cancel and hides quick-start chips`() {
        val now = System.currentTimeMillis()
        val view = render(WidgetSize.LARGE, PauseSnapshot(now, now + 90_000L, 0L))

        assertEquals(View.GONE, view.findViewById<LinearLayout>(R.id.widget_chips).visibility)
        assertEquals(View.VISIBLE, view.findViewById<TextView>(R.id.widget_cancel).visibility)
    }

    @Test
    fun `small always renders just the glyph, regardless of the countdown setting`() {
        SettingsStore.setShowCountdown(app, true)
        val now = System.currentTimeMillis()
        val view = render(WidgetSize.SMALL, PauseSnapshot(now, now + 90_000L, 0L))

        assertNull("the small layout has no label/chrono/static views at all", view.findViewById<View>(R.id.widget_label))
        val glyph = view.findViewById<ImageView>(R.id.widget_glyph)
        assertEquals(View.VISIBLE, glyph.visibility)
    }
}
