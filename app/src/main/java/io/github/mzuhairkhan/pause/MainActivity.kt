package io.github.mzuhairkhan.pause

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
import android.util.LruCache
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.annotation.StringRes
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.mzuhairkhan.pause.ui.theme.Accents
import io.github.mzuhairkhan.pause.ui.theme.PauseTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** A clear success green for granted-permission affordances, readable on light or dark. */
private val GrantedGreen = Color(0xFF2EBD6B)

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            var themeMode by remember { mutableStateOf(SettingsStore.themeMode(context)) }
            var accentColor by remember { mutableStateOf(SettingsStore.accentColor(context)) }
            var onboarded by remember { mutableStateOf(SettingsStore.onboardingComplete(context)) }
            PauseTheme(themeMode = themeMode, accentColor = accentColor) {
                Scaffold { padding ->
                    if (!onboarded) {
                        SetupWizard(
                            modifier = Modifier.padding(padding),
                            onFinish = { onboarded = true }
                        )
                    } else {
                        SettingsScreen(
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
}

@Composable
private fun SettingsScreen(
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
    var breathingOn by remember { mutableStateOf(SettingsStore.breathingEnabled(context)) }
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
                } else if (Settings.canDrawOverlays(context)) {
                    OverlayService.start(context)
                } else {
                    // The cached grant only refreshes on resume; if it was revoked while this
                    // screen stayed open, correct the flag (disabling the button) rather than
                    // starting a service that can't draw the bubble.
                    overlayGranted = false
                }
            }
        ) {
            Text(if (serviceRunning) stringResource(R.string.stop_overlay) else stringResource(R.string.start_overlay))
        }

        PermissionsSection(
            summaryGranted = requiredPermissionsGranted,
            collapseGranted = everyPermissionGranted
        ) {
            PermissionRow(
                label = stringResource(R.string.perm_overlay),
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
                label = stringResource(R.string.perm_notifications),
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
                label = stringResource(R.string.perm_battery),
                granted = batteryExempt,
                onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            )
            PermissionRow(
                label = stringResource(R.string.perm_usage),
                granted = usageAccessGranted,
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            )
        }

        SettingsSection(stringResource(R.string.section_bubble)) {
            SwitchRow(
                stringResource(R.string.show_countdown_title),
                showCountdown,
                subtitle = stringResource(R.string.show_countdown_subtitle)
            ) {
                showCountdown = it
                SettingsStore.setShowCountdown(context, it)
            }

            BubbleSizeChooser()
        }

        SettingsSection(stringResource(R.string.section_appearance)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    stringResource(R.string.theme_system),
                    stringResource(R.string.theme_light),
                    stringResource(R.string.theme_dark)
                ).forEachIndexed { index, label ->
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
                Accents.colors.forEachIndexed { index, colorInt ->
                    AccentChip(
                        color = colorInt,
                        name = Accents.names[index],
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
                    stringResource(R.string.custom_color),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(onClick = { showColorDialog = true }) { Text(stringResource(R.string.custom_color_pick)) }
            }
        }

        SettingsSection(stringResource(R.string.section_breathing)) {
            SwitchRow(
                stringResource(R.string.breathing_toggle),
                breathingOn,
                subtitle = stringResource(R.string.breathing_toggle_subtitle)
            ) {
                breathingOn = it
                SettingsStore.setBreathingEnabled(context, it)
            }
            // The breathing-specific controls are moot once the exercise is off; snooze still
            // applies (snooze is one of the dismiss options either way).
            if (breathingOn) {
                StepperRow(stringResource(R.string.breathing_in), inhale) {
                    inhale = it
                    SettingsStore.setInhaleSeconds(context, it)
                }
                StepperRow(stringResource(R.string.breathing_hold), hold) {
                    hold = it
                    SettingsStore.setHoldSeconds(context, it)
                }
                StepperRow(stringResource(R.string.breathing_out), exhale) {
                    exhale = it
                    SettingsStore.setExhaleSeconds(context, it)
                }
                StepperRow(
                    stringResource(R.string.no_skip_lock),
                    lockSec,
                    min = SettingsRanges.LOCK_MIN_SECONDS,
                    max = SettingsRanges.LOCK_MAX_SECONDS
                ) {
                    lockSec = it
                    SettingsStore.setLockSeconds(context, it)
                }
            }
            StepperRow(
                stringResource(R.string.snooze_length),
                snoozeMin,
                min = SettingsRanges.SNOOZE_MIN_MINUTES,
                max = SettingsRanges.SNOOZE_MAX_MINUTES,
                unitRes = R.string.unit_minutes_short
            ) {
                snoozeMin = it
                SettingsStore.setSnoozeMinutes(context, it)
            }
        }

        SettingsSection(stringResource(R.string.section_app_blocking), initiallyExpanded = false) {
            Text(
                stringResource(R.string.app_blocking_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!usageAccessGranted) {
                Text(
                    stringResource(R.string.usage_needed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            StepperRow(stringResource(R.string.break_length), blockMinutes, min = 1, max = 120, unitRes = R.string.unit_minutes_short) {
                blockMinutes = it
                SettingsStore.setBlockMinutes(context, it)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (blockedApps.isEmpty()) stringResource(R.string.apps_chosen_none)
                    else pluralStringResource(R.plurals.apps_chosen, blockedApps.size, blockedApps.size),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(onClick = { showAppPicker = true }) { Text(stringResource(R.string.choose)) }
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
            onClearAll = {
                blockedApps = emptySet()
                SettingsStore.setBlockedApps(context, emptySet())
            },
            onDismiss = { showAppPicker = false }
        )
    }
}

/**
 * The bubble size/alignment picker (presets + live readout + custom sliders), shared by the
 * settings screen and the setup wizard. Owns its own preset/size state and previews the change
 * on the real floating bubble when the overlay can draw.
 */
@Composable
private fun BubbleSizeChooser() {
    val context = LocalContext.current
    var bubblePreset by remember { mutableStateOf(SettingsStore.bubblePreset(context)) }
    var customBubbleSize by remember { mutableStateOf(SettingsStore.customBubbleSize(context)) }
    var customBubbleEdge by remember { mutableStateOf(SettingsStore.customBubbleEdge(context)) }
    val applyBubbleSize = {
        if (hasNotificationPermission(context) && Settings.canDrawOverlays(context)) {
            OverlayService.refreshBubble(context)
        }
    }
    Text(
        stringResource(R.string.match_icon_size),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            stringResource(R.string.preset_instagram),
            stringResource(R.string.preset_tiktok),
            stringResource(R.string.preset_shorts),
            stringResource(R.string.preset_custom)
        ).forEachIndexed { index, label ->
            val onPick = {
                bubblePreset = index
                SettingsStore.setBubblePreset(context, index)
                applyBubbleSize()
            }
            val content = @Composable {
                Text(
                    label,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            val contentPad = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            if (index == bubblePreset) {
                Button(onPick, Modifier.weight(1f), contentPadding = contentPad) { content() }
            } else {
                OutlinedButton(onPick, Modifier.weight(1f), contentPadding = contentPad) { content() }
            }
        }
    }
    val metrics = BubblePresets.metrics(bubblePreset, customBubbleSize, customBubbleEdge)
    BubbleSizeReadout(metrics.sizeFraction, metrics.edgeFraction)
    if (bubblePreset == BubblePresets.CUSTOM) {
        LabeledSlider(
            stringResource(R.string.bubble_size), customBubbleSize, BubblePresets.SIZE_MIN..BubblePresets.SIZE_MAX,
            onChange = { customBubbleSize = it },
            onCommit = {
                SettingsStore.setCustomBubbleSize(context, customBubbleSize)
                applyBubbleSize()
            }
        )
        LabeledSlider(
            stringResource(R.string.bubble_edge_gap), customBubbleEdge, BubblePresets.EDGE_MIN..BubblePresets.EDGE_MAX,
            onChange = { customBubbleEdge = it },
            onCommit = {
                SettingsStore.setCustomBubbleEdge(context, customBubbleEdge)
                applyBubbleSize()
            }
        )
    }
}

/**
 * One wizard page: a centered title + body, then its [content].
 *
 * The block is vertically centred rather than pinned to the top: with only a few rows of content
 * the earlier layout left roughly two thirds of the screen empty and read as unfinished.
 * `heightIn(min = viewport)` is what lets [Arrangement.Center] work inside a scrolling column —
 * short pages centre, while taller ones still grow and scroll from the top. The trailing spacer
 * seats the block slightly above true centre, which reads better than mathematical centring.
 */
@Composable
private fun WizardPage(
    title: String,
    body: String,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewport = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewport)
                .padding(top = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            content()
            Spacer(Modifier.height(viewport * 0.10f))
        }
    }
}

/**
 * First-run setup wizard: welcome -> language -> permissions -> bubble size -> done. The chosen
 * language is applied (via AppCompat per-app locales) on finish; finishing also marks onboarding
 * complete and starts the overlay if it can draw.
 */
@Composable
private fun SetupWizard(modifier: Modifier = Modifier, onFinish: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pageCount = 6
    val pager = rememberPagerState(pageCount = { pageCount })

    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var notificationsGranted by remember { mutableStateOf(hasNotificationPermission(context)) }
    var batteryExempt by remember { mutableStateOf(isBatteryOptimizationIgnored(context)) }
    var usageAccessGranted by remember { mutableStateOf(hasUsageAccess(context)) }
    var blockedApps by remember { mutableStateOf(SettingsStore.blockedApps(context)) }
    var showAppPicker by remember { mutableStateOf(false) }
    // Which app to size the bubble for (Instagram/TikTok/Shorts only here; Custom lives in settings).
    var mainApp by rememberSaveable {
        mutableStateOf(SettingsStore.bubblePreset(context).coerceIn(BubblePresets.INSTAGRAM, BubblePresets.SHORTS))
    }
    val notificationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> notificationsGranted = granted }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = Settings.canDrawOverlays(context)
                notificationsGranted = hasNotificationPermission(context)
                batteryExempt = isBatteryOptimizationIgnored(context)
                usageAccessGranted = hasUsageAccess(context)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    // null = system default; otherwise "en"/"fi". rememberSaveable so a mid-wizard config change
    // keeps the pick; normalised to the offered options (strip any region, fall back to system).
    var selectedLang by rememberSaveable {
        val primary = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            .substringBefore(',').substringBefore('-')
        mutableStateOf(primary.takeIf { it == "en" || it == "fi" })
    }

    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            when (page) {
                0 -> WizardPage(
                    stringResource(R.string.onb_welcome_title),
                    stringResource(R.string.onb_welcome_body)
                ) { BubblePreview(accentColor = SettingsStore.accentColor(context)) }

                1 -> WizardPage(
                    stringResource(R.string.onb_language_title),
                    stringResource(R.string.onb_language_body)
                ) {
                    val options = listOf(
                        null to stringResource(R.string.lang_system),
                        "en" to "English",
                        "fi" to "Suomi"
                    )
                    options.forEach { (tag, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .selectable(
                                    selected = selectedLang == tag,
                                    role = Role.RadioButton,
                                    onClick = { selectedLang = tag }
                                )
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedLang == tag, onClick = null)
                            Text(
                                label,
                                modifier = Modifier.padding(start = 12.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }

                2 -> WizardPage(
                    stringResource(R.string.onb_permissions_title),
                    stringResource(R.string.onb_permissions_body)
                ) {
                    PermissionRow(stringResource(R.string.perm_overlay), overlayGranted) {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                    PermissionRow(stringResource(R.string.perm_notifications), notificationsGranted) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            notificationsGranted = true
                        }
                    }
                    PermissionRow(stringResource(R.string.perm_battery), batteryExempt) {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        )
                    }
                }

                3 -> WizardPage(
                    stringResource(R.string.onb_apps_title),
                    stringResource(R.string.onb_apps_body)
                ) {
                    PermissionRow(stringResource(R.string.perm_usage), usageAccessGranted) {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (blockedApps.isEmpty()) stringResource(R.string.apps_chosen_none)
                            else pluralStringResource(R.plurals.apps_chosen, blockedApps.size, blockedApps.size),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedButton(onClick = { showAppPicker = true }) {
                            Text(stringResource(R.string.choose))
                        }
                    }
                }

                4 -> WizardPage(
                    stringResource(R.string.onb_size_title),
                    stringResource(R.string.onb_size_body)
                ) {
                    listOf(
                        BubblePresets.INSTAGRAM to stringResource(R.string.preset_instagram),
                        BubblePresets.TIKTOK to stringResource(R.string.preset_tiktok),
                        BubblePresets.SHORTS to stringResource(R.string.preset_shorts)
                    ).forEach { (preset, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .selectable(
                                    selected = mainApp == preset,
                                    role = Role.RadioButton,
                                    onClick = {
                                        mainApp = preset
                                        SettingsStore.setBubblePreset(context, preset)
                                    }
                                )
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = mainApp == preset, onClick = null)
                            Text(
                                label,
                                modifier = Modifier.padding(start = 12.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }

                else -> WizardPage(
                    stringResource(R.string.onb_done_title),
                    stringResource(R.string.onb_done_body)
                ) { BubblePreview(accentColor = SettingsStore.accentColor(context)) }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(pageCount) { i ->
                val active = pager.currentPage == i
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (active) 9.dp else 7.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (pager.currentPage > 0) {
                OutlinedButton(onClick = {
                    scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
                }) { Text(stringResource(R.string.onb_back)) }
            }
            Spacer(Modifier.weight(1f))
            val last = pager.currentPage == pageCount - 1
            Button(onClick = {
                if (last) {
                    AppCompatDelegate.setApplicationLocales(
                        if (selectedLang == null) LocaleListCompat.getEmptyLocaleList()
                        else LocaleListCompat.forLanguageTags(selectedLang)
                    )
                    SettingsStore.setOnboardingComplete(context, true)
                    if (Settings.canDrawOverlays(context) && notificationsGranted) {
                        OverlayService.start(context)
                    }
                    onFinish()
                } else {
                    scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }
            }) {
                Text(if (last) stringResource(R.string.onb_get_started) else stringResource(R.string.onb_next))
            }
        }

        if (showAppPicker) {
            AppPickerDialog(
                selected = blockedApps,
                onToggle = { pkg, on ->
                    val next = if (on) blockedApps + pkg else blockedApps - pkg
                    blockedApps = next
                    SettingsStore.setBlockedApps(context, next)
                },
                onClearAll = {
                    blockedApps = emptySet()
                    SettingsStore.setBlockedApps(context, emptySet())
                },
                onDismiss = { showAppPicker = false }
            )
        }
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
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(R.string.hero_tagline),
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
                stringResource(R.string.permissions_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium
            )
            if (summaryGranted) {
                Text(
                    stringResource(R.string.all_set),
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
private fun AccentChip(color: Int, name: String, selected: Boolean, onClick: () -> Unit) {
    val borderWidth by animateDpAsState(if (selected) 3.dp else 1.dp, label = "accentBorder")
    val chipSize by animateDpAsState(if (selected) 38.dp else 34.dp, label = "accentSize")
    // A 48dp touch target around the smaller visual swatch; selectable + labelled so TalkBack
    // announces the colour name and selected state instead of an unlabelled blob.
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center
    ) {
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
        )
    }
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

/**
 * The floating bubble's real dp size and edge offset for the current alignment, 1:1 with how
 * it lands on this device — size sets the glyph size, the edge gap moves it inward. The live
 * visual is the actual overlay bubble, which appears/resizes on screen as the size is changed;
 * this is just the numeric readout beneath the presets.
 */
@Composable
private fun BubbleSizeReadout(sizeFraction: Float, edgeFraction: Float) {
    val widthDp = LocalConfiguration.current.screenWidthDp
    val bubbleDp = widthDp * sizeFraction
    val gapDp = widthDp * edgeFraction
    Text(
        stringResource(R.string.bubble_size_readout, bubbleDp.roundToInt(), gapDp.roundToInt()),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onCommit: () -> Unit
) {
    Column {
        // One-decimal percent in the viewer's locale (Finnish renders "12,2 %").
        val locale = LocalConfiguration.current.locales[0]
        Text(
            stringResource(
                R.string.slider_readout,
                label,
                String.format(locale, "%.1f", value * 100f)
            ),
            style = MaterialTheme.typography.bodyMedium
        )
        // Snap to 0.1% (0.001) so the value has one-decimal precision. onChange updates state
        // live (so the preview tracks the drag); the persist + overlay refresh fire once on
        // release, so a drag doesn't spam startForegroundService.
        Slider(
            value = value,
            onValueChange = { onChange((it * 1000).roundToInt() / 1000f) },
            valueRange = range,
            onValueChangeFinished = onCommit
        )
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
                Text(stringResource(R.string.custom_color), style = MaterialTheme.typography.titleMedium)
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
                        stringResource(R.string.custom_color_preview),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = onDismiss) { Text(stringResource(R.string.done)) }
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
            if (granted) stringResource(R.string.perm_granted) else stringResource(R.string.perm_grant),
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
    @StringRes unitRes: Int = R.string.unit_seconds_short,
    onChange: (Int) -> Unit
) {
    val decreaseLabel = stringResource(R.string.stepper_decrease, label)
    val increaseLabel = stringResource(R.string.stepper_increase, label)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(
            onClick = { onChange((value - 1).coerceAtLeast(min)) },
            // A bare glyph tells a screen reader nothing about what it changes.
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = decreaseLabel },
            contentPadding = PaddingValues(0.dp)
        ) { Text("−") }
        Text(
            stringResource(unitRes, value),
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.titleMedium
        )
        OutlinedButton(
            onClick = { onChange((value + 1).coerceAtMost(max)) },
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = increaseLabel },
            contentPadding = PaddingValues(0.dp)
        ) { Text("+") }
    }
}

/** Caps concurrent launcher-icon decodes so a fast fling can't flood the IO thread pool. */
private val iconLoadSemaphore = Semaphore(4)

/** Max decoded icons kept in the per-dialog LRU (bounds memory on devices with many apps). */
private const val ICON_CACHE_MAX = 100

/** A package + display label for an installed, launchable app. */
private data class AppEntry(val packageName: String, val label: String)

/** Loads launchable apps (excluding this one) off the main thread, sorted by label. */
// produceState does assign value below; the lint check is a known false positive when
// the assignment's source is a withContext { } call (issuetracker.google.com/265036856).
@SuppressLint("ProduceStateDoesNotAssignValue")
@Composable
private fun rememberLaunchableApps(): List<AppEntry>? {
    val context = LocalContext.current
    // null while still loading; empty once loaded if there are no other launchable apps.
    val apps by produceState<List<AppEntry>?>(initialValue = null, context) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppPickerDialog(
    selected: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val apps = rememberLaunchableApps()   // null while loading
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        val list = apps ?: emptyList()
        if (query.isBlank()) list else list.filter { it.label.contains(query, ignoreCase = true) }
    }
    // Group the (already alphabetical) list by first letter for sticky A–Z headers; digits and
    // symbols fall under "#".
    val grouped = remember(filtered) {
        filtered.groupBy { it.label.firstOrNull()?.uppercaseChar()?.takeIf(Char::isLetter) ?: '#' }
    }
    // Bounded LRU of decoded icons so scrolling a large app list doesn't retain tens of MB.
    val iconCache = remember { LruCache<String, ImageBitmap>(ICON_CACHE_MAX) }
    val iconPx = with(LocalDensity.current) { 40.dp.roundToPx() }
    val clearSearchLabel = stringResource(R.string.clear_search)

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.apps_to_block), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.apps_to_block_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (selected.isNotEmpty()) {
                        Text(
                            pluralStringResource(R.plurals.apps_selected, selected.size, selected.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search_apps)) },
                    leadingIcon = {
                        SearchGlyph(MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(20.dp))
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable { query = "" }
                                    .semantics { contentDescription = clearSearchLabel },
                                contentAlignment = Alignment.Center
                            ) {
                                ClearGlyph(MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(15.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                when {
                    apps == null -> Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    }
                    filtered.isEmpty() -> Text(
                        if (query.isBlank()) stringResource(R.string.no_apps_found) else stringResource(R.string.no_apps_match, query),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        grouped.forEach { (letter, entries) ->
                            stickyHeader(key = "header_$letter") {
                                Text(
                                    letter.toString(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(start = 6.dp, top = 8.dp, bottom = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            items(entries, key = { it.packageName }) { app ->
                                val checked = app.packageName in selected
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(MaterialTheme.shapes.small)
                                        .background(
                                            if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            else Color.Transparent
                                        )
                                        .toggleable(
                                            value = checked,
                                            role = Role.Checkbox,
                                            onValueChange = { onToggle(app.packageName, it) }
                                        )
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppIcon(
                                        app.packageName, iconCache, iconPx,
                                        Modifier.size(40.dp).clip(MaterialTheme.shapes.small)
                                    )
                                    Text(
                                        app.label,
                                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Checkbox(checked = checked, onCheckedChange = null)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onClearAll, enabled = selected.isNotEmpty()) { Text(stringResource(R.string.clear_all)) }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = onDismiss) { Text(stringResource(R.string.done)) }
                }
            }
        }
    }
}

/**
 * App launcher icon, decoded off the main thread and kept in a bounded LRU so scrolling doesn't
 * reload it. The decode is gated by [iconLoadSemaphore] so a fast fling can't flood the IO pool,
 * and the cache is re-read for the current package each run so a recycled row never shows a stale
 * icon. A neutral tile stands in while loading or if the icon can't be resolved.
 */
// Known lint false positive when produceState's value is assigned from a withContext { } result
// (issuetracker.google.com/265036856) — same suppression as rememberLaunchableApps.
@SuppressLint("ProduceStateDoesNotAssignValue")
@Composable
private fun AppIcon(
    packageName: String,
    cache: LruCache<String, ImageBitmap>,
    sizePx: Int,
    modifier: Modifier
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = cache.get(packageName), packageName) {
        // Always assign value (cached, freshly decoded, or null on failure) so the icon matches
        // the current package even on a recycled row.
        value = cache.get(packageName) ?: withContext(Dispatchers.IO) {
            iconLoadSemaphore.withPermit {
                ensureActive() // bail if the row was flung past before a permit freed up
                runCatching {
                    context.packageManager.getApplicationIcon(packageName)
                        .toBitmap(sizePx, sizePx).asImageBitmap()
                }.getOrNull()
            }
        }?.also { cache.put(packageName, it) }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(bitmap = bmp, contentDescription = null, modifier = modifier)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

/** A hand-drawn magnifier (matches the Canvas style of [Chevron]); avoids an icon dependency. */
@Composable
private fun SearchGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.10f
        val r = size.minDimension * 0.30f
        val center = Offset(size.width * 0.42f, size.height * 0.42f)
        drawCircle(color = tint, radius = r, center = center, style = Stroke(width = stroke))
        val k = r * 0.72f
        drawLine(
            color = tint,
            start = Offset(center.x + k, center.y + k),
            end = Offset(size.width * 0.86f, size.height * 0.86f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

/** A hand-drawn ✕ for the search-clear button. */
@Composable
private fun ClearGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.16f
        val i = size.minDimension * 0.2f
        drawLine(tint, Offset(i, i), Offset(size.width - i, size.height - i), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(size.width - i, i), Offset(i, size.height - i), strokeWidth = stroke, cap = StrokeCap.Round)
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
