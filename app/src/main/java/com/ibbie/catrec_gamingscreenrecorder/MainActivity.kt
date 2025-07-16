package com.ibbie.catrec_gamingscreenrecorder

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ibbie.catrec_gamingscreenrecorder.ui.screens.RecordingScreen
import com.ibbie.catrec_gamingscreenrecorder.ui.screens.SettingsScreen
import com.ibbie.catrec_gamingscreenrecorder.ui.screens.SupportScreen
import com.ibbie.catrec_gamingscreenrecorder.ui.theme.CatRecTheme
import androidx.compose.ui.Alignment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.saveable.rememberSaveable
import android.content.BroadcastReceiver
import android.content.IntentFilter
import com.ibbie.catrec_gamingscreenrecorder.ui.CountdownOverlay
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import com.google.android.gms.ads.MobileAds
import com.google.firebase.FirebaseApp
import kotlin.OptIn
import overlay.RecordingOverlay
import android.util.DisplayMetrics

class MainActivity : ComponentActivity() {

    private var projectionIntent: Intent? = null
    private var projectionResultCode: Int = 0
    private var recordInternal: Boolean = true
    private var recordMic: Boolean = true
    private var orientation: String = "Auto"
    private var isDarkTheme: Boolean = true
    private var permissionsRequested = false

    // Snackbar state for permission denial
    private val snackbarHostState = SnackbarHostState()

