package com.lifelineventures.pause

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lifelineventures.pause.ui.theme.Accents
import com.lifelineventures.pause.ui.theme.PauseTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** A clear success green for granted-permission affordances, readable on light or dark. */
private val GrantedGreen = Color(0xFF2EBD6B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            var themeMode by remember { mutableStateOf(SettingsStore.themeMode(context)) }
            var accentColor by remember { mutableStateOf(SettingsStore.accentColor(context)) }
            PauseTheme(themeMode = themeMode, accentColor = accentColor) {
                Scaffold { padding ->
                    OnboardingScreen(
                        modifier = Modifier.padding(padding),
                        themeMode = themeMode,
                        onThemeModeChange = {
                            themeMode = it
                            SettingsStore.setThemeMode(context, it)
                        },
                        accentColor = accentColor,
                        onAccentChange = {
                            accentColor = it
                            SettingsStore.setAccentColor(context, it)
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
    accentColor: Int,
    onAccentChange: (Int) -> Unit
) {
    val context = LocalContext.current
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var notificationsGranted by remember { mutableStateOf(hasNotificationPermission(context)) }
    var batteryExempt by remember { mutableStateOf(isBatteryOptimizationIgnored(context)) }
    val serviceRunning by OverlayService.running.collectAsState()
    var showCountdown by remember { mutableStateOf(SettingsStore.showCountdown(context)) }
    var inhale by remember { mutableStateOf(SettingsStore.inhaleSeconds(context)) }
    var hold by remember { mutableStateOf(SettingsStore.holdSeconds(context)) }
    var exhale by remember { mutableStateOf(SettingsStore.exhaleSeconds(context)) }
    var lockSec by remember { mutableStateOf(SettingsStore.lockSeconds(context)) }
    var snoozeMin by remember { mutableStateOf(SettingsStore.snoozeMinutes(context)) }
    var blockMinutes by remember { mutableStateOf(SettingsStore.blockMinutes(context)) }
    var blockedApps by remember { mutableStateOf(SettingsStore.blockedApps(context)) }
    var usageAccessGranted by remember { mutableStateOf(hasUsageAccess(context)) }
    var showColorDialog by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }

    val notificationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted = granted
    }

    // Re-read permission state every time the screen returns to the foreground. The
    // overlay, battery, and usage permissions are granted on external Settings screens
    // that report nothing back, so without this the rows would stay stale at "Grant"
    // after the user comes back. ON_RESUME also fires on first display, so this supplies
    // the initial reconciliation too (the remember initializers give the first-frame value).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = Settings.canDrawOverlays(context)
                notificationsGranted = hasNotificationPermission(context)
                batteryExempt = isBatteryOptimizationIgnored(context)
                usageAccessGranted = hasUsageAccess(context)
                // Make sure the persistent "Start Pause" notification is present while the
                // overlay is off (it self-skips when running or without notification access).
                OverlayService.showStartNotification(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The three required permissions drive the "All set ✓" summary and the Start button.
    // Usage access is optional, so it only gates the *auto-collapse* — the section stays
    // open (showing the optional row) until everything, including usage access, is granted.
    val requiredPermissionsGranted = overlayGranted && notificationsGranted && batteryExempt
    val everyPermissionGranted = requiredPermissionsGranted && usageAccessGranted

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Hero(accentColor)

        Button(
            modifier = Modifier.fillMaxWidth(),
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

        PermissionsSection(
            summaryGranted = requiredPermissionsGranted,
            collapseGranted = everyPermissionGranted
        ) {
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
            PermissionRow(
                label = "Usage access (optional)",
                granted = usageAccessGranted,
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            )
        }

        SettingsSection("Bubble") {
            SwitchRow(
                "Show countdown on the bubble",
                showCountdown,
                subtitle = "Off shows a draining hourglass instead."
            ) {
                showCountdown = it
                SettingsStore.setShowCountdown(context, it)
            }
        }

        SettingsSection("Appearance") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("System", "Light", "Dark").forEachIndexed { index, label ->
                    val labelText = @Composable {
                        Text(label, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                    }
                    if (index == themeMode) {
                        Button(
                            onClick = { onThemeModeChange(index) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) { labelText() }
                    } else {
                        OutlinedButton(
                            onClick = { onThemeModeChange(index) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) { labelText() }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Accents.colors.forEach { colorInt ->
                    AccentChip(
                        color = colorInt,
                        selected = colorInt == accentColor,
                        onClick = { onAccentChange(colorInt) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(accentColor))
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
                Text(
                    "Custom color",
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(onClick = { showColorDialog = true }) { Text("Pick…") }
            }
        }

        SettingsSection("Breathing wind-down") {
            StepperRow("Breathe in", inhale) {
                inhale = it
                SettingsStore.setInhaleSeconds(context, it)
            }
            StepperRow("Hold", hold) {
                hold = it
                SettingsStore.setHoldSeconds(context, it)
            }
            StepperRow("Breathe out", exhale) {
                exhale = it
                SettingsStore.setExhaleSeconds(context, it)
            }
            StepperRow(
                "No-skip lock",
                lockSec,
                min = SettingsRanges.LOCK_MIN_SECONDS,
                max = SettingsRanges.LOCK_MAX_SECONDS
            ) {
                lockSec = it
                SettingsStore.setLockSeconds(context, it)
            }
            StepperRow(
                "Snooze length",
                snoozeMin,
                min = SettingsRanges.SNOOZE_MIN_MINUTES,
                max = SettingsRanges.SNOOZE_MAX_MINUTES,
                unit = "m"
            ) {
                snoozeMin = it
                SettingsStore.setSnoozeMinutes(context, it)
            }
        }

        SettingsSection("App blocking", initiallyExpanded = false) {
            Text(
                "\"Stop for now\" covers these apps if you open them during the break.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!usageAccessGranted) {
                Text(
                    "Needs the Usage access permission above to detect blocked apps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            StepperRow("Break length", blockMinutes, min = 1, max = 120, unit = " min") {
                blockMinutes = it
                SettingsStore.setBlockMinutes(context, it)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (blockedApps.isEmpty()) "No apps chosen"
                    else "${blockedApps.size} app${if (blockedApps.size == 1) "" else "s"} chosen",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(onClick = { showAppPicker = true }) { Text("Choose…") }
            }
        }
    }

    if (showColorDialog) {
        CustomColorDialog(
            initial = accentColor,
            onPick = onAccentChange,
            onDismiss = { showColorDialog = false }
        )
    }

    if (showAppPicker) {
        AppPickerDialog(
            selected = blockedApps,
            onToggle = { pkg, on ->
                val next = if (on) blockedApps + pkg else blockedApps - pkg
                blockedApps = next
                SettingsStore.setBlockedApps(context, next)
            },
            onDismiss = { showAppPicker = false }
        )
    }
}

@Composable
private fun Hero(accentColor: Int) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BubblePreview(accentColor)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Pause",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "A floating button you tap to set a \"stop using this app\" timer.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A static hourglass logo (mostly-full, not animated) on a soft accent glow. */
@Composable
private fun BubblePreview(accentColor: Int) {
    val accent = Color(accentColor)
    // Chip and glyph follow the theme so the logo reads on both light and dark backgrounds
    // (the glyph contrasts the chip in either mode), keyed so it rebuilds on a theme switch.
    val chipColor = MaterialTheme.colorScheme.surfaceVariant
    val glyphColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    // The same frame the launcher/notification icons are baked from (~70% top, 30% bottom,
    // mid-flow) so the in-app logo and the icons match exactly.
    val drawable = remember(glyphColor) { HourglassDrawable(glyphColor).apply { setProgress(0.58f) } }
    Box(
        modifier = Modifier.size(132.dp),
        contentAlignment = Alignment.Center
    ) {
        // Soft accent halo behind the bubble.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(listOf(accent.copy(alpha = 0.30f), Color.Transparent)),
                    CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(chipColor)
                .border(1.5.dp, accent.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx -> ImageView(ctx) },
                update = { it.setImageDrawable(drawable) },
                modifier = Modifier.size(54.dp)
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    initiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Chevron(expanded, MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )
            }
        }
    }
}

/**
 * Permissions get their own section. The green "All set ✓" summary shows once the
 * required permissions are granted ([summaryGranted]); the section only auto-collapses
 * once everything including the optional usage access is granted ([collapseGranted]), so
 * the optional row stays visible. It re-expands if a permission is later revoked, and can
 * always be collapsed/expanded by hand.
 */
@Composable
private fun PermissionsSection(
    summaryGranted: Boolean,
    collapseGranted: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(!collapseGranted) }
    LaunchedEffect(collapseGranted) { expanded = !collapseGranted }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Permissions",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium
            )
            if (summaryGranted) {
                Text(
                    "All set ✓",
                    color = GrantedGreen,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(8.dp))
            }
            Chevron(expanded, MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )
            }
        }
    }
}

/** A crisp Canvas chevron that rotates from ▸ (collapsed) to ▾ (expanded). */
@Composable
private fun Chevron(expanded: Boolean, tint: Color) {
    val rotation by animateFloatAsState(if (expanded) 0f else -90f, label = "chevron")
    Canvas(
        modifier = Modifier
            .size(18.dp)
            .rotate(rotation)
    ) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.28f, h * 0.40f)
            lineTo(w * 0.50f, h * 0.62f)
            lineTo(w * 0.72f, h * 0.40f)
        }
        drawPath(
            path,
            color = tint,
            style = Stroke(width = w * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/** A circular accent swatch whose ring grows and brightens with a spring when selected. */
@Composable
private fun AccentChip(color: Int, selected: Boolean, onClick: () -> Unit) {
    val borderWidth by animateDpAsState(if (selected) 3.dp else 1.dp, label = "accentBorder")
    val chipSize by animateDpAsState(if (selected) 38.dp else 34.dp, label = "accentSize")
    Box(
        modifier = Modifier
            .size(chipSize)
            .clip(CircleShape)
            .background(Color(color))
            .border(
                width = borderWidth,
                color = if (selected) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.outline
                },
                shape = CircleShape
            )
            .clickable { onClick() }
    )
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    subtitle: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CustomColorDialog(initial: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val seed = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember { mutableStateOf(seed[0]) }
    var sat by remember { mutableStateOf(seed[1]) }
    var value by remember { mutableStateOf(seed[2]) }

    fun emit() = onPick(Color.hsv(hue, sat, value).toArgb())

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Custom color", style = MaterialTheme.typography.titleMedium)
                ColorWheel(
                    hue = hue,
                    saturation = sat,
                    value = value,
                    modifier = Modifier.size(240.dp)
                ) { h, s ->
                    hue = h
                    sat = s
                    emit()
                }
                Slider(
                    value = value,
                    valueRange = 0f..1f,
                    onValueChange = {
                        value = it
                        emit()
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.hsv(hue, sat, value))
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                    Text(
                        "Preview",
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = onDismiss) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun ColorWheel(
    hue: Float,
    saturation: Float,
    value: Float,
    modifier: Modifier = Modifier,
    onChange: (Float, Float) -> Unit
) {
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset -> emitWheel(offset, size, onChange) }
            }
            .pointerInput(Unit) {
                detectDragGestures(onDragStart = { emitWheel(it, size, onChange) }) { change, _ ->
                    change.consume()
                    emitWheel(change.position, size, onChange)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red),
                    center = c
                ),
                radius = r,
                center = c
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White, Color.Transparent),
                    center = c,
                    radius = r
                ),
                radius = r,
                center = c
            )
            if (value < 1f) {
                drawCircle(color = Color.Black.copy(alpha = 1f - value), radius = r, center = c)
            }
            val angle = Math.toRadians(hue.toDouble())
            val selR = saturation * r
            val sel = Offset(
                c.x + (cos(angle) * selR).toFloat(),
                c.y + (sin(angle) * selR).toFloat()
            )
            drawCircle(color = Color.White, radius = 9f, center = sel, style = Stroke(width = 4f))
            drawCircle(color = Color.Black, radius = 11f, center = sel, style = Stroke(width = 1.5f))
        }
    }
}

