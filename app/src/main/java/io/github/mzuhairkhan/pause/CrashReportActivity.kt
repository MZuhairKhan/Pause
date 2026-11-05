package io.github.mzuhairkhan.pause

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.mzuhairkhan.pause.ui.theme.PauseTheme
import java.io.File
import java.io.IOException
import org.acra.file.CrashReportPersister
import org.json.JSONException

/**
 * The screen a crash notification, or Feedback's "Report the last crash", opens. It shows exactly
 * what would be shared and has four ways out. Nothing leaves the phone except through one of them.
 */
class CrashReportActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val file = intent.getStringExtra(EXTRA_REPORT_PATH)?.let(::File)?.takeIf(::isOwnFile)
        val report = file?.let(::load)

        setContent {
            val context = LocalContext.current
            PauseTheme(
                themeMode = SettingsStore.themeMode(context),
                accentColor = SettingsStore.accentColor(context)
            ) {
                Scaffold { padding ->
                    if (file == null || report == null) {
                        Unavailable(Modifier.padding(padding), onClose = ::finish)
                    } else {
                        CrashReportScreen(
                            report = report,
                            modifier = Modifier.padding(padding),
                            onReportOnGitHub = { note -> reportOnGitHub(file, report, note) },
                            onCopy = { note -> copyReport(report, note, R.string.crash_copied) },
                            onShare = { note -> shareReport(report, note) },
                            onDiscard = {
                                file.delete()
                                CrashNotification.clear(this@CrashReportActivity)
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }

    /** Only a file inside the app's own storage: the path arrives in an intent extra. */
    private fun isOwnFile(file: File): Boolean = try {
        file.canonicalPath.startsWith(File(applicationInfo.dataDir).canonicalPath + File.separator)
    } catch (e: IOException) {
        false
    }

    private fun load(file: File): CrashReport? = try {
        val data = CrashReportPersister().load(file)
        CrashReport.from { data.getString(it) }
    } catch (e: IOException) {
        null // gone already, or unreadable
    } catch (e: JSONException) {
        null // truncated by the crash it describes
    }

    private fun reportOnGitHub(file: File, report: CrashReport, note: String) {
        // The link carries only a trimmed trace; the whole report is on the clipboard to paste.
        copyReport(report, note, R.string.crash_copied_for_github)
        val url = IssueUrl.build(
            LinkTargets.NEW_ISSUE,
            IssueTemplates.CRASH,
            CrashIssue.fields(DeviceInfo.collect(this), report, note)
        )
        // Handed over only once something opened it; otherwise it stays, still on the clipboard.
        if (openLink(this, url)) {
            file.delete()
            CrashNotification.clear(this)
            finish()
        }
    }

    private fun copyReport(report: CrashReport, note: String, @androidx.annotation.StringRes message: Int) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Pause crash report", report.toMarkdown(note)))
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun shareReport(report: CrashReport, note: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Pause crash report")
            putExtra(Intent.EXTRA_TEXT, report.toMarkdown(note))
        }
        startActivity(Intent.createChooser(send, null))
    }

    companion object {
        const val EXTRA_REPORT_PATH = "report_path"

        fun intent(context: Context, reportFile: File): Intent =
            Intent(context, CrashReportActivity::class.java).putExtra(EXTRA_REPORT_PATH, reportFile.path)
    }
}

@Composable
private fun Unavailable(modifier: Modifier, onClose: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.crash_unavailable), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onClose) { Text(stringResource(R.string.crash_close)) }
    }
}

@Composable
private fun CrashReportScreen(
    report: CrashReport,
    modifier: Modifier,
    onReportOnGitHub: (String) -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onDiscard: () -> Unit
) {
    var note by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.crash_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.crash_intro), style = MaterialTheme.typography.bodyMedium)

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text(stringResource(R.string.crash_note_label)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )

        // Exactly the text that Copy and Share send, including the note as it is typed.
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                report.toMarkdown(note),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .padding(12.dp)
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState())
            )
        }

        Button(onClick = { onReportOnGitHub(note) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.crash_report_github))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { onCopy(note) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.crash_copy))
            }
            OutlinedButton(onClick = { onShare(note) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.crash_share))
            }
        }
        TextButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.crash_discard))
        }
    }
}
