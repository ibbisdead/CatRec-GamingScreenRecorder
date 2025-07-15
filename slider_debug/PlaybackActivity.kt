package com.ibbie.catrec_gamingscreenrecorder

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawBehind
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import java.io.File
import org.json.JSONArray
import java.util.concurrent.TimeUnit
import androidx.compose.foundation.layout.RowScope

class PlaybackActivity : ComponentActivity() {
    
    // Simple microphone permission launcher (example implementation)
    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Microphone permission is required for audio recording", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Example: Check microphone permission during initialization
        // Uncomment if this activity needs microphone access in the future
        /*
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        */
        
        val videoPath = intent.getStringExtra("video_uri")
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PlaybackScreen(
                        videoPath = videoPath,
                        onBack = { finish() },
                        onDelete = { deleteVideo(videoPath) },
                        onShare = { shareVideo(videoPath) }
                    )
                }
            }
        }
    }
    
    private fun deleteVideo(videoPath: String?) {
        videoPath?.let {
            val file = File(it)
            if (file.exists()) {
                file.delete()
            }
        }
        finish()
    }
    
    private fun shareVideo(videoPath: String?) {
        videoPath?.let {
            val file = File(it)
            if (file.exists()) {
                val uri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.provider",
                    file
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Share Recording"))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackScreen(
    videoPath: String?, 
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showHighlightsDialog by remember { mutableStateOf(false) }
    var showHighlightClipsDialog by remember { mutableStateOf(false) }
    var showBatchShareDialog by remember { mutableStateOf(false) }
    var selectedClips by remember { mutableStateOf(setOf<File>()) }
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            videoPath?.let {
                val uri = Uri.parse(it)
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
                playWhenReady = true
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }
    // Load highlights
    val highlightTimes = remember(videoPath) {
        val file = videoPath?.let {
            val f = File(it)
            File(f.parent, f.nameWithoutExtension + "_highlights.json")
        }
        if (file?.exists() == true) {
            try {
                val arr = JSONArray(file.readText())
                List(arr.length()) { arr.getLong(it) }
            } catch (_: Exception) { emptyList() }
        } else emptyList()
    }
    
    // Load highlight clips
    val highlightClips = remember(videoPath) {
        videoPath?.let {
            val videoFile = File(it)
            val parentDir = videoFile.parentFile
            if (parentDir?.exists() == true) {
                parentDir.listFiles()?.filter { file ->
                    file.name.startsWith("CatRec_Highlight_") && file.extension == "mp4"
                }?.sortedBy { it.name } ?: emptyList()
            } else emptyList()
        } ?: emptyList()
    }
    
    // Load highlight reel
    val highlightReel = remember(videoPath) {
        videoPath?.let {
            val videoFile = File(it)
            val parentDir = videoFile.parentFile
            if (parentDir?.exists() == true) {
                parentDir.listFiles()?.find { file ->
                    file.name.startsWith("CatRec_HighlightReel_") && file.extension == "mp4"
                }
            } else null
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Preview Recording") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                if (highlightTimes.isNotEmpty()) {
                    TextButton(onClick = { showHighlightsDialog = true }) {
                        Text("View Highlights")
                    }
                }
                if (highlightClips.isNotEmpty()) {
                    TextButton(onClick = { showHighlightClipsDialog = true }) {
                        Text("View Clips")
                    }
                    TextButton(onClick = { showBatchShareDialog = true }) {
                        Text("Share Clips")
                    }
                }
                if (highlightReel != null) {
                    TextButton(onClick = {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.provider",
                            highlightReel
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "video/*"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Highlight Reel"))
                    }) {
                        Text("Share Reel")
                    }
                }
            }
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black)
                .clickable {
                    if (exoPlayer.isPlaying) {
                        exoPlayer.pause()
                        isPlaying = false
                    } else {
                        exoPlayer.play()
                        isPlaying = true
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = {
                    if (exoPlayer.isPlaying) {
                        exoPlayer.pause()
                        isPlaying = false
                    } else {
                        exoPlayer.play()
                        isPlaying = true
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
            // Skip-to-highlight FABs
            if (highlightTimes.isNotEmpty()) {
                val duration = exoPlayer.duration
                val position = exoPlayer.currentPosition
                val prevHighlight = highlightTimes.filter { it < position }.maxOrNull()
                val nextHighlight = highlightTimes.filter { it > position }.minOrNull()
                // Previous Highlight FAB
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isPlaying,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 80.dp)
                ) {
                    FloatingActionButton(
                        onClick = { if (prevHighlight != null) exoPlayer.seekTo(prevHighlight) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous Highlight",
                            tint = if (prevHighlight != null) Color.White else Color.Gray
                        )
                    }
                }
                // Next Highlight FAB
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isPlaying,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 80.dp)
                ) {
                    FloatingActionButton(
                        onClick = { if (nextHighlight != null) exoPlayer.seekTo(nextHighlight) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Next Highlight",
                            tint = if (nextHighlight != null) Color.White else Color.Gray
                        )
                    }
                }
            }
        }
        // Seek bar with highlight markers
        if (exoPlayer.duration > 0) {
            val duration = exoPlayer.duration
            val position = exoPlayer.currentPosition
            val progress = if (duration > 0) position.toFloat() / duration else 0f
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Time display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(position),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = formatTime(duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Slider container
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = MaterialTheme.shapes.medium
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)) {
                
                Slider(
                    value = progress,
                    onValueChange = { v ->
                        exoPlayer.seekTo((v * duration).toLong())
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            // Draw highlight markers on canvas
                            if (highlightTimes.isNotEmpty()) {
                                val sliderWidth = size.width
                                val markerWidth = 3f
                                val markerHeight = size.height
                                
                                highlightTimes.forEach { timestamp ->
                                    val fraction = timestamp.toFloat() / duration
                                    val x = fraction * sliderWidth
                                    
                                    // Draw thin red vertical line for highlight marker
                                    drawRect(
                                        color = Color.Red,
                                        topLeft = androidx.compose.ui.geometry.Offset(x - markerWidth / 2, 0f),
                                        size = androidx.compose.ui.geometry.Size(markerWidth, markerHeight)
                                    )
                                    
                                    // Draw a subtle glow effect
                                    drawRect(
                                        color = Color.Red.copy(alpha = 0.3f),
                                        topLeft = androidx.compose.ui.geometry.Offset(x - markerWidth, 0f),
                                        size = androidx.compose.ui.geometry.Size(markerWidth * 2, markerHeight)
                                    )
                                }
                            }
                        },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        activeTickColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        inactiveTickColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    ),
                    thumb = {
                        SliderDefaults.Thumb(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            enabled = true,
                            modifier = Modifier
                                .size(20.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                        )
                    }
                )
            }
        }
    }
        // Bottom bar with action buttons
        BottomAppBar(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { showDeleteDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete")
                }
                Button(
                    onClick = onShare,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share")
                }
            }
        }
    }
    // Highlights dialog
    if (showHighlightsDialog) {
        AlertDialog(
            onDismissRequest = { showHighlightsDialog = false },
            title = { Text("Highlights") },
            text = {
                Column {
                    highlightTimes.forEach { t ->
                        val min = TimeUnit.MILLISECONDS.toMinutes(t)
                        val sec = TimeUnit.MILLISECONDS.toSeconds(t) % 60
                        TextButton(onClick = {
                            exoPlayer.seekTo(t)
                            showHighlightsDialog = false
                        }) {
                            Text(String.format("%02d:%02d", min, sec))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHighlightsDialog = false }) { Text("Close") }
            }
        )
    }
    
    // Highlight clips dialog
    if (showHighlightClipsDialog) {
        AlertDialog(
            onDismissRequest = { showHighlightClipsDialog = false },
            title = { Text("Highlight Clips") },
            text = {
                Column {
                    highlightClips.forEach { clipFile ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = clipFile.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Row {
                                TextButton(
                                    onClick = {
                                        // Preview clip
                                        val intent = Intent(context, PlaybackActivity::class.java).apply {
                                            putExtra("video_uri", clipFile.absolutePath)
                                        }
                                        context.startActivity(intent)
                                    }
                                ) {
                                    Text("Preview")
                                }
                                TextButton(
                                    onClick = {
                                        // Share clip
                                        val uri = androidx.core.content.FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.provider",
                                            clipFile
                                        )
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "video/*"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Highlight Clip"))
                                    }
                                ) {
                                    Text("Share")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHighlightClipsDialog = false }) { Text("Close") }
            }
        )
    }
    
    // Batch share clips dialog
    if (showBatchShareDialog) {
        AlertDialog(
            onDismissRequest = { 
                showBatchShareDialog = false 
                selectedClips = emptySet()
            },
            title = { Text("Select Clips to Share") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    LazyColumn {
                        items(highlightClips) { clipFile ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedClips = if (selectedClips.contains(clipFile)) {
                                            selectedClips - clipFile
                                        } else {
                                            selectedClips + clipFile
                                        }
                                    }
                                    .padding(vertical = 8.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (selectedClips.contains(clipFile)) {
                                        Icons.Default.CheckBox
                                    } else {
                                        Icons.Default.CheckBoxOutlineBlank
                                    },
                                    contentDescription = if (selectedClips.contains(clipFile)) "Selected" else "Not selected",
                                    tint = if (selectedClips.contains(clipFile)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                                Text(
                                    text = clipFile.name,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    if (highlightClips.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(
                                onClick = {
                                    selectedClips = if (selectedClips.size == highlightClips.size) {
                                        emptySet()
                                    } else {
                                        highlightClips.toSet()
                                    }
                                }
                            ) {
                                Text(if (selectedClips.size == highlightClips.size) "Deselect All" else "Select All")
                            }
                            Text(
                                text = "${selectedClips.size}/${highlightClips.size} selected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = { 
                            showBatchShareDialog = false 
                            selectedClips = emptySet()
                        }
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (selectedClips.isNotEmpty()) {
                                // Share selected clips
                                val clipUris = selectedClips.map { clipFile ->
                                    androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.provider",
                                        clipFile
                                    )
                                }
                                val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                    type = "video/*"
                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(clipUris))
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Highlight Clips"))
                                showBatchShareDialog = false
                                selectedClips = emptySet()
                            } else {
                                // Show toast for no selection
                                android.widget.Toast.makeText(context, "Please select clips to share", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = selectedClips.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedClips.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text("Share Selected (${selectedClips.size})")
                    }
                }
            }
        )
    }
    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Recording") },
            text = { Text("Are you sure you want to delete this recording? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}