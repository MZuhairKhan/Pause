package io.github.mzuhairkhan.pause

import android.app.Application
import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Drives [PauseWidgetProvider] directly rather than through Robolectric's full app-widget-host
 * broadcast simulation (uncertain whether that actually dispatches to a provider's `onUpdate`
 * under Robolectric): `onUpdate`/`onAppWidgetOptionsChanged` are called the same way the OS
 * calls them, and `ShadowAppWidgetManager#getViewFor` inspects what was actually pushed.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PauseWidgetProviderTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val manager get() = AppWidgetManager.getInstance(app)
    private val provider = PauseWidgetProvider()

    private fun newWidget(): Int {
        val shadow = shadowOf(manager)
        return shadow.createWidget(PauseWidgetProvider::class.java, R.layout.widget_pause_medium)
    }

    @Test
    fun `no options bundle falls back to the declared default size, not a crash`() {
        val id = newWidget()

        provider.onUpdate(app, manager, intArrayOf(id))

        // 110x40dp (the fallback) is WidgetBreakpoints.forDp's medium range: has the label
        // row but not the large-only chips row.
        val view = shadowOf(manager).getViewFor(id)
        assertNotNull(view.findViewById<android.view.View>(R.id.widget_label))
        assertNull(view.findViewById<android.view.View>(R.id.widget_chips))
    }

    @Test
    fun `a resize to the large threshold switches the pushed layout`() {
        val id = newWidget()
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 260)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 150)
        }
        manager.updateAppWidgetOptions(id, options)

        provider.onAppWidgetOptionsChanged(app, manager, id, options)

        val view = shadowOf(manager).getViewFor(id)
        assertNotNull(
            "a 260x150dp placement must render the large layout's chip row",
            view.findViewById<android.view.View>(R.id.widget_chips)
        )
    }

    @Test
    fun `a resize below the medium threshold renders the glyph-only small layout`() {
        val id = newWidget()
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 60)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 40)
        }
        manager.updateAppWidgetOptions(id, options)

        provider.onAppWidgetOptionsChanged(app, manager, id, options)

        val view = shadowOf(manager).getViewFor(id)
        assertNull(
            "small has no label/chrono/static views at all",
            view.findViewById<android.view.View>(R.id.widget_label)
        )
    }

    @Test
    fun `refresh with no placed widgets does not crash`() {
        PauseWidgetProvider.refresh(app)
    }

    @Test
    fun `refresh pushes the current PauseState to every placed widget`() {
        val id = newWidget()
        provider.onUpdate(app, manager, intArrayOf(id))
        // Idle: the headline text, not "Time remaining".
        val idleView = shadowOf(manager).getViewFor(id)
        val idleText = idleView.findViewById<android.widget.TextView>(R.id.widget_static).text.toString()

        val end = System.currentTimeMillis() + 90_000L
        PauseState.setTimer(app, System.currentTimeMillis(), end)
        PauseWidgetProvider.refresh(app)

        val runningView = shadowOf(manager).getViewFor(id)
        val runningLabel = runningView.findViewById<android.widget.TextView>(R.id.widget_label)
        org.junit.Assert.assertEquals(android.view.View.VISIBLE, runningLabel.visibility)
        org.junit.Assert.assertNotEquals(idleText, runningLabel.text.toString())
    }
}
