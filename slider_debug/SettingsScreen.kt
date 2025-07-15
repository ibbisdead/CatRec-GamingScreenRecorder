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
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.ibbie.catrec_gamingscreenrecorder.SettingsDataStore
import com.ibbie.catrec_gamingscreenrecorder.viewmodel.SettingsViewModel
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.ibbie.catrec_gamingscreenrecorder.AdManager
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.mutableFloatStateOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController,
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onStartRecording: (Boolean, Boolean, String) -> Unit
) {
    val context = LocalContext.current
    val settingsDataStore = SettingsDataStore(context)
    val scope = rememberCoroutineScope()
    val isRecording = remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val analyticsManager = remember { AnalyticsManager(context) }
    
    // ViewModel for settings
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(SettingsDataStore(LocalContext.current))
    )
    val settings by viewModel.settingsFlow.collectAsState()
    
    val micPermissionGranted = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micPermissionGranted.value = granted
        if (!granted) {
            Toast.makeText(context, "Microphone permission is required for mic test.", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Check service status periodically
    LaunchedEffect(Unit) {
        while (true) {
            val serviceRunning = isServiceRunning(context)
            isRecording.value = serviceRunning
            kotlinx.coroutines.delay(1000)
        }
    }
    
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
                    val countdownOptions = listOf(0, 1, 3, 5, 10)
                    // Countdown
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Countdown",
                            modifier = Modifier.weight(1f),
                            color = if (darkTheme) Color.White else Color.Black
                        )
                        Box(contentAlignment = Alignment.CenterEnd) {
                            Button(
                                onClick = { expanded = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                            ) {
                                Text(
                                    text = when (settings.countdown) {
                                        0 -> "Off"
                                        1 -> "1 second"
                                        else -> "${settings.countdown} seconds"
                                    },
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
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
                        }
                    }
                    
                    // Overlay
                    SettingsRow(
                        title = "Overlay",
                        value = if (settings.overlayEnabled) "On" else "Off",
                        onClick = {
                            scope.launch { settingsDataStore.setOverlayEnabled(!settings.overlayEnabled) }
                        },
                        darkTheme = darkTheme
                    )
                    
                    // Stop Options
                    // Stopping Options
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Stopping Options",
                            modifier = Modifier.weight(1f),
                            color = if (darkTheme) Color.White else Color.Black
                        )
                        Box(contentAlignment = Alignment.CenterEnd) {
                            Button(
                                onClick = { showStopOptionsDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                            ) {
                                Text(
                                    text = if (settings.stopOptions.size > 1) "Multiple" else settings.stopOptions.firstOrNull() ?: "None",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            DropdownMenu(
                                expanded = showStopOptionsDialog,
                                onDismissRequest = { showStopOptionsDialog = false }
                            ) {
                                listOf("Screen Off", "Notification Bar", "Shake Device").forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            showStopOptionsDialog = false
                                            scope.launch {
                                                settingsDataStore.setStopOptions(settings.stopOptions + option)
                                                if (option == "Screen Off") {
                                                    settingsDataStore.setPauseOnScreenOff(false)
                                                }
                                            }
                                        }
                                    )
                                }
                            }
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
                            checked = settings.pauseOnScreenOff,
                            onCheckedChange = { 
                                scope.launch { 
                                    settingsDataStore.setPauseOnScreenOff(it)
                                    // If enabling pause, disable stop on screen off
                                    if (it) {
                                        val newStopOptions = settings.stopOptions - "Screen Off"
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
                    // Replace the Resolution dropdown with a right-aligned button and DropdownMenu:
                    // Resolution
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Resolution",
                            modifier = Modifier.weight(1f),
                            color = if (darkTheme) Color.White else Color.Black
                        )
                        Box(contentAlignment = Alignment.CenterEnd) {
                            Button(
                                onClick = { resolutionExpanded = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                            ) {
                                Text(
                                    text = resolutionOptions.find { it.first == settings.resolution }?.second ?: "Device Native",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
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
                        }
                    }
                    
                    // Bitrate
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = "Bitrate",
                            modifier = Modifier.padding(bottom = 4.dp),
                            color = if (darkTheme) Color.White else Color.Black
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        var sliderBitrate by remember { mutableFloatStateOf(settings.videoBitrate.toFloat()) }
                        var lastHapticBitrate by remember { mutableStateOf(sliderBitrate) }
                        Text(
                            text = "Bitrate: ${(settings.videoBitrate / 1_000_000f).toInt()} Mbps",
                            color = if (darkTheme) Color.White else Color.Black,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        // Bitrate Slider
                        Slider(
                            value = sliderBitrate,
                            onValueChange = { sliderBitrate = it },
                            valueRange = 5_000_000f..100_000_000f,
                            steps = 0,
                            modifier = Modifier.fillMaxWidth().height(32.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.Red,
                                inactiveTrackColor = Color.Red.copy(alpha = 0.25f),
                                activeTickColor = Color.Transparent,
                                inactiveTickColor = Color.Transparent
                            )
                        )
                    }
                    
                    // Framerate
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = "Framerate",
                            color = if (darkTheme) Color.White else Color.Black,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        var sliderFramerate by remember { mutableFloatStateOf(settings.fps.toFloat()) }
                        var lastHapticFps by remember { mutableStateOf(sliderFramerate) }
                        Text(
                            text = "Framerate: ${settings.fps} FPS",
                            color = if (darkTheme) Color.White else Color.Black,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        // Framerate Slider
                        Slider(
                            value = sliderFramerate,
                            onValueChange = { sliderFramerate = it },
                            valueRange = 15f..120f,
                            steps = 0,
                            modifier = Modifier.fillMaxWidth().height(32.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.Red,
                                inactiveTrackColor = Color.Red.copy(alpha = 0.25f),
                                activeTickColor = Color.Transparent,
                                inactiveTickColor = Color.Transparent
                            )
                        )
                    }
                    
                    // Audio Bitrate
                    // Audio Bitrate
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Audio Bitrate",
                            modifier = Modifier.weight(1f),
                            color = if (darkTheme) Color.White else Color.Black
                        )
                        Box(contentAlignment = Alignment.CenterEnd) {
                            Button(
                                onClick = { showAudioBitrateDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                            ) {
                                Text(
                                    text = settings.audioBitrate,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            DropdownMenu(
                                expanded = showAudioBitrateDialog,
                                onDismissRequest = { showAudioBitrateDialog = false }
                            ) {
                                listOf("128 kbps", "192 kbps", "256 kbps", "320 kbps").forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            showAudioBitrateDialog = false
                                            scope.launch { settingsDataStore.setAudioBitrate(option) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    // Audio Sample Rate
                    // Audio Sample Rate
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Audio Sample Rate",
                            modifier = Modifier.weight(1f),
                            color = if (darkTheme) Color.White else Color.Black
                        )
                        Box(contentAlignment = Alignment.CenterEnd) {
                            Button(
                                onClick = { showAudioSampleRateDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                            ) {
                                Text(
                                    text = settings.audioSampleRate,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            DropdownMenu(
                                expanded = showAudioSampleRateDialog,
                                onDismissRequest = { showAudioSampleRateDialog = false }
                            ) {
                                listOf("44100 Hz", "48000 Hz").forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            showAudioSampleRateDialog = false
                                            scope.launch { settingsDataStore.setAudioSampleRate(option) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    // Audio Channel
                    var audioChannelExpanded by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Audio Channel",
                            modifier = Modifier.weight(1f),
                            color = if (darkTheme) Color.White else Color.Black
                        )
                        Box(
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Button(
                                onClick = { audioChannelExpanded = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                            ) {
                                Text(
                                    text = settings.audioChannel,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            DropdownMenu(
                                expanded = audioChannelExpanded,
                                onDismissRequest = { audioChannelExpanded = false }
                            ) {
                                listOf("Mono", "Stereo").forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            audioChannelExpanded = false
                                            scope.launch { settingsDataStore.setAudioChannel(option) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    // Audio Source
                    var audioSourceExpanded by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Audio Source",
                            modifier = Modifier.weight(1f),
                            color = if (darkTheme) Color.White else Color.Black
                        )
                        Box(
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Button(
                                onClick = { audioSourceExpanded = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                            ) {
                                Text(
                                    text = settings.audioSource,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            DropdownMenu(
                                expanded = audioSourceExpanded,
                                onDismissRequest = { audioSourceExpanded = false }
                            ) {
                                listOf("Mic", "System Audio", "System Audio + Mic").forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            audioSourceExpanded = false
                                            scope.launch { settingsDataStore.setAudioSource(option) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    // Orientation
                    var orientationExpanded by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Orientation",
                            modifier = Modifier.weight(1f),
                            color = if (darkTheme) Color.White else Color.Black
                        )
                        Box(
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Button(
                                onClick = { orientationExpanded = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                            ) {
                                Text(
                                    text = settings.orientation,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            DropdownMenu(
                                expanded = orientationExpanded,
                                onDismissRequest = { orientationExpanded = false }
                            ) {
                                listOf("Vertical", "Horizontal", "Auto").forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            orientationExpanded = false
                                            scope.launch { settingsDataStore.setOrientation(option) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    // Recording Destination
                    var destinationExpanded by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recording Destination",
                            modifier = Modifier.weight(1f),
                            color = if (darkTheme) Color.White else Color.Black
                        )
                        Box(
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Button(
                                onClick = { destinationExpanded = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                            ) {
                                Text(
                                    text = if (settings.fileDestination.contains("Movies")) "Movies/CatRec" else "App Specific",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            DropdownMenu(
                                expanded = destinationExpanded,
                                onDismissRequest = { destinationExpanded = false }
                            ) {
                                listOf("/storage/emulated/0/Movies/CatRec", "App Specific").forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            destinationExpanded = false
                                            scope.launch { settingsDataStore.setFileDestination(option) }
                                        }
                                    )
                                }
                            }
                        }
                    }


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
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = settings.analyticsEnabled,
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
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = settings.autoUpdateCheckEnabled,
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
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = settings.crashReportingEnabled,
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
                            color = Color.Gray
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
            value = settings.countdown,
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
            selectedOptions = settings.stopOptions,
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
            value = when (settings.orientation) {
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
            value = when (settings.resolution) {
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
            value = settings.videoBitrate / 1_000_000f,
            onValueChange = { v ->
                scope.launch { settingsDataStore.setVideoBitrate((v * 1_000_000).toInt()) }
            },
            onDismiss = { showBitrateDialog = false },
            valueRange = 1f..100f,
            steps = 99,
            valueText = "${(settings.videoBitrate / 1_000_000f).toInt()} Mbps",
            darkTheme = darkTheme
        )
    }
    
    if (showFpsDialog) {
        DropdownDialog(
            title = "FPS",
            value = when (settings.fps) {
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
            value = when (settings.audioBitrate) {
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
            value = if (settings.audioSampleRate == "44100 Hz") 0 else 1,
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
            value = if (settings.audioChannel == "Mono") 0 else 1,
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
            value = when (settings.audioSource) {
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
            value = if (settings.fileDestination == "/storage/emulated/0/Movies/CatRec") 0 else 1,
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
            micVolume = settings.micVolume,
            noiseSuppression = settings.noiseSuppression,
            onMicVolumeChange = { volume ->
                scope.launch { settingsDataStore.setMicVolume(volume) }
            },
            onNoiseSuppressionChange = { enabled ->
                scope.launch { settingsDataStore.setNoiseSuppression(enabled) }
            },
            onDismiss = { showMicTestDialog = false },
            micPermissionGranted = micPermissionGranted.value,
            onRequestPermission = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SliderDialog(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    valueText: String? = null,
    darkTheme: Boolean
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.Red,
                        inactiveTrackColor = Color.Red.copy(alpha = 0.3f),
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent
                    )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MicTestDialog(
    darkTheme: Boolean,
    micVolume: Int,
    noiseSuppression: Boolean,
    onMicVolumeChange: (Int) -> Unit,
    onNoiseSuppressionChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    micPermissionGranted: Boolean,
    onRequestPermission: () -> Unit
) {
    var isRecording by remember { mutableStateOf(false) }
    var audioRecorder by remember { mutableStateOf<android.media.AudioRecord?>(null) }
    var audioPlayer by remember { mutableStateOf<android.media.AudioTrack?>(null) }
    var recordingThread by remember { mutableStateOf<Thread?>(null) }
    var audioRecordStarted by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // Cleanup on dismiss
    DisposableEffect(Unit) {
        onDispose {
            isRecording = false
            recordingThread?.interrupt()
            audioRecorder?.let { ar ->
                if (audioRecordStarted && ar.recordingState == android.media.AudioRecord.RECORDSTATE_RECORDING) {
                    try { ar.stop() } catch (_: Exception) {}
                }
                ar.release()
            }
            audioPlayer?.let { ap ->
                if (ap.playState == android.media.AudioTrack.PLAYSTATE_PLAYING) {
                    try { ap.stop() } catch (_: Exception) {}
                }
                ap.release()
            }
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
                if (!micPermissionGranted) {
                    Text(
                        text = "Microphone permission is required to test the mic.",
                        color = Color.Red,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onRequestPermission) {
                        Text("Grant Permission")
                    }
                    return@Column
                }
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
                    text = "Mic Gain: ${micVolume}%",
                    color = if (darkTheme) Color.White else Color.Black,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                // Mic Gain Slider (in Mic Test dialog)
                Slider(
                    value = micVolume.toFloat(),
                    onValueChange = { onMicVolumeChange(it.toInt()) },
                    valueRange = 0f..100f,
                    steps = 0,
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.Red,
                        inactiveTrackColor = Color.Red.copy(alpha = 0.25f),
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent
                    )
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
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isRecording) {
                        isRecording = false
                        recordingThread?.interrupt()
                        audioRecorder?.let { ar ->
                            if (audioRecordStarted && ar.recordingState == android.media.AudioRecord.RECORDSTATE_RECORDING) {
                                try { ar.stop() } catch (_: Exception) {}
                            }
                            ar.release()
                        }
                        audioPlayer?.let { ap ->
                            if (ap.playState == android.media.AudioTrack.PLAYSTATE_PLAYING) {
                                try { ap.stop() } catch (_: Exception) {}
                            }
                            ap.release()
                        }
                        audioRecordStarted = false
                    } else {
                        // Check runtime mic permission before starting test
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            onRequestPermission()
                            return@Button
                        }
                        
                        try {
                            val sampleRate = 44100
                            val channelConfig = android.media.AudioFormat.CHANNEL_IN_MONO
                            val audioFormat = android.media.AudioFormat.ENCODING_PCM_16BIT
                            val bufferSize = android.media.AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

                            val ar = android.media.AudioRecord(
                                android.media.MediaRecorder.AudioSource.MIC,
                                sampleRate,
                                channelConfig,
                                audioFormat,
                                bufferSize
                            )
                            if (ar.state != android.media.AudioRecord.STATE_INITIALIZED) {
                                Toast.makeText(context, "Failed to initialize AudioRecord.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            audioRecorder = ar
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
                            
                            // Before using audioTrack, add safety check
                            if (audioPlayer == null || audioPlayer?.state != android.media.AudioTrack.STATE_INITIALIZED) {
                                Toast.makeText(context, "Failed to initialize AudioTrack.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            ar.startRecording()
                            audioRecordStarted = true
                            audioPlayer?.play()
                            isRecording = true
                            recordingThread = Thread {
                                val buffer = ShortArray(bufferSize / 2)
                                while (isRecording && !Thread.currentThread().isInterrupted) {
                                    val readSize = audioRecorder?.read(buffer, 0, buffer.size) ?: 0
                                    if (readSize > 0) {
                                        val volumeMultiplier = micVolume / 100f
                                        for (i in 0 until readSize) {
                                            buffer[i] = (buffer[i] * volumeMultiplier).toInt().toShort()
                                        }
                                        if (noiseSuppression) {
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
                            Toast.makeText(context, "Error starting microphone test: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) Color.Gray else Color(0xFFD32F2F)
                ),
                enabled = micPermissionGranted
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