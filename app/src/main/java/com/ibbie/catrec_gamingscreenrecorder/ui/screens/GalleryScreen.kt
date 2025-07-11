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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity
import com.ibbie.catrec_gamingscreenrecorder.PlaybackActivity
import java.io.File
import android.graphics.BitmapFactory
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val recordingsDir = context.getExternalFilesDir(null)
    var galleryItems by remember { mutableStateOf(listOf<GalleryItem>()) }

    LaunchedEffect(Unit) {
        val files = recordingsDir?.listFiles()?.filter { it.name.endsWith("_with_audio.mp4") }?.sortedByDescending { it.lastModified() } ?: emptyList()
        val items = files.map { videoFile ->
            val thumbFile = File(videoFile.parent, videoFile.nameWithoutExtension + "_thumbnail.png")
            GalleryItem(
                videoFile = videoFile,
                thumbnailFile = thumbFile.takeIf { it.exists() },
                date = Date(videoFile.lastModified()),
                filename = videoFile.name
            )
        }
        galleryItems = items
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recordings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (galleryItems.isEmpty()) {
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
                items(galleryItems) { item ->
                    GalleryThumbnail(item = item, onClick = {
                        val intent = Intent(context, PlaybackActivity::class.java).apply {
                            putExtra("video_uri", item.videoFile.absolutePath)
                        }
                        startActivity(context, intent, null)
                    })
                }
            }
        }
    }
}

data class GalleryItem(
    val videoFile: File,
    val thumbnailFile: File?,
    val date: Date,
    val filename: String
)

@Composable
fun GalleryThumbnail(item: GalleryItem, onClick: () -> Unit) {
    val thumbBitmap = remember(item.thumbnailFile) {
        item.thumbnailFile?.let {
            try {
                BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap()
            } catch (_: Exception) { null }
        }
    }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    Column(
        modifier = Modifier
            .width(128.dp)
            .clickable { onClick() }
            .background(MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (thumbBitmap != null) {
            Image(
                bitmap = thumbBitmap,
                contentDescription = "Recording thumbnail",
                modifier = Modifier.size(96.dp)
            )
        } else {
            Box(
                modifier = Modifier.size(96.dp).background(Color.DarkGray, shape = MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center
            ) {
                Text("No Preview", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.filename,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            text = dateFormat.format(item.date),
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
        )
    }
} 