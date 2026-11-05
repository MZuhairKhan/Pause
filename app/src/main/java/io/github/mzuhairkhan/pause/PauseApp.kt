package io.github.mzuhairkhan.pause

import android.app.Application
import android.content.Context
import org.acra.ReportField
import org.acra.data.StringFormat
import org.acra.ktx.initAcra

/**
 * Installs ACRA's crash capture. Nothing is ever sent from here: no sender module is included and
 * the app has no internet permission. A crash is saved on the phone, and [CrashInteraction] offers
 * it to the user.
 */
class PauseApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        initAcra {
            reportFormat = StringFormat.JSON
            reportContent = REPORT_FIELDS
            // Keep the system's own crash dialog and its `logcat -b crash` entry.
            alsoReportToAndroidFramework = true
        }
    }

    companion object {
        /**
         * An allowlist, not ACRA's defaults. Left out on purpose: shared preferences (they hold
         * the apps the person chose to block), system settings, device and installation ids, the
         * environment and the device feature list. `CrashReportFieldsTest` pins this.
         */
        internal val REPORT_FIELDS = listOf(
            ReportField.APP_VERSION_CODE,
            ReportField.APP_VERSION_NAME,
            ReportField.PACKAGE_NAME,
            ReportField.PHONE_MODEL,
            ReportField.BRAND,
            ReportField.PRODUCT,
            ReportField.ANDROID_VERSION,
            ReportField.STACK_TRACE,
            ReportField.USER_APP_START_DATE,
            ReportField.USER_CRASH_DATE,
            ReportField.LOGCAT
        )
    }
}
