package io.github.mzuhairkhan.pause

import android.content.Context
import java.io.File
import org.acra.ReportField
import org.acra.file.ReportLocator

/**
 * Shortens a stack trace to what fits in a link, keeping what a maintainer reads first: each
 * exception's header line and the top of its frames. The full trace still goes to the clipboard.
 * Pure, so it unit-tests on a plain JVM.
 */
internal object StackTraceDigest {
    const val FRAMES_PER_CAUSE = 6

    fun digest(stackTrace: String, maxChars: Int, framesPerCause: Int = FRAMES_PER_CAUSE): String {
        if (maxChars <= 0) return ""
        val kept = mutableListOf<String>()
        var framesSeen = 0
        var framesKept = 0

        fun noteOmittedFrames() {
            val omitted = framesSeen - framesKept
            if (omitted > 0) kept += "\t... $omitted more frames"
        }

        for (line in stackTrace.lines()) {
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("at ") -> {
                    framesSeen++
                    if (framesSeen <= framesPerCause) {
                        kept += line
                        framesKept++
                    }
                }
                // The JVM's own "... N more" refers to frames already shown for the outer exception.
                trimmed.startsWith("...") || line.isBlank() -> Unit
                else -> {
                    noteOmittedFrames()
                    framesSeen = 0
                    framesKept = 0
                    kept += line
                }
            }
        }
        noteOmittedFrames()

        val joined = kept.joinToString("\n")
        if (joined.length <= maxChars) return joined
        // Cut at a line so no frame is left half-written, and say so.
        val cut = joined.take(maxChars - 1)
        val lineEnd = cut.lastIndexOf('\n')
        return (if (lineEnd > 0) cut.take(lineEnd) else cut) + "…"
    }
}

/** Fills the crash issue form from a saved crash. */
internal object CrashIssue {
    const val WHAT_HAPPENED = "what-happened"
    const val CRASH_LOG = "crash-log"

    /** Characters of stack trace carried in the link; the full report goes to the clipboard. */
    const val LOG_CHARS = 2_500

    fun fields(device: DeviceInfo, report: CrashReport, note: String): Map<String, String> {
        val fields = LinkedHashMap<String, String>()
        // The version that crashed, which may not be the one installed by the time it is reported.
        fields.putAll(device.copy(appVersion = report.appVersion.ifBlank { device.appVersion }).fields())
        // Left out when blank, so the form's required "what happened" still asks the person.
        note.trim().takeIf { it.isNotEmpty() }?.let { fields[WHAT_HAPPENED] = it }
        fields[CRASH_LOG] = StackTraceDigest.digest(report.stackTrace, LOG_CHARS)
        return fields
    }
}

/** The newest crash saved on the phone that nobody has reported or dismissed, if any. */
internal object PendingCrash {
    fun latest(context: Context): File? =
        ReportLocator(context).unapprovedReports.maxByOrNull { it.lastModified() }
}

/**
 * A saved crash, as the report screen shows it and as it is shared. Built from ACRA's stored
 * report, which [org.acra.ReportField] limits to the fields `PauseApp` asks for.
 */
internal data class CrashReport(
    val appVersion: String,
    val androidVersion: String,
    val device: String,
    val crashedAt: String,
    val stackTrace: String,
    val log: String
) {
    /** The exception line, e.g. `java.lang.IllegalStateException: boom`. */
    val headline: String get() = stackTrace.lineSequence().firstOrNull().orEmpty()

    /** The full report as Markdown, for the clipboard and the share sheet. */
    fun toMarkdown(note: String): String = buildString {
        if (note.isNotBlank()) {
            append("## What I was doing\n\n").append(note.trim()).append("\n\n")
        }
        append("## Crash\n\n")
        append("- Pause: ").append(appVersion).append('\n')
        append("- Android: ").append(androidVersion).append('\n')
        append("- Device: ").append(device).append('\n')
        append("- When: ").append(crashedAt).append('\n')
        append("\n### Stack trace\n\n```\n").append(stackTrace.trim()).append("\n```\n")
        if (log.isNotBlank()) {
            append("\n### Log (Pause's own process)\n\n```\n").append(log.trim()).append("\n```\n")
        }
    }

    companion object {
        fun from(field: (ReportField) -> String?): CrashReport {
            fun value(f: ReportField) = field(f).orEmpty()
            val code = value(ReportField.APP_VERSION_CODE)
            return CrashReport(
                appVersion = value(ReportField.APP_VERSION_NAME) + if (code.isNotEmpty()) " ($code)" else "",
                androidVersion = value(ReportField.ANDROID_VERSION),
                device = "${value(ReportField.BRAND)} ${value(ReportField.PHONE_MODEL)}".trim(),
                crashedAt = value(ReportField.USER_CRASH_DATE),
                stackTrace = value(ReportField.STACK_TRACE),
                log = value(ReportField.LOGCAT)
            )
        }
    }
}
