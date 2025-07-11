package com.ibbie.catrec_gamingscreenrecorder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ibbie.catrec_gamingscreenrecorder.SettingsDataStore
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
    val settingsDataStore = remember { SettingsDataStore(context) }
    val scope = rememberCoroutineScope()
    val scheduledRecordingEnabled by settingsDataStore.scheduledRecordingEnabled.collectAsState(initial = false)
    val scheduledRecordingTime by settingsDataStore.scheduledRecordingTime.collectAsState(initial = 0L)
    var showDatePicker by remember { mutableStateOf(false) }
    val scheduledDate = remember(scheduledRecordingTime) {
        if (scheduledRecordingTime > 0L) Date(scheduledRecordingTime) else Date()
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
        if (scheduledRecordingEnabled && scheduledRecordingTime > System.currentTimeMillis()) {
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
                initialTime = scheduledRecordingTime,
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