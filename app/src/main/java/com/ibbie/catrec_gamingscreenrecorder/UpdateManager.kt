package com.ibbie.catrec_gamingscreenrecorder

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.util.Log
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class UpdateManager(private val context: Context) {
    
    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(context)
    private val analyticsManager = AnalyticsManager(context)
    
    companion object {
        private const val TAG = "UpdateManager"
        private const val UPDATE_REQUEST_CODE = 500
    }
    
    /**
     * Check for app updates and show update dialog if available
     * @param activity The activity to show the update dialog from
     * @param forceCheck Whether to force check for updates (ignoring network/analytics settings)
     */
    suspend fun checkForUpdates(activity: Activity, forceCheck: Boolean = false) {
        try {
            // Check if auto update check is enabled, analytics is enabled, and network is available
            if (!forceCheck) {
                val autoUpdateCheckEnabled = withContext(Dispatchers.IO) {
                    SettingsDataStore(context).autoUpdateCheckEnabled.first()
                }
                if (!autoUpdateCheckEnabled) {
                    Log.d(TAG, "Update check skipped - auto update check disabled")
                    return
                }
                
                val analyticsEnabled = withContext(Dispatchers.IO) {
                    SettingsDataStore(context).analyticsEnabled.first()
                }
                if (!analyticsEnabled) {
                    Log.d(TAG, "Update check skipped - analytics disabled")
                    return
                }
                
                if (!isNetworkAvailable(context)) {
                    Log.d(TAG, "Update check skipped - no network available")
                    return
                }
            }
            
            Log.d(TAG, "Checking for app updates...")
            
            val appUpdateInfo = appUpdateManager.appUpdateInfo.await()
            
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                
                Log.d(TAG, "Update available: ${appUpdateInfo.availableVersionCode()}")
                
                // Log update availability if analytics enabled
                withContext(Dispatchers.IO) {
                    val analyticsEnabled = SettingsDataStore(context).analyticsEnabled.first()
                    if (analyticsEnabled) {
                        val availableVersion = appUpdateInfo.availableVersionCode().toString()
                        val currentVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toString()
                        val params = mapOf<String, String>(
                            "available_version" to availableVersion,
                            "current_version" to currentVersion
                        )
                        analyticsManager.logEvent("update_available", params)
                    }
                }
                
                // Show update dialog
                showUpdateDialog(activity, appUpdateInfo)
                
            } else {
                Log.d(TAG, "No update available or update type not allowed")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            
            // Log error if analytics enabled
            withContext(Dispatchers.IO) {
                val analyticsEnabled = SettingsDataStore(context).analyticsEnabled.first()
                if (analyticsEnabled) {
                    val errorParams = mapOf<String, String>(
                        "error" to (e.message ?: "Unknown error")
                    )
                    analyticsManager.logEvent("update_check_error", errorParams)
                }
            }
        }
    }
    
    /**
     * Show update dialog to user
     */
    private fun showUpdateDialog(activity: Activity, appUpdateInfo: AppUpdateInfo) {
        val updateOptions = AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE)
            .setAllowAssetPackDeletion(true)
            .build()
        
        try {
            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo,
                activity,
                updateOptions,
                UPDATE_REQUEST_CODE
            )
        } catch (e: IntentSender.SendIntentException) {
            Log.e(TAG, "Error starting update flow", e)
        }
    }
    
    /**
     * Handle update result from activity
     */
    fun handleUpdateResult(requestCode: Int, resultCode: Int) {
        if (requestCode == UPDATE_REQUEST_CODE) {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    Log.d(TAG, "Update flow started successfully")
                    // Log update started if analytics enabled
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                        val analyticsEnabled = SettingsDataStore(context).analyticsEnabled.first()
                        if (analyticsEnabled) {
                            analyticsManager.logEvent("update_started")
                        }
                    }
                }
                Activity.RESULT_CANCELED -> {
                    Log.d(TAG, "Update flow cancelled by user")
                    // Log update cancelled if analytics enabled
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                        val analyticsEnabled = SettingsDataStore(context).analyticsEnabled.first()
                        if (analyticsEnabled) {
                            analyticsManager.logEvent("update_cancelled")
                        }
                    }
                }
                else -> {
                    Log.d(TAG, "Update flow failed with result code: $resultCode")
                }
            }
        }
    }
    
    /**
     * Set up install state listener to track update progress
     */
    fun setupInstallStateListener() {
        val listener = InstallStateUpdatedListener { state ->
            when (state.installStatus()) {
                InstallStatus.DOWNLOADING -> {
                    val bytesDownloaded = state.bytesDownloaded()
                    val totalBytesToDownload = state.totalBytesToDownload()
                    val progress = if (totalBytesToDownload > 0) {
                        (bytesDownloaded * 100 / totalBytesToDownload).toInt()
                    } else 0
                    Log.d(TAG, "Update downloading: $progress%")
                }
                InstallStatus.DOWNLOADED -> {
                    Log.d(TAG, "Update downloaded successfully")
                    // Log update downloaded if analytics enabled
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                        val analyticsEnabled = SettingsDataStore(context).analyticsEnabled.first()
                        if (analyticsEnabled) {
                            analyticsManager.logEvent("update_downloaded")
                        }
                    }
                }
                InstallStatus.INSTALLED -> {
                    Log.d(TAG, "Update installed successfully")
                    // Log update installed if analytics enabled
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                        val analyticsEnabled = SettingsDataStore(context).analyticsEnabled.first()
                        if (analyticsEnabled) {
                            analyticsManager.logEvent("update_installed")
                        }
                    }
                }
                InstallStatus.FAILED -> {
                    Log.e(TAG, "Update failed: ${state.installErrorCode()}")
                    // Log update failed if analytics enabled
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                        val analyticsEnabled = SettingsDataStore(context).analyticsEnabled.first()
                        if (analyticsEnabled) {
                            val failedParams = mapOf<String, String>(
                                "error_code" to state.installErrorCode().toString()
                            )
                            analyticsManager.logEvent("update_failed", failedParams)
                        }
                    }
                }
                else -> {
                    Log.d(TAG, "Update status: ${state.installStatus()}")
                }
            }
        }
        
        appUpdateManager.registerListener(listener)
    }
    
    /**
     * Check if network is available
     */
    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return networkCapabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    
    /**
     * Complete update installation (called when app is ready to restart)
     */
    fun completeUpdate() {
        appUpdateManager.completeUpdate()
    }
} 