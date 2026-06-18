package com.lifelineventures.pause

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-posts the persistent "Start Pause" notification after the device boots, so the overlay
 * can be launched from the shade without first opening the app. Does not start the service
 * itself — that's left to the user tapping Start.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            OverlayService.showStartNotification(context)
        }
    }
}
