package io.github.mzuhairkhan.pause

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService

/**
 * Quick Settings tile: tapping it opens the timer picker via [PauseLaunchActivity], the same
 * trampoline the widget's body tap uses. Never toggles anything itself -- the picker is always
 * the next step, whether starting a fresh pause or (harmlessly) reopening on a running one.
 */
class PauseTileService : TileService() {
    override fun onClick() {
        if (isLocked) {
            unlockAndRun { launch() }
        } else {
            launch()
        }
    }

    // The deprecated Intent overload is the only one that exists below API 34, so it's
    // unavoidable here despite targetSdk 36 -- lint's StartActivityAndCollapseDeprecated
    // flags any use of it as an error regardless of the surrounding SDK guard.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun launch() {
        val intent = Intent(this, PauseLaunchActivity::class.java)
        if (TileLaunch.usePendingIntentOverload(Build.VERSION.SDK_INT)) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                REQ_TILE_OPEN,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private companion object {
        const val REQ_TILE_OPEN = 120
    }
}
