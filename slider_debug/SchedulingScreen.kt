package com.ibbie.catrec_gamingscreenrecorder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ibbie.catrec_gamingscreenrecorder.SettingsDataStore
import com.ibbie.catrec_gamingscreenrecorder.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulingScreen(
    navController: NavHostController,
    darkTheme: Boolean
) {
    val context = LocalContext.current
    val settingsDataStore = SettingsDataStore(context)
    val scope = rememberCoroutineScope()
    
    // ViewModel for settings
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(SettingsDataStore(LocalContext.current))
    )
    val settings by viewModel.settingsFlow.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    val scheduledDate = remember(settings.scheduledRecordingTime) {
        if (settings.scheduledRecordingTime > 0L) Date(settings.scheduledRecordingTime) else Date()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Scheduled Recordings",
            style = MaterialTheme.typography.headlineMedium,
            color = if (darkTheme) Color.White else Color.Black
        )
        Spacer(modifier = Modifier.height(32.dp))
        if (settings.scheduledRecordingEnabled && settings.scheduledRecordingTime > System.currentTimeMillis()) {
            Text(
                text = "Next scheduled recording:",
                style = MaterialTheme.typography.bodyLarge,
                color = if (darkTheme) Color.White else Color.Black
            )
            Text(
                text = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(scheduledDate),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row {
                Button(onClick = { showDatePicker = true }) {
                    Text("Reschedule")
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(onClick = {
                    scope.launch {
                        settingsDataStore.setScheduledRecordingEnabled(false)
                        settingsDataStore.setScheduledRecordingTime(0L)
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Cancel")
                }
            }
        } else {
            Text(
                text = "No scheduled recordings.",
                style = MaterialTheme.typography.bodyLarge,
                color = if (darkTheme) Color.Gray else Color.DarkGray
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { showDatePicker = true }) {
                Text("Schedule New Recording")
            }
        }
        if (showDatePicker) {
            DateTimePickerDialog(
                initialTime = settings.scheduledRecordingTime,
                onDismiss = { showDatePicker = false },
                onTimeSelected = { time ->
                    scope.launch {
                        settingsDataStore.setScheduledRecordingTime(time)
                        settingsDataStore.setScheduledRecordingEnabled(true)
                    }
                    showDatePicker = false
                }
            )
        }
    }
} 