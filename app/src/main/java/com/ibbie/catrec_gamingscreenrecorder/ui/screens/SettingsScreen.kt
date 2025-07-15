package com.ibbie.catrec_gamingscreenrecorder.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ibbie.catrec_gamingscreenrecorder.AnalyticsManager
import com.ibbie.catrec_gamingscreenrecorder.SettingsDataStore
import com.ibbie.catrec_gamingscreenrecorder.UpdateManager
import com.ibbie.catrec_gamingscreenrecorder.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableFloatStateOf


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit
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
    var showStopOptionsDialog by remember { mutableStateOf(false) }
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
                                                // settingsDataStore.setStopOptions(settings.stopOptions + option) // Removed: function does not exist
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
                                        // settingsDataStore.setStopOptions(newStopOptions) // Removed: function does not exist
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
                        Text(
                            text = "Bitrate: ${(sliderBitrate / 1_000_000f).toInt()} Mbps",
                            color = if (darkTheme) Color.White else Color.Black,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        // Bitrate Slider
                        Slider(
                            value = sliderBitrate,
                            onValueChange = { sliderBitrate = it },
                            onValueChangeFinished = { 
                                scope.launch { settingsDataStore.setVideoBitrate(sliderBitrate.toInt()) }
                            },
                            valueRange = 5_000_000f..100_000_000f,
                            steps = 0,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                activeTrackColor = Color.Red,
                                inactiveTrackColor = Color.Red.copy(alpha = 0.25f),
                                activeTickColor = Color.Transparent,
                                inactiveTickColor = Color.Transparent
                            ),
                            thumb = { CircularThumb() }
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
                        Text(
                            text = "Framerate: ${sliderFramerate.toInt()} FPS",
                            color = if (darkTheme) Color.White else Color.Black,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        // Framerate Slider
                        Slider(
                            value = sliderFramerate,
                            onValueChange = { sliderFramerate = it },
                            onValueChangeFinished = { 
                                scope.launch { settingsDataStore.setFps(sliderFramerate.toInt()) }
                            },
                            valueRange = 15f..120f,
                            steps = 0,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                activeTrackColor = Color.Red,
                                inactiveTrackColor = Color.Red.copy(alpha = 0.25f),
                                activeTickColor = Color.Transparent,
                                inactiveTickColor = Color.Transparent
                            ),
                            thumb = { CircularThumb() }
                        )
                    }
                    
                    // Audio Bitrate
                    var audioBitrateExpanded by remember { mutableStateOf(false) }
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
                                onClick = { audioBitrateExpanded = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                            ) {
                                Text(
                                    text = settings.audioBitrate,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            DropdownMenu(
                                expanded = audioBitrateExpanded,
                                onDismissRequest = { audioBitrateExpanded = false }
                            ) {
                                listOf("128 kbps", "192 kbps", "256 kbps", "320 kbps").forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            audioBitrateExpanded = false
                                            scope.launch { settingsDataStore.setAudioBitrate(option) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    // Audio Sample Rate
                    var audioSampleRateExpanded by remember { mutableStateOf(false) }
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
                                onClick = { audioSampleRateExpanded = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                            ) {
                                Text(
                                    text = settings.audioSampleRate,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            DropdownMenu(
                                expanded = audioSampleRateExpanded,
                                onDismissRequest = { audioSampleRateExpanded = false }
                            ) {
                                listOf("44100 Hz", "48000 Hz").forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            audioSampleRateExpanded = false
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
                    
                    // Mic Test
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Microphone Test",
                                color = if (darkTheme) Color.White else Color.Black
                            )
                            Text(
                                text = "Test your microphone settings",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                        Button(
                            onClick = { showMicTestDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                        ) {
                            Text(
                                text = "Test",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
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
    LocalHapticFeedback.current

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
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color.Red,
                        inactiveTrackColor = Color.Red.copy(alpha = 0.25f),
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent
                    ),
                    thumb = { CircularThumb() }
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



private fun isServiceRunning(context: Context): Boolean {
    val prefs = context.getSharedPreferences("service_state", Context.MODE_PRIVATE)
    return prefs.getBoolean("is_recording", false)
}

@Composable
fun CircularThumb() {
    Canvas(modifier = Modifier.size(16.dp)) {
        drawCircle(
            color = Color.White,
            radius = size.minDimension / 2
        )
    }
} 