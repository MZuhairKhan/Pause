package io.github.mzuhairkhan.pause

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-posts the persistent "Start Pause" notification after boot so the overlay can be
 * launched from the shade. Does not start the service itself.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            OverlayService.showStartNotification(context)
        }
    }
}
