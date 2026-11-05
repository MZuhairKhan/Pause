package io.github.mzuhairkhan.pause

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import java.net.URLEncoder

/** The four GitHub issue forms in `.github/ISSUE_TEMPLATE`. */
internal object IssueTemplates {
    const val BUG = "bug_report.yml"
    const val CRASH = "crash_report.yml"
    const val FEATURE = "feature_request.yml"
    const val QUESTION = "question.yml"
}

/**
 * Field ids in those forms. GitHub pre-fills a form field from a query parameter named after its
 * `id`, so these are a contract with the YAML; `IssueTemplateContractTest` keeps them in step.
 */
internal object IssueFields {
    const val APP_VERSION = "app-version"
    const val INSTALL_SOURCE = "install-source"
    const val ANDROID_VERSION = "android-version"
    const val DEVICE_MODEL = "device-model"
    const val ROM = "rom"
    const val PERMISSIONS = "permissions"

    /** What a bug or crash report carries, in the order the forms ask for it. */
    val DEVICE = listOf(APP_VERSION, INSTALL_SOURCE, ANDROID_VERSION, DEVICE_MODEL, ROM, PERMISSIONS)
}

/**
 * Builds a "new issue" link that opens a form with some fields already filled in. Pure, so it
 * unit-tests on a plain JVM.
 */
internal object IssueUrl {
    /**
     * GitHub answers an over-long URL with 414, and what it will accept is not documented, so
     * stay well under the common 8 KB server limit.
     */
    const val MAX_LENGTH = 6_000

    fun build(
        base: String,
        template: String,
        fields: Map<String, String>,
        maxLength: Int = MAX_LENGTH
    ): String {
        // How many characters of each value to keep. The longest is cut first, so short fields
        // such as the version survive whole and only the crash log gives way.
        val keep = fields.mapValues { it.value.length }.toMutableMap()

        fun render(): String = buildString {
            append(base).append("?template=").append(encode(template))
            for ((key, value) in fields) {
                val n = keep.getValue(key)
                val shown = if (n < value.length) value.take(n) + "…" else value
                append('&').append(encode(key)).append('=').append(encode(shown))
            }
        }

        var url = render()
        while (url.length > maxLength) {
            val longest = keep.maxByOrNull { it.value }?.takeIf { it.value > 0 } ?: break
            // Encoding makes text longer than its character count, so cut by a share of the
            // overshoot rather than by an exact amount, and re-measure.
            val cut = maxOf(1, (url.length - maxLength) / 4)
            keep[longest.key] = (longest.value - cut).coerceAtLeast(0)
            url = render()
        }
        return url
    }

    private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
}

/**
 * What a bug report says about the phone it came from: the things most Pause bugs depend on.
 * Deliberately not the chosen apps to block, the timer or any setting: those say what someone does
 * on their phone, and a report doesn't need them.
 */
internal data class DeviceInfo(
    val appVersion: String,
    val installSource: String,
    val androidVersion: String,
    val deviceModel: String,
    val rom: String,
    val permissions: String
) {
    /** Keyed by the issue forms' field ids, in the order [IssueFields.DEVICE] lists them. */
    fun fields(): Map<String, String> = linkedMapOf(
        IssueFields.APP_VERSION to appVersion,
        IssueFields.INSTALL_SOURCE to installSource,
        IssueFields.ANDROID_VERSION to androidVersion,
        IssueFields.DEVICE_MODEL to deviceModel,
        IssueFields.ROM to rom,
        IssueFields.PERMISSIONS to permissions
    )

    companion object {
        /** Read at the moment of the tap: permissions change while the page sits open. */
        fun collect(context: Context): DeviceInfo = DeviceInfo(
            appVersion = appVersionName(context),
            installSource = InstallSource.label(installerPackage(context)),
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            rom = "${Build.MANUFACTURER} ${Build.DISPLAY}",
            permissions = permissionSummary(
                overlay = Settings.canDrawOverlays(context),
                notifications = hasNotificationPermission(context),
                battery = isBatteryOptimizationIgnored(context),
                usage = hasUsageAccess(context)
            )
        )

        fun permissionSummary(overlay: Boolean, notifications: Boolean, battery: Boolean, usage: Boolean): String {
            fun word(granted: Boolean) = if (granted) "granted" else "not granted"
            return "overlay: ${word(overlay)}, notifications: ${word(notifications)}, " +
                "battery exemption: ${word(battery)}, usage access: ${word(usage)}"
        }

        private fun installerPackage(context: Context): String? = try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(context.packageName)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
}

/**
 * Opens a link in whatever handles it, and says whether anything did. Some F-Droid devices have no
 * browser installed at all, in which case the person is told instead of the app crashing.
 */
internal fun openLink(context: Context, url: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    true
} catch (e: ActivityNotFoundException) {
    Toast.makeText(context, R.string.about_no_browser, Toast.LENGTH_SHORT).show()
    false
}

/**
 * `gradle.properties` turns `BuildConfig` off project-wide, so the version comes from
 * `PackageManager` instead. The `int` overload works unchanged on every API level this app
 * supports, so there is no need for the `PackageInfoFlags` one added in 33.
 */
@Suppress("DEPRECATION")
internal fun appVersionName(context: Context): String {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    return info.versionName ?: "?"
}

/** Names where the app was installed from, for bug reports. */
internal object InstallSource {
    fun label(installerPackage: String?): String = when {
        installerPackage.isNullOrBlank() -> "Unknown (APK or adb)"
        installerPackage == "org.fdroid.fdroid" || installerPackage == "org.fdroid.basic" -> "F-Droid"
        installerPackage == "com.android.vending" -> "Google Play"
        else -> installerPackage
    }
}
