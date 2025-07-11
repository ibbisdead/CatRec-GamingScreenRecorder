package com.ibbie.catrec_gamingscreenrecorder.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import com.ibbie.catrec_gamingscreenrecorder.SettingsDataStore
import android.util.Log
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.Manifest
import android.widget.Toast
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.ibbie.catrec_gamingscreenrecorder.AnalyticsManager
import com.ibbie.catrec_gamingscreenrecorder.UpdateManager
import android.app.Activity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController,
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onStartRecording: (Boolean, Boolean, String) -> Unit
) {
    val context = LocalContext.current
    val settingsDataStore = remember { SettingsDataStore(context) }
    val scope = rememberCoroutineScope()
    val isRecording = remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val analyticsManager = remember { AnalyticsManager(context) }
    
    // Check service status periodically
    LaunchedEffect(Unit) {
        while (true) {
            val serviceRunning = isServiceRunning(context)
            isRecording.value = serviceRunning
            kotlinx.coroutines.delay(1000)
        }
    }
    
    // Settings state
    val countdown by settingsDataStore.countdown.collectAsState(initial = 0)
    val recordingOverlay by settingsDataStore.recordingOverlay.collectAsState(initial = true)
    val stopOptions by settingsDataStore.stopOptions.collectAsState(initial = setOf("Screen Off"))
    val pauseOnScreenOff by settingsDataStore.pauseOnScreenOff.collectAsState(initial = false)
    val orientation by settingsDataStore.orientation.collectAsState(initial = "Auto")
    val resolution by settingsDataStore.resolution.collectAsState(initial = "Native")
    val videoBitrate by settingsDataStore.videoBitrate.collectAsState(initial = 8000000)
    val fps by settingsDataStore.fps.collectAsState(initial = 60)
    val audioBitrate by settingsDataStore.audioBitrate.collectAsState(initial = "128 kbps")
    val audioSampleRate by settingsDataStore.audioSampleRate.collectAsState(initial = "44100 Hz")
    val audioChannel by settingsDataStore.audioChannel.collectAsState(initial = "Stereo")
    val audioSource by settingsDataStore.audioSource.collectAsState(initial = "System Audio")
    val showTouches by settingsDataStore.showTouches.collectAsState(initial = false)
    val fileDestination by settingsDataStore.fileDestination.collectAsState(initial = "/storage/emulated/0/Movies/CatRec")
    val overlayEnabled by settingsDataStore.overlayEnabled.collectAsState(initial = false)
    val overlayButton by settingsDataStore.overlayButton.collectAsState(initial = true)
    val micVolume by settingsDataStore.micVolume.collectAsState(initial = 100)
    val noiseSuppression by settingsDataStore.noiseSuppression.collectAsState(initial = false)
    val autoStopMinutes by settingsDataStore.autoStopMinutes.collectAsState(initial = 0)
    val pauseEnabled by settingsDataStore.pauseEnabled.collectAsState(initial = false)
    val micMuteEnabled by settingsDataStore.micMuteEnabled.collectAsState(initial = false)
    val cloudBackupEnabled by settingsDataStore.cloudBackupEnabled.collectAsState(initial = false)
    val cloudBackupProvider by settingsDataStore.cloudBackupProvider.collectAsState(initial = "Google Drive")
    val autoTrimEnabled by settingsDataStore.autoTrimEnabled.collectAsState(initial = false)
    val autoTrimStartSeconds by settingsDataStore.autoTrimStartSeconds.collectAsState(initial = 0)
    val autoTrimEndSeconds by settingsDataStore.autoTrimEndSeconds.collectAsState(initial = 0)
    val gestureControlsEnabled by settingsDataStore.gestureControlsEnabled.collectAsState(initial = false)
    val scheduledRecordingEnabled by settingsDataStore.scheduledRecordingEnabled.collectAsState(initial = false)
    val scheduledRecordingTime by settingsDataStore.scheduledRecordingTime.collectAsState(initial = 0L)
    val scheduledRecurrence by settingsDataStore.scheduledRecurrence.collectAsState(initial = "None")
    val scheduledCustomDays by settingsDataStore.scheduledCustomDays.collectAsState(initial = setOf())
    val autoHighlightDetection by settingsDataStore.autoHighlightDetection.collectAsState(initial = false)
    val autoHighlightClipExtraction by settingsDataStore.autoHighlightClipExtraction.collectAsState(initial = false)
    val autoHighlightReelGeneration by settingsDataStore.autoHighlightReelGeneration.collectAsState(initial = false)
    val highlightClipLength by settingsDataStore.highlightClipLength.collectAsState(initial = 15)
    val autoDeleteEnabled by settingsDataStore.autoDeleteEnabled.collectAsState(initial = false)
    val retentionDays by settingsDataStore.retentionDays.collectAsState(initial = 30)
    val analyticsEnabled by settingsDataStore.analyticsEnabled.collectAsState(initial = false)
    val autoUpdateCheckEnabled by settingsDataStore.autoUpdateCheckEnabled.collectAsState(initial = true)
    val crashReportingEnabled by settingsDataStore.crashReportingEnabled.collectAsState(initial = false)
    
    // Dialog states
    var showCountdownDialog by remember { mutableStateOf(false) }
    var showStopOptionsDialog by remember { mutableStateOf(false) }
    var showOrientationDialog by remember { mutableStateOf(false) }
    var showResolutionDialog by remember { mutableStateOf(false) }
    var showBitrateDialog by remember { mutableStateOf(false) }
    var showFpsDialog by remember { mutableStateOf(false) }
    var showAudioBitrateDialog by remember { mutableStateOf(false) }
    var showAudioSampleRateDialog by remember { mutableStateOf(false) }
    var showAudioChannelDialog by remember { mutableStateOf(false) }
    var showAudioSourceDialog by remember { mutableStateOf(false) }
    var showDestinationDialog by remember { mutableStateOf(false) }
    var showMicTestDialog by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (darkTheme) Color.Black else Color(0xFFF5F5F5))
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header with theme toggle
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (darkTheme) Color.White else Color.Black
                    )
                    
                    IconButton(
                        onClick = { 
                            onThemeChange(!darkTheme)
                            scope.launch { analyticsManager.logThemeChanged(!darkTheme) }
                        }
                    ) {
                        Icon(
                            imageVector = if (darkTheme) Icons.Default.WbSunny else Icons.Default.NightsStay,
                            contentDescription = if (darkTheme) "Light Mode" else "Dark Mode",
                            tint = Color(0xFFD32F2F)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
            
            // Control Settings Category
            item {
                SettingsCategory(
                    title = "Control Settings",
                    darkTheme = darkTheme
                ) {
                    // Recording Countdown
                    var expanded by remember { mutableStateOf(false) }
                    val countdownOptions = listOf(0, 1, 3, 5)
                    SettingsRow(
                        title = "Recording Countdown",
                        value = when (countdown) {
                            0 -> "Off"
                            1 -> "1 second"
                            else -> "$countdown seconds"
                        },
                        onClick = { expanded = true },
                        darkTheme = darkTheme
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        countdownOptions.forEach { value ->
                            DropdownMenuItem(
                                text = { Text(if (value == 0) "Off" else "$value second${if (value > 1) "s" else ""}") },
                                onClick = {
                                    expanded = false
                                    scope.launch { settingsDataStore.setCountdown(value) }
                                }
                            )
                        }
                    }
                    
                    // Overlay
                    SettingsRow(
                        title = "Overlay",
                        value = if (overlayEnabled) "On" else "Off",
                        onClick = {
                            scope.launch { settingsDataStore.setOverlayEnabled(!overlayEnabled) }
                        },
                        darkTheme = darkTheme
                    )
                    
                    // Stop Options
                    SettingsRow(
                        title = "Stop Options",
                        value = if (stopOptions.size > 1) "Multiple" else stopOptions.firstOrNull() ?: "None",
                        onClick = { showStopOptionsDialog = true },
                        darkTheme = darkTheme
                    )
                    
                    // Auto-stop Timer
                    var autoStopExpanded by remember { mutableStateOf(false) }
                    val autoStopOptions = listOf(0, 5, 10, 30, 60)
                    SettingsRow(
                        title = "Auto-stop Timer",
                        value = when (autoStopMinutes) {
                            0 -> "Off"
                            else -> "$autoStopMinutes min"
                        },
                        onClick = { autoStopExpanded = true },
                        darkTheme = darkTheme
                    )
                    DropdownMenu(
                        expanded = autoStopExpanded,
                        onDismissRequest = { autoStopExpanded = false }
                    ) {
                        autoStopOptions.forEach { value ->
                            DropdownMenuItem(
                                text = { Text(if (value == 0) "Off" else "$value min") },
                                onClick = {
                                    autoStopExpanded = false
                                    scope.launch { settingsDataStore.setAutoStopMinutes(value) }
                                }
                            )
                        }
                    }
                    
                    // Screen off pauses recording
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Screen off pauses recording",
                            modifier = Modifier.weight(1f),
                            color = if (darkTheme) Color.White else Color.Black
                        )
                        Switch(
                            checked = pauseOnScreenOff,
                            onCheckedChange = { 
                                scope.launch { 
                                    settingsDataStore.setPauseOnScreenOff(it)
                                    // If enabling pause, disable stop on screen off
                                    if (it) {
                                        val newStopOptions = stopOptions - "Screen Off"
                                        settingsDataStore.setStopOptions(newStopOptions)
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFD32F2F),
                                checkedTrackColor = Color(0xFFD32F2F).copy(alpha = 0.5f)
                            )
                        )
                    }
                    
                    // Pause functionality toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Enable Pause During Recording",
                            modifier = Modifier.weight(1f),
                            color = if (darkTheme) Color.White else Color.Black
                        )
                        Switch(
                            checked = pauseEnabled,
                            onCheckedChange = { scope.launch { settingsDataStore.setPauseEnabled(it) } },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFD32F2F),
                                checkedTrackColor = Color(0xFFD32F2F).copy(alpha = 0.5f)
                            )
                        )
                    }
                    
                    // Mic mute toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Enable Mic Mute During Recording",
                            modifier = Modifier.weight(1f),
                            color = if (darkTheme) Color.White else Color.Black
                        )
                        Switch(
                            checked = micMuteEnabled,
                            onCheckedChange = { scope.launch { settingsDataStore.setMicMuteEnabled(it) } },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFD32F2F),
                                checkedTrackColor = Color(0xFFD32F2F).copy(alpha = 0.5f)
                            )
                        )
                    }
                    
                    // Gesture controls toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Enable Gesture Controls",
                            modifier = Modifier.weight(1f),
                            color = if (darkTheme) Color.White else Color.Black
                        )
                        Switch(
                            checked = gestureControlsEnabled,
                            onCheckedChange = { scope.launch { settingsDataStore.setGestureControlsEnabled(it) } },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFD32F2F),
                                checkedTrackColor = Color(0xFFD32F2F).copy(alpha = 0.5f)
                            )
                        )
                    }
                    
                    // Cloud backup toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Enable Cloud Backup",
                            modifier = Modifier.weight(1f),
                            color = if (darkTheme) Color.White else Color.Black
                        )
                        Switch(
                            checked = cloudBackupEnabled,
                            onCheckedChange = { scope.launch { settingsDataStore.setCloudBackupEnabled(it) } },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFD32F2F),
                                checkedTrackColor = Color(0xFFD32F2F).copy(alpha = 0.5f)
                            )
                        )
                    }
                    // Cloud backup provider dropdown
                    if (cloudBackupEnabled) {
                        var providerExpanded by remember { mutableStateOf(false) }
                        val providers = listOf("Google Drive", "Dropbox")
                        SettingsRow(
                            title = "Backup Provider",
                            value = cloudBackupProvider,
                            onClick = { providerExpanded = true },
                            darkTheme = darkTheme
                        )
                        DropdownMenu(
                            expanded = providerExpanded,
                            onDismissRequest = { providerExpanded = false }
                        ) {
                            providers.forEach { provider ->
                                DropdownMenuItem(
                                    text = { Text(provider) },
                                    onClick = {
                                        providerExpanded = false
                                        scope.launch { settingsDataStore.setCloudBackupProvider(provider) }
                                    }
                                )
                            }
                        }
                    }
                    
                    // Auto Highlight Detection toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Auto Highlight Detection",
                            modifier = Modifier.weight(1f),
                            color = if (darkTheme) Color.White else Color.Black
                        )
                        Switch(
                            checked = autoHighlightDetection,
                            onCheckedChange = { scope.launch { settingsDataStore.setAutoHighlightDetection(it) } },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFD32F2F),
                                checkedTrackColor = Color(0xFFD32F2F).copy(alpha = 0.5f)
                            )
                        )
                    }
                    
                    // Auto Highlight Clip Extraction toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Auto Highlight Clip Extraction",
                            modifier = Modifier.weight(1f),
                            color = if (darkTheme) Color.White else Color.Black
                        )
                        Switch(
                            checked = autoHighlightClipExtraction,
                            onCheckedChange = { scope.launch { settingsDataStore.setAutoHighlightClipExtraction(it) } },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFD32F2F),
                                checkedTrackColor = Color(0xFFD32F2F).copy(alpha = 0.5f)
                            )
                        )
                    }
                    
                    // Auto Highlight Reel Generation toggle (only show if clip extraction is enabled)
                    if (autoHighlightClipExtraction) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Auto Highlight Reel Generation",
                                modifier = Modifier.weight(1f),
                                color = if (darkTheme) Color.White else Color.Black
                            )
                            Switch(
                                checked = autoHighlightReelGeneration,
                                onCheckedChange = { scope.launch { settingsDataStore.setAutoHighlightReelGeneration(it) } },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFFD32F2F),
                                    checkedTrackColor = Color(0xFFD32F2F).copy(alpha = 0.5f)
                                )
                            )
                        }
                    }
                    
                    // Highlight Clip Length (only show if clip extraction is enabled)
                    if (autoHighlightClipExtraction) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Clip Length",
                                color = if (darkTheme) Color.White else Color.Black,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            var sliderClipLength by remember { mutableStateOf(highlightClipLength.toFloat()) }
                            var lastHapticClipLength by remember { mutableStateOf(sliderClipLength) }
                            Slider(
                                value = sliderClipLength,
                                onValueChange = {
                                    sliderClipLength = it
                                    if (kotlin.math.abs(it - lastHapticClipLength) >= 1f) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        lastHapticClipLength = it
                                    }
                                    scope.launch { settingsDataStore.setHighlightClipLength(it.toInt()) }
                                },
                                valueRange = 5f..60f,
                                steps = 11, // 5, 10, 15, ..., 60 (5 second steps)
                                colors = SliderDefaults.colors(
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    thumbColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.weight(1f),
                                thumb = {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "${sliderClipLength.toInt()}s",
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        SliderDefaults.Thumb(
                                            interactionSource = remember { MutableInteractionSource() },
                                            enabled = true
                                        )
                                    }
                                }
                            )
                            Text(
                                text = "${sliderClipLength.toInt()}s",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }

                    // Auto Delete toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Enable Auto Delete",
                            modifier = Modifier.weight(1f),
                            color = if (darkTheme) Color.White else Color.Black
                        )
                        Switch(
                            checked = autoDeleteEnabled,
                            onCheckedChange = { 
                                scope.launch { 
                                    settingsDataStore.setAutoDeleteEnabled(it)
                                    if (it) {
                                        analyticsManager.logAutoDeleteEnabled()
                                    } else {
                                        analyticsManager.logAutoDeleteDisabled()
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFD32F2F),
                                checkedTrackColor = Color(0xFFD32F2F).copy(alpha = 0.5f)
                            )
                        )
                    }
                    // Retention duration dropdown (only show if auto delete is enabled)
                    if (autoDeleteEnabled) {
                        var retentionExpanded by remember { mutableStateOf(false) }
                        val retentionOptions = listOf(7, 14, 30, 60)
                        SettingsRow(
                            title = "Retention Duration",
                            value = "$retentionDays days",
                            onClick = { retentionExpanded = true },
                            darkTheme = darkTheme
                        )
                        DropdownMenu(
                            expanded = retentionExpanded,
                            onDismissRequest = { retentionExpanded = false }
                        ) {
                            retentionOptions.forEach { value ->
                                DropdownMenuItem(
                                    text = { Text("$value days") },
                                    onClick = {
                                        retentionExpanded = false
                                        scope.launch { settingsDataStore.setRetentionDays(value) }
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            // Video Settings Category
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsCategory(
                    title = "Video Settings",
                    darkTheme = darkTheme
                ) {
                    // Resolution
                    var resolutionExpanded by remember { mutableStateOf(false) }
                    val resolutionOptions = listOf(
                        "Native" to "Device Native",
                        "1280x720" to "720p (1280x720)",
                        "1920x1080" to "1080p (1920x1080)"
                    )
                    SettingsRow(
                        title = "Resolution",
                        value = resolutionOptions.find { it.first == resolution }?.second ?: "Device Native",
                        onClick = { resolutionExpanded = true },
                        darkTheme = darkTheme
                    )
                    DropdownMenu(
                        expanded = resolutionExpanded,
                        onDismissRequest = { resolutionExpanded = false }
                    ) {
                        resolutionOptions.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    resolutionExpanded = false
                                    scope.launch { settingsDataStore.setResolution(value) }
                                }
                            )
                        }
                    }
                    
                    // Bitrate
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Bitrate",
                            color = if (darkTheme) Color.White else Color.Black,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        var sliderBitrate by remember { mutableStateOf(videoBitrate.toFloat()) }
                        var lastHapticBitrate by remember { mutableStateOf(sliderBitrate) }
                        Slider(
                            value = sliderBitrate,
                            onValueChange = {
                                sliderBitrate = it
                                if (kotlin.math.abs(it - lastHapticBitrate) >= 5_000_000f) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    lastHapticBitrate = it
                                }
                                scope.launch { settingsDataStore.setVideoBitrate(it.toInt()) }
                            },
                            valueRange = 5_000_000f..100_000_000f,
                            steps = 19, // 5, 10, ..., 100 Mbps (5M steps)
                            colors = SliderDefaults.colors(
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                thumbColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.weight(1f),
                            thumb = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${(sliderBitrate / 1_000_000).toInt()} Mbps",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    SliderDefaults.Thumb(
                                        interactionSource = remember { MutableInteractionSource() },
                                        enabled = true
                                    )
                                }
                            }
                        )
                        Text(
                            text = "${(sliderBitrate / 1_000_000).toInt()} Mbps",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    
                    // Framerate
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Framerate",
                            color = if (darkTheme) Color.White else Color.Black,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        var sliderFps by remember { mutableStateOf(fps.toFloat()) }
                        var lastHapticFps by remember { mutableStateOf(sliderFps) }
                        Slider(
                            value = sliderFps,
                            onValueChange = {
                                sliderFps = it
                                if (kotlin.math.abs(it - lastHapticFps) >= 1f) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    lastHapticFps = it
                                }
                                scope.launch { settingsDataStore.setFps(it.toInt()) }
                            },
                            valueRange = 15f..120f,
                            steps = 21, // 15, 20, ..., 120 (5 step)
                            colors = SliderDefaults.colors(
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                thumbColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.weight(1f),
                            thumb = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${sliderFps.toInt()} FPS",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    SliderDefaults.Thumb(
                                        interactionSource = remember { MutableInteractionSource() },
                                        enabled = true
                                    )
                                }
                            }
                        )
                        Text(
                            text = "${sliderFps.toInt()} FPS",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    
                    // Audio Bitrate
                    SettingsRow(
                        title = "Audio Bitrate",
                        value = audioBitrate,
                        onClick = { showAudioBitrateDialog = true },
                        darkTheme = darkTheme
                    )
                    
                    // Audio Sample Rate
                    SettingsRow(
                        title = "Audio Sample Rate",
                        value = audioSampleRate,
                        onClick = { showAudioSampleRateDialog = true },
                        darkTheme = darkTheme
                    )
                    
                    // Audio Channel
                    SettingsRow(
                        title = "Audio Channel",
                        value = audioChannel,
                        onClick = { showAudioChannelDialog = true },
                        darkTheme = darkTheme
                    )
                    
                    // Audio Source
                    SettingsRow(
                        title = "Audio Source",
                        value = audioSource,
                        onClick = { showAudioSourceDialog = true },
                        darkTheme = darkTheme
                    )
                    
                    // Microphone Volume Slider
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = "Microphone Volume",
                            color = if (darkTheme) Color.White else Color.Black,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = micVolume.toFloat(),
                            onValueChange = { 
                                scope.launch { settingsDataStore.setMicVolume(it.toInt()) }
                            },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFD32F2F),
                                activeTrackColor = Color(0xFFD32F2F),
                                inactiveTrackColor = if (darkTheme) Color.DarkGray else Color.LightGray
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "0%",
                                color = if (darkTheme) Color.Gray else Color.DarkGray,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "100%",
                                color = if (darkTheme) Color.Gray else Color.DarkGray,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    
                    // Noise Suppression
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Noise Suppression",
                            modifier = Modifier.weight(1f),
                            color = if (darkTheme) Color.White else Color.Black
                        )
                        Switch(
                            checked = noiseSuppression,
                            onCheckedChange = { 
                                scope.launch { settingsDataStore.setNoiseSuppression(it) }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFD32F2F),
                                checkedTrackColor = Color(0xFFD32F2F).copy(alpha = 0.5f)
                            )
                        )
                    }
                    
                    // Microphone Test Button
                    Button(
                        onClick = { showMicTestDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "Test Microphone",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Test Microphone",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    // Orientation
                    SettingsRow(
                        title = "Orientation",
                        value = orientation,
                        onClick = { showOrientationDialog = true },
                        darkTheme = darkTheme
                    )
                    
                    // Show Touches
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Show Touches",
                            modifier = Modifier.weight(1f),
                            color = if (darkTheme) Color.White else Color.Black
                        )
                        Switch(
                            checked = showTouches,
                            onCheckedChange = { 
                                scope.launch { settingsDataStore.setShowTouches(it) }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFD32F2F),
                                checkedTrackColor = Color(0xFFD32F2F).copy(alpha = 0.5f)
                            )
                        )
                    }
                    
                    // Recording Destination
                    SettingsRow(
                        title = "Recording Destination",
                        value = if (fileDestination.contains("Movies")) "Movies/CatRec" else "App Specific",
                        onClick = { showDestinationDialog = true },
                        darkTheme = darkTheme
                    )

                    // Auto-trim toggle and sliders
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Auto-trim Recording",
                            modifier = Modifier.weight(1f),
                            color = if (darkTheme) Color.White else Color.Black
                        )
                        Switch(
                            checked = autoTrimEnabled,
                            onCheckedChange = { scope.launch { settingsDataStore.setAutoTrimEnabled(it) } },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFD32F2F),
                                checkedTrackColor = Color(0xFFD32F2F).copy(alpha = 0.5f)
                            )
                        )
                    }
                    if (autoTrimEnabled) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Text("Trim from Start: $autoTrimStartSeconds s", color = if (darkTheme) Color.White else Color.Black)
                            Slider(
                                value = autoTrimStartSeconds.toFloat(),
                                onValueChange = { scope.launch { settingsDataStore.setAutoTrimStartSeconds(it.toInt()) } },
                                valueRange = 0f..30f,
                                steps = 29
                            )
                            Text("Trim from End: $autoTrimEndSeconds s", color = if (darkTheme) Color.White else Color.Black)
                            Slider(
                                value = autoTrimEndSeconds.toFloat(),
                                onValueChange = { scope.launch { settingsDataStore.setAutoTrimEndSeconds(it.toInt()) } },
                                valueRange = 0f..30f,
                                steps = 29
                            )
                        }
                    }
                }
            }
            
            // Scheduled Recording
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsCategory(
                    title = "Scheduled Recording",
                    darkTheme = darkTheme
                ) {
                    // Scheduled Recording
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Enable Scheduled Recording",
                            modifier = Modifier.weight(1f),
                            color = if (darkTheme) Color.White else Color.Black
                        )
                        Switch(
                            checked = scheduledRecordingEnabled,
                            onCheckedChange = { 
                                scope.launch { 
                                    settingsDataStore.setScheduledRecordingEnabled(it)
                                    if (it) {
                                        analyticsManager.logSettingChanged("scheduled_recording", "enabled")
                                    } else {
                                        analyticsManager.logScheduledRecordingCancelled()
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFD32F2F),
                                checkedTrackColor = Color(0xFFD32F2F).copy(alpha = 0.5f)
                            )
                        )
                    }
                    if (scheduledRecordingEnabled) {
                        var showDatePicker by remember { mutableStateOf(false) }
                        val scheduledDate = remember(scheduledRecordingTime) {
                            if (scheduledRecordingTime > 0L) java.util.Date(scheduledRecordingTime) else java.util.Date()
                        }
                        SettingsRow(
                            title = "Scheduled Start Time",
                            value = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(scheduledDate),
                            onClick = { showDatePicker = true },
                            darkTheme = darkTheme
                        )
                        if (showDatePicker) {
                            DateTimePickerDialog(
                                initialTime = scheduledRecordingTime,
                                onDismiss = { showDatePicker = false },
                                onTimeSelected = { time ->
                                    scope.launch { 
                                        settingsDataStore.setScheduledRecordingTime(time)
                                        settingsDataStore.scheduleInitialRecording()
                                    }
                                    showDatePicker = false
                                }
                            )
                        }
                        
                        // Recurrence selector
                        var showRecurrenceDialog by remember { mutableStateOf(false) }
                        SettingsRow(
                            title = "Recurrence",
                            value = scheduledRecurrence,
                            onClick = { showRecurrenceDialog = true },
                            darkTheme = darkTheme
                        )
                        if (showRecurrenceDialog) {
                            DropdownDialog(
                                title = "Recurrence",
                                value = when (scheduledRecurrence) {
                                    "None" -> 0
                                    "Daily" -> 1
                                    "Weekly" -> 2
                                    "Custom" -> 3
                                    else -> 0
                                },
                                onValueChange = { v ->
                                    val newRecurrence = when (v) {
                                        0 -> "None"
                                        1 -> "Daily"
                                        2 -> "Weekly"
                                        3 -> "Custom"
                                        else -> "None"
                                    }
                                    scope.launch { settingsDataStore.setScheduledRecurrence(newRecurrence) }
                                },
                                onDismiss = { showRecurrenceDialog = false },
                                valueRange = 0..3,
                                valueLabels = listOf("None", "Daily", "Weekly", "Custom")
                            )
                        }
                        
                        // Custom days selector (only show if Custom is selected)
                        if (scheduledRecurrence == "Custom") {
                            var showCustomDaysDialog by remember { mutableStateOf(false) }
                            val customDaysText = if (scheduledCustomDays.isEmpty()) "No days selected" else scheduledCustomDays.joinToString(", ")
                            SettingsRow(
                                title = "Custom Days",
                                value = customDaysText,
                                onClick = { showCustomDaysDialog = true },
                                darkTheme = darkTheme
                            )
                            if (showCustomDaysDialog) {
                                MultiSelectDialog(
                                    title = "Select Days",
                                    options = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"),
                                    selectedOptions = scheduledCustomDays,
                                    onSelectionChange = { days ->
                                        scope.launch { settingsDataStore.setScheduledCustomDays(days) }
                                    },
                                    onDismiss = { showCustomDaysDialog = false }
                                )
                            }
                        }
                    }
                }
            }

            // Scheduled Recording Management
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { navController.navigate("scheduling") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Manage Scheduled Recordings", color = Color.White)
                }
            }

            // Analytics
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Allow Usage Analytics",
                            color = if (darkTheme) Color.White else Color.Black
                        )
                        Text(
                            text = "Track app usage for performance insights",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (darkTheme) Color.Gray else Color.DarkGray
                        )
                    }
                    Switch(
                        checked = analyticsEnabled,
                        onCheckedChange = { 
                            scope.launch { 
                                settingsDataStore.setAnalyticsEnabled(it)
                                analyticsManager.logSettingChanged("analytics_enabled", if (it) "true" else "false")
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFD32F2F),
                            checkedTrackColor = Color(0xFFD32F2F).copy(alpha = 0.5f)
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Auto Check for Updates
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto Check for Updates",
                            color = if (darkTheme) Color.White else Color.Black
                        )
                        Text(
                            text = "Automatically check for app updates on launch",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (darkTheme) Color.Gray else Color.DarkGray
                        )
                    }
                    Switch(
                        checked = autoUpdateCheckEnabled,
                        onCheckedChange = { 
                            scope.launch { 
                                settingsDataStore.setAutoUpdateCheckEnabled(it)
                                analyticsManager.logSettingChanged("auto_update_check_enabled", if (it) "true" else "false")
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFD32F2F),
                            checkedTrackColor = Color(0xFFD32F2F).copy(alpha = 0.5f)
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Crash Reporting
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Crash Reporting",
                            color = if (darkTheme) Color.White else Color.Black
                        )
                        Text(
                            text = "Send crash reports to help improve the app",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (darkTheme) Color.Gray else Color.DarkGray
                        )
                    }
                    Switch(
                        checked = crashReportingEnabled,
                        onCheckedChange = { 
                            scope.launch { 
                                settingsDataStore.setCrashReportingEnabled(it)
                                analyticsManager.logSettingChanged("crash_reporting_enabled", if (it) "true" else "false")
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFD32F2F),
                            checkedTrackColor = Color(0xFFD32F2F).copy(alpha = 0.5f)
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Check for Updates
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Check for Updates",
                            color = if (darkTheme) Color.White else Color.Black
                        )
                        Text(
                            text = "Check for app updates from Google Play",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (darkTheme) Color.Gray else Color.DarkGray
                        )
                    }
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch {
                                try {
                                    val updateManager = UpdateManager(context)
                                    updateManager.checkForUpdates(context as Activity, forceCheck = true)
                                    analyticsManager.logEvent("manual_update_check")
                                } catch (e: Exception) {
                                    Log.e("SettingsScreen", "Error checking for updates", e)
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Check for Updates",
                            tint = Color(0xFFD32F2F)
                        )
                    }
                }
            }

        }
    }
    
    // Dialogs
    if (showCountdownDialog) {
        DropdownDialog(
            title = "Recording Countdown",
            value = countdown,
            onValueChange = { v ->
                scope.launch { settingsDataStore.setCountdown(v) }
            },
            onDismiss = { showCountdownDialog = false },
            valueRange = 0..10
        )
    }
    
    if (showStopOptionsDialog) {
        MultiSelectDialog(
            title = "Stop Options",
            options = listOf("Screen Off", "Notification Bar", "Shake Device"),
            selectedOptions = stopOptions,
            onSelectionChange = { options ->
                scope.launch { 
                    settingsDataStore.setStopOptions(options)
                    // If "Screen Off" is selected, disable pause on screen off
                    if (options.contains("Screen Off")) {
                        settingsDataStore.setPauseOnScreenOff(false)
                    }
                }
            },
            onDismiss = { showStopOptionsDialog = false }
        )
    }
    
    if (showOrientationDialog) {
        DropdownDialog(
            title = "Orientation",
            value = when (orientation) {
                "Vertical" -> 0
                "Horizontal" -> 1
                else -> 2
            },
            onValueChange = { v ->
                val newOrientation = when (v) {
                    0 -> "Vertical"
                    1 -> "Horizontal"
                    else -> "Auto"
                }
                scope.launch { settingsDataStore.setOrientation(newOrientation) }
            },
            onDismiss = { showOrientationDialog = false },
            valueRange = 0..2,
            valueLabels = listOf("Vertical", "Horizontal", "Auto")
        )
    }
    
    if (showResolutionDialog) {
        DropdownDialog(
            title = "Resolution",
            value = when (resolution) {
                "480p" -> 0
                "720p" -> 1
                "1080p" -> 2
                "1440p" -> 3
                else -> 4
            },
            onValueChange = { v ->
                val newResolution = when (v) {
                    0 -> "480p"
                    1 -> "720p"
                    2 -> "1080p"
                    3 -> "1440p"
                    else -> "Native"
                }
                scope.launch { settingsDataStore.setResolution(newResolution) }
            },
            onDismiss = { showResolutionDialog = false },
            valueRange = 0..4,
            valueLabels = listOf("480p", "720p", "1080p", "1440p", "Native")
        )
    }
    
    if (showBitrateDialog) {
        SliderDialog(
            title = "Video Bitrate",
            value = videoBitrate / 1000000f,
            onValueChange = { v ->
                scope.launch { settingsDataStore.setVideoBitrate((v * 1000000).toInt()) }
            },
            onDismiss = { showBitrateDialog = false },
            valueRange = 1f..100f,
            steps = 99,
            valueText = "${(videoBitrate / 1000000f).toInt()} Mbps"
        )
    }
    
    if (showFpsDialog) {
        DropdownDialog(
            title = "FPS",
            value = when (fps) {
                24 -> 0
                30 -> 1
                48 -> 2
                60 -> 3
                90 -> 4
                else -> 5
            },
            onValueChange = { v ->
                val newFps = when (v) {
                    0 -> 24
                    1 -> 30
                    2 -> 48
                    3 -> 60
                    4 -> 90
                    else -> 120
                }
                scope.launch { settingsDataStore.setFps(newFps) }
            },
            onDismiss = { showFpsDialog = false },
            valueRange = 0..5,
            valueLabels = listOf("24", "30", "48", "60", "90", "120")
        )
    }
    
    if (showAudioBitrateDialog) {
        DropdownDialog(
            title = "Audio Bitrate",
            value = when (audioBitrate) {
                "64 kbps" -> 0
                "128 kbps" -> 1
                "256 kbps" -> 2
                else -> 3
            },
            onValueChange = { v ->
                val newBitrate = when (v) {
                    0 -> "64 kbps"
                    1 -> "128 kbps"
                    2 -> "256 kbps"
                    else -> "320 kbps"
                }
                scope.launch { settingsDataStore.setAudioBitrate(newBitrate) }
            },
            onDismiss = { showAudioBitrateDialog = false },
            valueRange = 0..3,
            valueLabels = listOf("64 kbps", "128 kbps", "256 kbps", "320 kbps")
        )
    }
    
    if (showAudioSampleRateDialog) {
        DropdownDialog(
            title = "Audio Sample Rate",
            value = if (audioSampleRate == "44100 Hz") 0 else 1,
            onValueChange = { v ->
                val newSampleRate = if (v == 0) "44100 Hz" else "48000 Hz"
                scope.launch { settingsDataStore.setAudioSampleRate(newSampleRate) }
            },
            onDismiss = { showAudioSampleRateDialog = false },
            valueRange = 0..1,
            valueLabels = listOf("44100 Hz", "48000 Hz")
        )
    }
    
    if (showAudioChannelDialog) {
        DropdownDialog(
            title = "Audio Channel",
            value = if (audioChannel == "Mono") 0 else 1,
            onValueChange = { v ->
                val newChannel = if (v == 0) "Mono" else "Stereo"
                scope.launch { settingsDataStore.setAudioChannel(newChannel) }
            },
            onDismiss = { showAudioChannelDialog = false },
            valueRange = 0..1,
            valueLabels = listOf("Mono", "Stereo")
        )
    }
    
    if (showAudioSourceDialog) {
        DropdownDialog(
            title = "Audio Source",
            value = when (audioSource) {
                "Mic" -> 0
                "System Audio" -> 1
                else -> 2
            },
            onValueChange = { v ->
                val newSource = when (v) {
                    0 -> "Mic"
                    1 -> "System Audio"
                    else -> "System Audio + Mic"
                }
                scope.launch { settingsDataStore.setAudioSource(newSource) }
            },
            onDismiss = { showAudioSourceDialog = false },
            valueRange = 0..2,
            valueLabels = listOf("Mic", "System Audio", "System Audio + Mic")
        )
    }
    
    if (showDestinationDialog) {
        DropdownDialog(
            title = "Recording Destination",
            value = if (fileDestination == "/storage/emulated/0/Movies/CatRec") 0 else 1,
            onValueChange = { v ->
                val newDestination = if (v == 0) "/storage/emulated/0/Movies/CatRec" else "app_specific"
                scope.launch { settingsDataStore.setFileDestination(newDestination) }
            },
            onDismiss = { showDestinationDialog = false },
            valueRange = 0..1,
            valueLabels = listOf("/storage/emulated/0/Movies/CatRec", "App Specific")
        )
    }
    
    if (showMicTestDialog) {
        MicTestDialog(
            darkTheme = darkTheme,
            micVolume = micVolume,
            noiseSuppression = noiseSuppression,
            onMicVolumeChange = { volume ->
                scope.launch { settingsDataStore.setMicVolume(volume) }
            },
            onNoiseSuppressionChange = { enabled ->
                scope.launch { settingsDataStore.setNoiseSuppression(enabled) }
            },
            onDismiss = { showMicTestDialog = false }
        )
    }
}

