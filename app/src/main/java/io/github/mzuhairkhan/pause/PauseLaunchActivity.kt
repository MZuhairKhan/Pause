package io.github.mzuhairkhan.pause

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings

/**
 * Transparent, no UI of its own: exists only so opening the picker from the Quick Settings
 * tile or the widget's body tap happens from an already-foreground activity, which makes
 * `OverlayService.openPicker`'s `startForegroundService` call unambiguously a foreground
 * start rather than one the OS might refuse. Finishes immediately either way.
 */
class PauseLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Settings.canDrawOverlays(this)) {
            OverlayService.openPicker(this)
        } else {
            // No overlay permission: send the user to the app's own permission-grant UI
            // rather than opening a picker that couldn't draw anyway.
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                }
            )
        }
        finish()
    }
}
