package com.ibbie.catrec_gamingscreenrecorder.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Square
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.io.File
import android.util.Log

@Composable
fun RecordingScreen(
    onStartRecording: (Boolean, Boolean, String) -> Unit,
    darkTheme: Boolean
) {
    val context = LocalContext.current
    val settingsDataStore = remember { com.ibbie.catrec_gamingscreenrecorder.SettingsDataStore(context) }
    val orientation by settingsDataStore.orientation.collectAsState(initial = "Auto")
    val recordings = remember { mutableStateListOf<File>() }
    val isRecording = remember { mutableStateOf(false) }
    
    // Load recordings on first composition
    LaunchedEffect(Unit) {
        loadRecordings(context, recordings)
    }
    
    // Check service status periodically and refresh recordings list
    LaunchedEffect(Unit) {
        // Clear any stale recording state on app start
        val prefs = context.getSharedPreferences("service_state", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_recording", false).apply()
        Log.d("RecordingScreen", "Cleared stale recording state on app start")
        
        while (true) {
            val serviceRunning = isServiceRunning(context)
            
            // If service just stopped, refresh the recordings list
            if (isRecording.value && !serviceRunning) {
                loadRecordings(context, recordings)
                Log.d("RecordingScreen", "Recording stopped, refreshed recordings list")
            }
            
            // Only log when state changes
            if (isRecording.value != serviceRunning) {
                Log.d("RecordingScreen", "Service state changed: $serviceRunning")
            }
            
            isRecording.value = serviceRunning
            delay(1000)
        }
    }
    
    // Auto-refresh recordings when recording state changes
    LaunchedEffect(isRecording.value) {
        if (!isRecording.value) {
            delay(1000) // Wait a bit for file to be saved
            loadRecordings(context, recordings)
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (darkTheme) Color.Black else Color(0xFFF5F5F5))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 160.dp) // Make room for the record button
        ) {
            // Videos title and line
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Videos",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (darkTheme) Color.White else Color.Black
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(if (darkTheme) Color.White else Color.Black)
                )
            }
            
            // Videos list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                if (recordings.isEmpty()) {
                    item {
                        // Empty state - no content below "Videos"
                    }
                } else {
                    items(recordings) { file ->
                        RecordingItem(
                            file = file,
                            darkTheme = darkTheme,
                            onPlay = { playRecording(context, file) },
                            onShare = { shareRecording(context, file) },
                            onDelete = { 
                                deleteRecording(context, file, recordings)
                            }
                        )
                    }
                }
            }
        }
        
        // Record button at bottom right
        Button(
            onClick = {
                Log.d("RecordingScreen", "Record button clicked, isRecording: "+isRecording.value)
                if (isRecording.value) {
                    // Stop recording
                    Log.d("RecordingScreen", "Stopping recording")
                    val stopIntent = Intent(context, com.ibbie.catrec_gamingscreenrecorder.ScreenRecorderService::class.java)
                    stopIntent.action = "com.ibbie.catrec.ACTION_STOP"
                    context.startService(stopIntent)
                } else {
                    // Start recording
                    Log.d("RecordingScreen", "Starting recording")
                    onStartRecording(true, true, orientation)
                }
            },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)), // Red color
            modifier = Modifier
                .size(140.dp) // Increased from 120dp
                .align(Alignment.BottomEnd)
                .padding(32.dp)
        ) {
            Icon(
                imageVector = if (isRecording.value) Icons.Default.Square else Icons.Default.PlayArrow,
                contentDescription = if (isRecording.value) "Stop Recording" else "Start Recording",
                tint = Color.White,
                modifier = Modifier.size(96.dp) // Increased from 80dp
            )
        }
    }
}

@Composable
fun RecordingItem(
    file: File,
    darkTheme: Boolean,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { 
                Text(
                    "Delete Recording",
                    color = if (darkTheme) Color.White else Color.Black
                )
            },
            text = { 
                Text(
                    "Are you sure you want to delete '${file.name}'?",
                    color = if (darkTheme) Color.White else Color.Black
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = Color(0xFFD32F2F))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = if (darkTheme) Color.White else Color.Black)
                }
            },
            containerColor = if (darkTheme) Color.DarkGray else Color(0xFFF5F5F5),
            titleContentColor = if (darkTheme) Color.White else Color.Black,
            textContentColor = if (darkTheme) Color.White else Color.Black
        )
    }
    val textColor = if (darkTheme) Color.White else Color.Black
    val backgroundColor = if (darkTheme) Color.DarkGray else Color.LightGray
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = file.name,
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatFileSize(file.length()) + " • " + formatDate(file.lastModified()),
                    color = if (darkTheme) Color.Gray else Color.DarkGray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            // Action buttons - all red with bigger icons
            IconButton(
                onClick = onPlay,
                modifier = Modifier.size(56.dp) // Increased from 48dp
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color(0xFFD32F2F),
                    modifier = Modifier.size(40.dp) // Increased from 32dp
                )
            }
            
            IconButton(
                onClick = onShare,
                modifier = Modifier.size(56.dp) // Increased from 48dp
            ) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = "Share",
                    tint = Color(0xFFD32F2F),
                    modifier = Modifier.size(40.dp) // Increased from 32dp
                )
            }
            
            IconButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.size(56.dp) // Increased from 48dp
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color(0xFFD32F2F),
                    modifier = Modifier.size(40.dp) // Increased from 32dp
                )
            }
        }
    }
}

// Helper functions (same as before)
private fun loadRecordings(context: Context, recordings: MutableList<File>) {
    val dir = File(context.getExternalFilesDir(null), "Recordings")
    if (dir.exists()) {
        val files = dir.listFiles { file -> 
            file.isFile && file.extension == "mp4" && file.name.contains("_final")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
        
        recordings.clear()
        recordings.addAll(files)
    }
}

private fun isServiceRunning(context: Context): Boolean {
    val prefs = context.getSharedPreferences("service_state", Context.MODE_PRIVATE)
    return prefs.getBoolean("is_recording", false)
}

private fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
        else -> "${size / (1024 * 1024 * 1024)} GB"
    }
}

private fun formatDate(timestamp: Long): String {
    val date = java.util.Date(timestamp)
    val formatter = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
    return formatter.format(date)
}

private fun playRecording(context: Context, file: File) {
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e("RecordingScreen", "Error playing recording", e)
        // Show a toast or snackbar to inform the user
        android.widget.Toast.makeText(context, "Error playing video", android.widget.Toast.LENGTH_SHORT).show()
    }
}

private fun shareRecording(context: Context, file: File) {
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Recording"))
    } catch (e: Exception) {
        Log.e("RecordingScreen", "Error sharing recording", e)
        // Show a toast or snackbar to inform the user
        android.widget.Toast.makeText(context, "Error sharing video", android.widget.Toast.LENGTH_SHORT).show()
    }
}

private fun deleteRecording(context: Context, file: File, recordings: MutableList<File>) {
    if (file.delete()) {
        recordings.remove(file)
    }
} 