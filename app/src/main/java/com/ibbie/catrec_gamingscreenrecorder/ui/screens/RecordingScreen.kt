package com.ibbie.catrec_gamingscreenrecorder.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Square
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ibbie.catrec_gamingscreenrecorder.SettingsDataStore
import com.ibbie.catrec_gamingscreenrecorder.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay
import java.io.File
import android.util.Log
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.VideoView
import android.widget.MediaController
import android.net.Uri
import androidx.compose.material.icons.filled.Close
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.Bitmap

@Composable
fun RecordingScreen(
    onStartRecording: (Boolean, Boolean, String) -> Unit,
    darkTheme: Boolean
) {
    val context = LocalContext.current
    val settingsDataStore = SettingsDataStore(context)
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(settingsDataStore)
    )
    val settings by viewModel.settingsFlow.collectAsState()
    val recordings = remember { mutableStateListOf<File>() }
    val isRecording = remember { mutableStateOf(false) }
    var sortByNewest by remember { mutableStateOf(true) }
    var selectedRecording by remember { mutableStateOf<File?>(null) }
    var showSortDropdown by remember { mutableStateOf(false) }
    val isRefreshing = remember { mutableStateOf(false) }

    // Load recordings on first composition
    LaunchedEffect(Unit) {
        loadRecordings(context, recordings, sortByNewest)
    }
    // Refresh recordings when sort changes
    LaunchedEffect(sortByNewest) {
        loadRecordings(context, recordings, sortByNewest)
    }
    // Check service status periodically and refresh recordings list
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("service_state", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_recording", false).apply()
        while (true) {
            val serviceRunning = isServiceRunning(context)
            if (isRecording.value && !serviceRunning) {
                loadRecordings(context, recordings, sortByNewest)
            }
            isRecording.value = serviceRunning
            delay(1000)
        }
    }
    LaunchedEffect(isRecording.value) {
        if (!isRecording.value) {
            delay(1000)
            loadRecordings(context, recordings, sortByNewest)
            // Do NOT set selectedRecording here; prevents auto-opening video player
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
                .padding(bottom = 160.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Screen Recorder",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (darkTheme) Color.White else Color.Black,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    IconButton(onClick = { showSortDropdown = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort",
                            tint = Color(0xFFD32F2F)
                        )
                    }
                    DropdownMenu(
                        expanded = showSortDropdown,
                        onDismissRequest = { showSortDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Newest First") },
                            onClick = {
                                sortByNewest = true
                                showSortDropdown = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Oldest First") },
                            onClick = {
                                sortByNewest = false
                                showSortDropdown = false
                            }
                        )
                    }
                }
            }
            if (recordings.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No recordings found.", color = Color.Gray)
                }
            } else {
                SwipeRefresh(
                    state = rememberSwipeRefreshState(isRefreshing.value),
                    onRefresh = {
                        isRefreshing.value = true
                        loadRecordings(context, recordings, sortByNewest)
                        isRefreshing.value = false
                    }
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(220.dp),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(recordings) { file ->
                            RecordingThumbnail(
                                file = file,
                                darkTheme = darkTheme,
                                onClick = { selectedRecording = file }
                            )
                        }
                    }
                }
            }
        }
        
        // Record button at bottom right
        Button(
            onClick = {
                if (isRecording.value) {
                    val stopIntent = Intent(context, com.ibbie.catrec_gamingscreenrecorder.ScreenRecorderService::class.java)
                    stopIntent.action = "com.ibbie.catrec.ACTION_STOP"
                    context.startService(stopIntent)
                    isRecording.value = false // Update immediately
                } else {
                    // Use audio source setting to determine what to record
                    val recordMic = when (settings.audioSource) {
                        "Mic" -> true
                        "System Audio" -> false
                        "System Audio + Mic" -> true
                        else -> false
                    }
                    val recordInternal = when (settings.audioSource) {
                        "Mic" -> false
                        "System Audio" -> true
                        "System Audio + Mic" -> true
                        else -> true
                    }
                    onStartRecording(recordMic, recordInternal, settings.orientation)
                    isRecording.value = true // Update immediately
                }
            },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
            modifier = Modifier
                .size(140.dp)
                .align(Alignment.BottomEnd)
                .padding(32.dp)
        ) {
            Icon(
                imageVector = if (isRecording.value) Icons.Default.Square else Icons.Default.PlayArrow,
                contentDescription = if (isRecording.value) "Stop Recording" else "Start Recording",
                tint = Color.White,
                modifier = Modifier.size(96.dp)
            )
        }
        if (selectedRecording != null) {
            VideoPlayerOverlay(
                file = selectedRecording!!,
                onClose = { selectedRecording = null },
                onShare = { shareRecording(context, selectedRecording!!) },
                onDelete = {
                    deleteRecording(context, selectedRecording!!, recordings)
                    selectedRecording = null
                }
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
fun loadRecordings(context: Context, recordings: MutableList<File>, sortByNewest: Boolean) {
    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        try {
            // Look in Movies/CatRec folder instead of app-specific storage
            val moviesDir = File("/storage/emulated/0/Movies/CatRec")
            if (!moviesDir.exists()) {
                moviesDir.mkdirs()
            }
            
            val videoFiles = moviesDir.listFiles { file ->
                file.isFile && file.extension.lowercase() in listOf("mp4", "avi", "mov", "mkv")
            }?.toList() ?: emptyList()
            
            val sortedFiles = if (sortByNewest) {
                videoFiles.sortedByDescending { it.lastModified() }
            } else {
                videoFiles.sortedBy { it.lastModified() }
            }
            
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                recordings.clear()
                recordings.addAll(sortedFiles)
            }
        } catch (e: Exception) {
            Log.e("RecordingScreen", "Error loading recordings", e)
        }
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

@Composable
fun RecordingThumbnail(
    file: File,
    darkTheme: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    LaunchedEffect(file) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(file.absolutePath)
                
                // Get first frame instead of looking for thumbnail file
                val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        bitmap = frame
                    }
                }
                retriever.release()
            } catch (e: Exception) {
                Log.e("RecordingThumbnail", "Error loading thumbnail", e)
            }
        }
    }
    
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "Recording thumbnail",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Gray),
            contentAlignment = Alignment.Center
        ) {
            Text("No Preview", color = Color.White)
        }
    }
}