    // Broadcast receiver to clear projection data when recording stops
    private val recordingStoppedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ScreenRecorderService.ACTION_RECORDING_STOPPED) {
                clearProjectionData()
            }
        }
    }

    private var countdownOverlay: CountdownOverlay? = null
    private val settingsDataStore by lazy { SettingsDataStore(this) }
    private val analyticsManager by lazy { AnalyticsManager(this) }
    private val updateManager by lazy { UpdateManager(this) }
    private var recordingOverlay: RecordingOverlay? = null

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            projectionIntent = result.data
            projectionResultCode = result.resultCode
            showCountdownAndStartService()
        }
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, proceed with recording
            requestCapturePermission(recordMic, recordInternal)
        } else {
            val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)
            if (!shouldShowRationale) {
                // Denied permanently - show enhanced snackbar with settings action
                showMicDeniedSnackbar(permanentlyDenied = true)
                recordMic = false
                requestCapturePermission(false, recordInternal)
            } else {
                // Denied temporarily - show snackbar with try again option
                showMicDeniedSnackbar(permanentlyDenied = false)
                recordMic = false
                requestCapturePermission(false, recordInternal)
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Log.w("MainActivity", "Notification permission denied")
        }
    }

    // Remove the deprecated onActivityResult method and replace with Activity Result API
    // 1. Define a property at the top of the class:
    private lateinit var startForResult: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        
        // Initialize MobileAds
        MobileAds.initialize(this) {}
        
        // Suppress FFmpeg deprecation warnings
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            android.util.Log.w("MainActivity", "Suppressing FFmpeg deprecation warnings")
        }
        
        createNotificationChannel()
        
        // Register broadcast receiver for recording stopped events
        registerReceiver(recordingStoppedReceiver, IntentFilter(ScreenRecorderService.ACTION_RECORDING_STOPPED), Context.RECEIVER_NOT_EXPORTED)
        
        // Clear any stale projection data on app start
        clearProjectionData()
        
        // Request permissions on app launch
        requestInitialPermissions()
        
        // Handle start recording from notification or service
        if (intent?.action == "START_RECORDING") {
            // Check if we already have projection data
            if (projectionIntent != null && projectionResultCode != 0) {
                // We have valid projection data, start recording immediately
                showCountdownAndStartService()
            } else {
                // No projection data, request new permission
                requestCapturePermission(true, true)
            }
        }
        
        setContent {
            var isDarkTheme by rememberSaveable { mutableStateOf(this.isDarkTheme) }
            val snackbarHostState = remember { this@MainActivity.snackbarHostState }
            val coroutineScope = rememberCoroutineScope()
            var showMicRationale by remember { mutableStateOf(false) }
            var pendingMicRequest by remember { mutableStateOf(false) }
            var rationaleRequested by remember { mutableStateOf(false) }
            var showStorageDialog by remember { mutableStateOf(false) }
            var proceedWithLowStorage by remember { mutableStateOf(false) }

            // Overlay observer
            val overlayEnabledState = produceState(initialValue = false) {
                value = settingsDataStore.overlayEnabled.first()
                settingsDataStore.overlayEnabled.collect { enabled ->
                    value = enabled
                    runOnUiThread {
                        if (enabled) {
                            if (recordingOverlay == null) {
                                recordingOverlay = RecordingOverlay(this@MainActivity)
                                recordingOverlay?.show()
                            }
                        } else {
                            recordingOverlay?.hide()
                            recordingOverlay = null
                        }
                    }
                }
            }
            
            CatRecTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CatRecApp(
                            onStartRecording = { recordMic: Boolean, recordInternal: Boolean, orientation: String ->
                                pendingMicRequest = recordMic
                                rationaleRequested = false
                                requestCapturePermission(recordMic, recordInternal, coroutineScope, {
                                    showMicRationale = true
                                }, rationaleRequested, orientation)
                            },
                            onThemeChange = { darkTheme: Boolean ->
                                isDarkTheme = darkTheme
                            },
                            darkTheme = isDarkTheme
                        )
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier.align(Alignment.BottomCenter),
                            snackbar = { snackbarData ->
                                Snackbar(
                                    action = {
                                        snackbarData.visuals.actionLabel?.let {
                                            TextButton(onClick = {
                                                when (it) {
                                                    "Go to Settings" -> {
                                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                            data = Uri.fromParts("package", packageName, null)
                                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        }
                                                        startActivity(intent)
                                                    }
                                                    "Try Again" -> {
                                                        rationaleRequested = false
                                                        requestCapturePermission(pendingMicRequest, this@MainActivity.recordInternal, coroutineScope, {
                                                            showMicRationale = true
                                                        }, rationaleRequested)
                                                    }
                                                }
                                            }) {
                                                Text(it, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                ) {
                                    Text(snackbarData.visuals.message)
                                }
                            }
                        )
                        if (showMicRationale) {
                            AlertDialog(
                                onDismissRequest = { showMicRationale = false },
                                title = { Text("Microphone Access Needed") },
                                text = { Text("To record your voice, CatRec needs access to your microphone. You can still record internal audio without it.") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showMicRationale = false
                                        rationaleRequested = true
                                        requestMicPermissionWithRationale(true)
                                    }) { Text("Allow") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showMicRationale = false }) { Text("Not Now") }
                                }
                            )
                        }
                        if (showStorageDialog) {
                            AlertDialog(
                                onDismissRequest = { showStorageDialog = false },
                                title = { Text("Low Storage Space") },
                                text = { Text("There may not be enough storage space to record. Please free up space or proceed at your own risk.") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showStorageDialog = false
                                        proceedWithLowStorage = true
                                        showCountdownAndStartService(true)
                                    }) { Text("Record Anyway") }
                                },
                                dismissButton = {
                                    TextButton(onClick = {
                                        showStorageDialog = false
                                        proceedWithLowStorage = false
                                    }) { Text("Cancel Recording") }
                                }
                            )
                        }
                    }
                }
            }
        }
        AutoDeleteWorker.schedule(this)
        CoroutineScope(Dispatchers.IO).launch {
            analyticsManager.logSessionStart()
        }
        
        // Set up update manager and check for updates
        updateManager.setupInstallStateListener()
        CoroutineScope(Dispatchers.IO).launch {
            // Only check for updates if auto update check is enabled
            val autoUpdateCheckEnabled = settingsDataStore.autoUpdateCheckEnabled.first()
            if (autoUpdateCheckEnabled) {
                updateManager.checkForUpdates(this@MainActivity)
            }
        }

        // 2. In onCreate or init block, initialize the launcher:
        startForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                // Handle the result here
            }
        }
    }

    private fun requestInitialPermissions() {
        if (permissionsRequested) return
        permissionsRequested = true
        
        // Only request overlay permission on first launch
        if (!checkOverlayPermission()) {
            requestOverlayPermission()
        }
        
        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        // DO NOT request microphone permission here - it will be requested when needed
    }

    @Composable
    fun CatRecApp(
        onStartRecording: (Boolean, Boolean, String) -> Unit,
        onThemeChange: (Boolean) -> Unit,
        darkTheme: Boolean
    ) {
        val navController = rememberNavController()
        
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = if (darkTheme) Color.Black else Color.White,
                    contentColor = Color(0xFFD32F2F) // Red color for icons
                ) {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination
                    
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Home, contentDescription = "Recording") },
                        label = { Text("Recording", fontWeight = FontWeight.Bold) },
                        selected = currentDestination?.hierarchy?.any { it.route == "recording" } == true,
                        onClick = {
                            navController.navigate("recording") {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                        label = { Text("Settings", fontWeight = FontWeight.Bold) },
                        selected = currentDestination?.hierarchy?.any { it.route == "settings" } == true,
                        onClick = {
                            navController.navigate("settings") {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    
                    NavigationBarItem(
                        icon = { Text("🐱", style = MaterialTheme.typography.titleLarge) },
                        label = { Text("Support", fontWeight = FontWeight.Bold) },
                        selected = currentDestination?.hierarchy?.any { it.route == "support" } == true,
                        onClick = {
                            navController.navigate("support") {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        ) { paddingValues ->
            NavHost(
                navController = navController, 
                startDestination = "recording",
                modifier = Modifier.padding(paddingValues)
            ) {
                composable("recording") {
                    RecordingScreen(
                        onStartRecording = onStartRecording,
                        darkTheme = darkTheme
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        darkTheme = darkTheme,
                        onThemeChange = onThemeChange
                    )
                }
                composable("scheduling") {
                    com.ibbie.catrec_gamingscreenrecorder.ui.screens.SchedulingScreen(
                        navController = navController,
                        darkTheme = darkTheme
                    )
                }
                composable("support") {
                    SupportScreen(
                        darkTheme = darkTheme,
                        onStartRecording = { mic, internal -> onStartRecording(mic, internal, "Auto") }
                    )
                }
            }
        }
    }

    private fun requestMicPermissionWithRationale(shouldRequest: Boolean) {
        if (shouldRequest) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun requestCapturePermission(
        recordMic: Boolean,
        recordInternal: Boolean,
        coroutineScope: CoroutineScope? = null,
        onRationale: (() -> Unit)? = null,
        rationaleRequested: Boolean = false,
        orientation: String = "Auto"
    ) {
        coroutineScope?.launch {
            val settings = settingsDataStore.settingsFlow.first()
            val resolution = settings.resolution
            val (width, height) = if (resolution == "Native") {
                val metrics = resources.displayMetrics
                metrics.widthPixels to metrics.heightPixels
            } else {
                val parts = resolution.split("x")
                if (parts.size == 2) parts[0].toInt() to parts[1].toInt() else 1280 to 720
            }
            var shouldRecordMic = recordMic
            this@MainActivity.recordInternal = recordInternal
            
            // Request microphone permission only when user tries to record with mic
            if (shouldRecordMic) {
                if (!isMicrophoneAvailable()) {
                    shouldRecordMic = false
                } else if (!hasMicrophonePermission()) {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this@MainActivity, Manifest.permission.RECORD_AUDIO) && !rationaleRequested) {
                        onRationale?.invoke()
                    } else {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    return@launch
                }
            }
            
            this@MainActivity.recordMic = shouldRecordMic
            this@MainActivity.orientation = orientation
            
            // Request media projection permission only when starting recording
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
        }
    }

    private fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isMicrophoneAvailable(): Boolean {
        return packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
    }

    private fun showCountdownAndStartService(forceProceed: Boolean = false) {
        if (checkOverlayPermission()) {
            // Check available storage before countdown/recording
            val filesDir = getExternalFilesDir(null)
            val stat = StatFs(filesDir?.absolutePath ?: Environment.getDataDirectory().absolutePath)
            val bytesAvailable = stat.availableBytes
            val minRequired = 500L * 1024 * 1024 // 500 MB
            if (!forceProceed && bytesAvailable < minRequired) {
                Log.w("MainActivity", "Low storage space detected")
                return
            }
            // Read countdown duration from settings
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                val countdown = settingsDataStore.countdown.first()
                if (countdown <= 0) {
                    startRecordingService(orientation)
                } else {
                    if (countdownOverlay == null) {
                        countdownOverlay = CountdownOverlay(this@MainActivity)
                    }
                    countdownOverlay?.showCountdown(countdown) {
                            startRecordingService(orientation)
                    }
                }
            }
        } else {
            requestOverlayPermission()
        }
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun startRecordingService(orientation: String) {
        projectionIntent?.let { data ->
            val intent = Intent(this, ScreenRecorderService::class.java).apply {
                action = "START_RECORDING"
                putExtra("resultCode", projectionResultCode)
                putExtra("data", projectionIntent)
                putExtra("width", 1280) // Default width
                putExtra("height", 720) // Default height
                putExtra("density", resources.displayMetrics.densityDpi)
                putExtra("bitrate", 1000000) // Default bitrate
                putExtra("orientation", orientation)
            }
                startForegroundService(intent)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ScreenRecorderService.CHANNEL_ID,
                "Screen Recorder",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun showMicDeniedSnackbar(permanentlyDenied: Boolean) {
        val message = if (permanentlyDenied) {
            "Microphone access denied. Recording will continue with internal audio only. You can enable microphone access in Settings."
        } else {
            "Microphone permission denied. Recording will continue with internal audio only."
        }
        val actionLabel = if (permanentlyDenied) "Go to Settings" else "Try Again"
        
        CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                duration = SnackbarDuration.Long
            )
        }
    }

    private fun clearProjectionData() {
        projectionIntent = null
        projectionResultCode = 0
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(recordingStoppedReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered, ignore
        }
        CoroutineScope(Dispatchers.IO).launch {
            analyticsManager.logSessionEnd()
        }
    }
}