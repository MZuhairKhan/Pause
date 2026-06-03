package com.lifelineventures.pause

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lifelineventures.pause.ui.theme.Accents
import com.lifelineventures.pause.ui.theme.PauseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            var themeMode by remember { mutableStateOf(SettingsStore.themeMode(context)) }
            var accentIndex by remember { mutableStateOf(SettingsStore.accentIndex(context)) }
            PauseTheme(themeMode = themeMode, accentIndex = accentIndex) {
                Scaffold { padding ->
                    OnboardingScreen(
                        modifier = Modifier.padding(padding),
                        themeMode = themeMode,
                        onThemeModeChange = {
                            themeMode = it
                            SettingsStore.setThemeMode(context, it)
                        },
                        accentIndex = accentIndex,
                        onAccentChange = {
                            accentIndex = it
                            SettingsStore.setAccentIndex(context, it)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingScreen(
    modifier: Modifier = Modifier,
    themeMode: Int,
    onThemeModeChange: (Int) -> Unit,
    accentIndex: Int,
    onAccentChange: (Int) -> Unit
) {
    val context = LocalContext.current
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var notificationsGranted by remember { mutableStateOf(hasNotificationPermission(context)) }
    var batteryExempt by remember { mutableStateOf(isBatteryOptimizationIgnored(context)) }
    val serviceRunning by OverlayService.running.collectAsState()
    var showCountdown by remember { mutableStateOf(SettingsStore.showCountdown(context)) }

    val notificationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted = granted
    }

    LaunchedEffect(Unit) {
        overlayGranted = Settings.canDrawOverlays(context)
        notificationsGranted = hasNotificationPermission(context)
        batteryExempt = isBatteryOptimizationIgnored(context)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Pause", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Three quick permissions and the floating button is ready to host a timer.",
            style = MaterialTheme.typography.bodyMedium
        )

        PermissionRow(
            label = "Display over other apps",
            granted = overlayGranted,
            onClick = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
            }
        )

        PermissionRow(
            label = "Post notifications",
            granted = notificationsGranted,
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    notificationsGranted = true
                }
            }
        )

        PermissionRow(
            label = "Ignore battery optimization",
            granted = batteryExempt,
            onClick = {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Show countdown on the bubble",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            Switch(
                checked = showCountdown,
                onCheckedChange = {
                    showCountdown = it
                    SettingsStore.setShowCountdown(context, it)
                }
            )
        }

        Text("Appearance", style = MaterialTheme.typography.titleMedium)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("System", "Light", "Dark").forEachIndexed { index, label ->
                if (index == themeMode) {
                    Button(
                        onClick = { onThemeModeChange(index) },
                        modifier = Modifier.weight(1f)
                    ) { Text(label) }
                } else {
                    OutlinedButton(
                        onClick = { onThemeModeChange(index) },
                        modifier = Modifier.weight(1f)
                    ) { Text(label) }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Accents.colors.forEachIndexed { index, colorInt ->
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(colorInt))
                        .border(
                            width = if (index == accentIndex) 3.dp else 1.dp,
                            color = if (index == accentIndex) {
                                MaterialTheme.colorScheme.onBackground
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            shape = CircleShape
                        )
                        .clickable { onAccentChange(index) }
                )
            }
        }

        Button(
            enabled = overlayGranted && notificationsGranted,
            onClick = {
                if (serviceRunning) {
                    OverlayService.stop(context)
                } else {
                    OverlayService.start(context)
                }
            }
        ) {
            Text(if (serviceRunning) "Stop overlay service" else "Start overlay service")
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onClick: () -> Unit) {
    val status = if (granted) "granted" else "needed"
    Button(onClick = onClick, enabled = !granted) {
        Text("$label — $status")
    }
}

private fun hasNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

private fun isBatteryOptimizationIgnored(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}
