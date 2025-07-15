package com.ibbie.catrec_gamingscreenrecorder.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Square
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ibbie.catrec_gamingscreenrecorder.R
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.AdListener
import androidx.compose.ui.viewinterop.AndroidView
import com.ibbie.catrec_gamingscreenrecorder.AnalyticsManager
import com.ibbie.catrec_gamingscreenrecorder.AdManager
import android.app.Activity
import android.widget.Toast
import kotlinx.coroutines.launch

@Composable
fun SupportScreen(
    darkTheme: Boolean,
    onStartRecording: (Boolean, Boolean) -> Unit
) {

    val context = LocalContext.current
    val isRecording = remember { mutableStateOf(false) }
    val analyticsManager = remember { AnalyticsManager(context) }
    val scope = rememberCoroutineScope()
    
    // Check service status periodically
    LaunchedEffect(Unit) {
        while (true) {
            val serviceRunning = isServiceRunning(context)
            isRecording.value = serviceRunning
            kotlinx.coroutines.delay(1000)
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            // App icon
            Image(
                painter = painterResource(id = R.drawable.catrec_icon),
                contentDescription = "CatRec Icon",
                modifier = Modifier.size(120.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Version text
            Text(
                text = "CatRec Version 1.0",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = if (darkTheme) Color.White else Color.Black
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Support options
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Watch an ad to support me
                var adLoading by remember { mutableStateOf(false) }
                Button(
                    onClick = {
                        adLoading = true
                        AdManager.loadRewardedAd(
                            context = context,
                            onLoaded = {
                                adLoading = false
                                AdManager.showRewardedAd(
                                    activity = context as Activity,
                                    onReward = {
                                        Toast.makeText(context, "Reward earned!", Toast.LENGTH_SHORT).show()
                                    },
                                    onClosed = {},
                                    onFailed = {
                                        Toast.makeText(context, "Ad failed to show", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            },
                            onFailed = {
                                adLoading = false
                                Toast.makeText(context, "Failed to load ad: $it", Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !adLoading
                ) {
                    if (adLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text(
                            "Watch an ad to support me",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // Share this app
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Check out CatRec - A great screen recording app!")
                        }
                        context.startActivity(Intent.createChooser(intent, "Share CatRec"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Share this app",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Subscribe to YouTube channel
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/@ibbie"))
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Subscribe to my YouTube channel",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun isServiceRunning(context: Context): Boolean {
    val prefs = context.getSharedPreferences("service_state", Context.MODE_PRIVATE)
    return prefs.getBoolean("is_recording", false)
} 