private fun emitWheel(offset: Offset, size: IntSize, onChange: (Float, Float) -> Unit) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val dx = offset.x - cx
    val dy = offset.y - cy
    val maxR = minOf(cx, cy)
    val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    if (angle < 0f) angle += 360f
    val sat = (dist / maxR).coerceIn(0f, 1f)
    onChange(angle, sat)
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(if (granted) Modifier else Modifier.clickable { onClick() })
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(
                    if (granted) GrantedGreen.copy(alpha = 0.20f)
                    else MaterialTheme.colorScheme.errorContainer
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (granted) "✓" else "!",
                color = if (granted) GrantedGreen else MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            label,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            if (granted) "Granted" else "Grant",
            color = if (granted) GrantedGreen else MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (granted) FontWeight.Normal else FontWeight.SemiBold
        )
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: Int,
    min: Int = 1,
    max: Int = 20,
    unit: String = "s",
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { onChange((value - 1).coerceAtLeast(min)) }) { Text("−") }
        Text(
            "$value$unit",
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.titleMedium
        )
        OutlinedButton(onClick = { onChange((value + 1).coerceAtMost(max)) }) { Text("+") }
    }
}

/** A package + display label for an installed, launchable app. */
private data class AppEntry(val packageName: String, val label: String)

/** Loads launchable apps (excluding this one) off the main thread, sorted by label. */
// produceState does assign value below; the lint check is a known false positive when
// the assignment's source is a withContext { } call (issuetracker.google.com/265036856).
@SuppressLint("ProduceStateDoesNotAssignValue")
@Composable
private fun rememberLaunchableApps(): List<AppEntry> {
    val context = LocalContext.current
    val apps by produceState(initialValue = emptyList<AppEntry>(), context) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, 0)
                .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                .filter { it.packageName != context.packageName }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        }
    }
    return apps
}

@Composable
private fun AppPickerDialog(
    selected: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val apps = rememberLaunchableApps()
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Apps to block", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (apps.isEmpty()) {
                    Text(
                        "Loading apps…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(filtered, key = { it.packageName }) { app ->
                            val checked = app.packageName in selected
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable { onToggle(app.packageName, !checked) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { onToggle(app.packageName, it) }
                                )
                                Text(
                                    app.label,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 8.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    Button(onClick = onDismiss) { Text("Done") }
                }
            }
        }
    }
}

private fun hasUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
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
