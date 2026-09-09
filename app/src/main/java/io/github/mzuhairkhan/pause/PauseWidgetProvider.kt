package io.github.mzuhairkhan.pause

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Bundle

/**
 * A pure renderer: reads [PauseState] and the placed widget size, then pushes [RemoteViews]
 * built by [PauseWidgetRenderer]. Carries no custom actions -- `exported=true` is required at
 * minSdk 26 (a non-exported provider is known to misbehave on older platforms, since the
 * `ACTION_APPWIDGET_UPDATE` broadcast comes from `system_server`), so any action here would be
 * reachable by any installed app. Every click instead targets [PauseActionReceiver]
 * (`exported=false`) or [PauseLaunchActivity], via [PauseWidgetRenderer]'s `PendingIntent`s.
 */
class PauseWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> updateOne(context, appWidgetManager, id) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        updateOne(context, appWidgetManager, appWidgetId)
    }

    private fun updateOne(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        // Not every launcher writes the options bundle at initial bind, so the declared
        // minWidth/minHeight from pause_widget_info.xml are the fallback, not a guess.
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, DEFAULT_WIDTH_DP)
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, DEFAULT_HEIGHT_DP)
        val size = WidgetBreakpoints.forDp(widthDp, heightDp)
        val rv = PauseWidgetRenderer.build(context, size, PauseState.snapshot(context))
        appWidgetManager.updateAppWidget(appWidgetId, rv)
    }

    companion object {
        // Matches pause_widget_info.xml's minWidth/minHeight.
        private const val DEFAULT_WIDTH_DP = 110
        private const val DEFAULT_HEIGHT_DP = 40

        /** Re-renders every placed instance of this widget from the current [PauseState]. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PauseWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val provider = PauseWidgetProvider()
            ids.forEach { id -> provider.updateOne(context, manager, id) }
        }
    }
}