@Composable
fun VideoPlayerOverlay(
    file: File,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }
    var videoPosition by remember { mutableStateOf(0) }
    var videoDuration by remember { mutableStateOf(0) }
    var videoView: VideoView? by remember { mutableStateOf(null) }

    fun formatDuration(ms: Int): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) // Fully opaque
            .zIndex(10f)
    ) {
        // Video
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoURI(Uri.fromFile(file))
                    setOnPreparedListener { mp ->
                        videoDuration = mp.duration
                        start()
                        isPlaying = true
                    }
                    setOnCompletionListener {
                        isPlaying = false
                    }
                    setOnClickListener {
                        if (isPlaying) pause() else start()
                        isPlaying = !isPlaying
                    }
                    videoView = this
                }
            },
            update = { view ->
                if (isPlaying) view.start() else view.pause()
                view.seekTo(videoPosition)
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .align(Alignment.Center)
        )
        // Center play/pause button
        IconButton(
            onClick = {
                videoView?.let {
                    if (isPlaying) it.pause() else it.start()
                    isPlaying = !isPlaying
                }
            },
            modifier = Modifier.align(Alignment.Center)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(64.dp)
            )
        }
        // Seek bar with duration
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 32.dp, vertical = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Slider(
                value = videoPosition.toFloat(),
                onValueChange = {
                    videoPosition = it.toInt()
                    videoView?.seekTo(videoPosition)
                },
                valueRange = 0f..(videoDuration.toFloat().coerceAtLeast(1f)),
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFD32F2F),
                    activeTrackColor = Color(0xFFD32F2F)
                ),
                thumb = {
                    Box(
                        Modifier
                            .size(20.dp)
                            .background(Color(0xFFD32F2F), shape = CircleShape)
                    )
                }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "${formatDuration(videoPosition)} / ${formatDuration(videoDuration)}",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        // Top bar with close, share, delete
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
            Row {
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                }
            }
        }
    }
} 