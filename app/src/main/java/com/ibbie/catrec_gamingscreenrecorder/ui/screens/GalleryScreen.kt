package com.ibbie.catrec_gamingscreenrecorder.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ibbie.catrec_gamingscreenrecorder.PlaybackActivity
import java.io.File
import android.graphics.BitmapFactory
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.PlayArrow
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.launch
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val recordings = remember { mutableStateListOf<GalleryItem>() }
    val sortByNewest = remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        loadGalleryRecordings(context, recordings, sortByNewest.value)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recordings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (recordings.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No recordings found.", color = Color.Gray)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(128.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(recordings) { item ->
                    GalleryThumbnail(item = item, onClick = {
                        val intent = Intent(context, PlaybackActivity::class.java).apply {
                            putExtra("video_uri", item.file.absolutePath)
                        }
                        context.startActivity(intent, null)
                    })
                }
            }
        }
    }
}

fun loadGalleryRecordings(context: Context, recordings: MutableList<GalleryItem>, sortByNewest: Boolean) {
    GlobalScope.launch(Dispatchers.IO) {
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
            
            val items = sortedFiles.map { videoFile ->
                GalleryItem(
                    file = videoFile,
                    thumbnailFile = null // We'll generate thumbnails from video frames
                )
            }
            
            withContext(Dispatchers.Main) {
                recordings.clear()
                recordings.addAll(items)
            }
        } catch (e: Exception) {
            Log.e("GalleryScreen", "Error loading recordings", e)
        }
    }
}

data class GalleryItem(
    val file: File,
    val thumbnailFile: File?
)

@Composable
fun GalleryThumbnail(item: GalleryItem, onClick: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    LaunchedEffect(item.file) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(item.file.absolutePath)
                
                // Get first frame instead of looking for thumbnail file
                val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    withContext(Dispatchers.Main) {
                        bitmap = frame
                    }
                }
                retriever.release()
            } catch (e: Exception) {
                Log.e("GalleryThumbnail", "Error loading thumbnail", e)
            }
        }
    }
    
    Box(
        modifier = Modifier
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.medium)
            .background(Color.DarkGray)
            .clickable { onClick() }
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Recording thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        
        // Overlay info (resolution, size, duration, timestamp)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        ) {
            Text(
                text = "Recording at " + SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(item.file.lastModified())),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = readableFileSize(item.file.length()),
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
                // TODO: Show duration and resolution if available
            }
        }
        
        // Play button overlay
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

fun readableFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var i = 0
    var currentSize = size.toDouble()
    while (currentSize >= 1024 && i < units.size - 1) {
        currentSize /= 1024
        i++
    }
    return String.format("%.1f %s", currentSize, units[i])
} 