@Composable
fun SettingsCategory(
    title: String,
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val backgroundColor = if (darkTheme) Color.DarkGray else Color.LightGray
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (darkTheme) Color.White else Color.Black,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}

@Composable
fun SettingsRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    darkTheme: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = if (darkTheme) Color.White else Color.Black
        )
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
        ) {
            Text(
                text = value,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun DropdownDialog(
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    valueRange: IntRange,
    valueLabels: List<String>? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                valueRange.forEach { v ->
                    val label = valueLabels?.getOrNull(v) ?: v.toString()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = value == v,
                            onClick = { 
                                onValueChange(v)
                                onDismiss() // Auto-close dialog when option is selected
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFFD32F2F)
                            )
                        )
                        Text(
                            text = label,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            // Empty confirm button to satisfy AlertDialog requirements
        }
    )
}

@Composable
fun SliderDialog(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    valueText: String? = null
) {
    var sliderValue by remember { mutableStateOf(value) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                // Current value display
                Text(
                    text = valueText ?: sliderValue.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD32F2F),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Slider
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = valueRange,
                    steps = steps,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFD32F2F),
                        activeTrackColor = Color(0xFFD32F2F),
                        inactiveTrackColor = Color(0xFFD32F2F).copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Min and Max labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${valueRange.start.toInt()} Mbps",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Text(
                        text = "${valueRange.endInclusive.toInt()} Mbps",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onValueChange(sliderValue)
                    onDismiss()
                }
            ) {
                Text("OK", color = Color(0xFFD32F2F))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun MicTestDialog(
    darkTheme: Boolean,
    micVolume: Int,
    noiseSuppression: Boolean,
    onMicVolumeChange: (Int) -> Unit,
    onNoiseSuppressionChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var isRecording by remember { mutableStateOf(false) }
    var audioRecorder by remember { mutableStateOf<android.media.AudioRecord?>(null) }
    var audioPlayer by remember { mutableStateOf<android.media.AudioTrack?>(null) }
    var recordingThread by remember { mutableStateOf<Thread?>(null) }
    
    val context = LocalContext.current
    
    // Cleanup on dismiss
    DisposableEffect(Unit) {
        onDispose {
            isRecording = false
            recordingThread?.interrupt()
            audioRecorder?.stop()
            audioRecorder?.release()
            audioPlayer?.stop()
            audioPlayer?.release()
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "Microphone Test",
                color = if (darkTheme) Color.White else Color.Black
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                // Status indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                color = if (isRecording) Color(0xFFD32F2F) else Color.Gray,
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRecording) "Recording..." else "Ready to test",
                        color = if (darkTheme) Color.White else Color.Black,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Volume Slider
                Text(
                    text = "Volume: ${micVolume}%",
                    color = if (darkTheme) Color.White else Color.Black,
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = micVolume.toFloat(),
                    onValueChange = { onMicVolumeChange(it.toInt()) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFD32F2F),
                        activeTrackColor = Color(0xFFD32F2F),
                        inactiveTrackColor = Color(0xFFD32F2F).copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Noise Suppression Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Noise Suppression",
                        modifier = Modifier.weight(1f),
                        color = if (darkTheme) Color.White else Color.Black
                    )
                    Switch(
                        checked = noiseSuppression,
                        onCheckedChange = onNoiseSuppressionChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFD32F2F),
                            checkedTrackColor = Color(0xFFD32F2F).copy(alpha = 0.5f)
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Instructions
                Text(
                    text = "Speak into your microphone to hear yourself in real-time. Adjust volume and noise suppression settings to find the best quality.",
                    color = if (darkTheme) Color.Gray else Color.DarkGray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isRecording) {
                        // Stop recording
                        isRecording = false
                        recordingThread?.interrupt()
                        audioRecorder?.stop()
                        audioRecorder?.release()
                        audioPlayer?.stop()
                        audioPlayer?.release()
                    } else {
                        // Start recording
                        try {
                            val sampleRate = 44100
                            val channelConfig = android.media.AudioFormat.CHANNEL_IN_MONO
                            val audioFormat = android.media.AudioFormat.ENCODING_PCM_16BIT
                            val bufferSize = android.media.AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

                            // Permission check for RECORD_AUDIO
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                Toast.makeText(context, "RECORD_AUDIO permission not granted.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            
                            audioRecorder = android.media.AudioRecord(
                                android.media.MediaRecorder.AudioSource.MIC,
                                sampleRate,
                                channelConfig,
                                audioFormat,
                                bufferSize
                            )
                            
                            val playerBufferSize = android.media.AudioTrack.getMinBufferSize(
                                sampleRate,
                                android.media.AudioFormat.CHANNEL_OUT_MONO,
                                audioFormat
                            )
                            
                            audioPlayer = android.media.AudioTrack.Builder()
                                .setAudioAttributes(android.media.AudioAttributes.Builder()
                                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build())
                                .setAudioFormat(android.media.AudioFormat.Builder()
                                    .setEncoding(audioFormat)
                                    .setSampleRate(sampleRate)
                                    .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                                    .build())
                                .setBufferSizeInBytes(playerBufferSize)
                                .setTransferMode(android.media.AudioTrack.MODE_STREAM)
                                .build()
                            
                            audioRecorder?.startRecording()
                            audioPlayer?.play()
                            
                            isRecording = true
                            
                            recordingThread = Thread {
                                val buffer = ShortArray(bufferSize / 2)
                                while (isRecording && !Thread.currentThread().isInterrupted) {
                                    val readSize = audioRecorder?.read(buffer, 0, buffer.size) ?: 0
                                    if (readSize > 0) {
                                        // Apply volume
                                        val volumeMultiplier = micVolume / 100f
                                        for (i in 0 until readSize) {
                                            buffer[i] = (buffer[i] * volumeMultiplier).toInt().toShort()
                                        }
                                        
                                        // Apply noise suppression if enabled
                                        if (noiseSuppression) {
                                            // Simple noise gate - mute very quiet sounds
                                            val threshold = 1000
                                            for (i in 0 until readSize) {
                                                if (kotlin.math.abs(buffer[i].toInt()) < threshold) {
                                                    buffer[i] = 0
                                                }
                                            }
                                        }
                                        
                                        audioPlayer?.write(buffer, 0, readSize)
                                    }
                                }
                            }
                            recordingThread?.start()
                            
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Error starting microphone test: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) Color.Gray else Color(0xFFD32F2F)
                )
            ) {
                Text(
                    text = if (isRecording) "Stop Test" else "Start Test",
                    color = Color.White
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = if (darkTheme) Color.White else Color.Black)
            }
        },
        containerColor = if (darkTheme) Color.DarkGray else Color(0xFFF5F5F5),
        titleContentColor = if (darkTheme) Color.White else Color.Black,
        textContentColor = if (darkTheme) Color.White else Color.Black
    )
}

@Composable
fun MultiSelectDialog(
    title: String,
    options: List<String>,
    selectedOptions: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedOptions.contains(option),
                            onCheckedChange = { checked ->
                                val newSelection = if (checked) {
                                    selectedOptions + option
                                } else {
                                    selectedOptions - option
                                }
                                onSelectionChange(newSelection)
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFFD32F2F)
                            )
                        )
                        Text(
                            text = option,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

private fun isServiceRunning(context: Context): Boolean {
    val prefs = context.getSharedPreferences("service_state", Context.MODE_PRIVATE)
    return prefs.getBoolean("is_recording", false)
} 