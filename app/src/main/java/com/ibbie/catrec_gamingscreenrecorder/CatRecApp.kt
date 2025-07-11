package com.ibbie.catrec_gamingscreenrecorder

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CatRecApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val settingsDataStore = SettingsDataStore(this)
        CoroutineScope(Dispatchers.IO).launch {
            val analyticsEnabled = settingsDataStore.analyticsEnabled.first()
            val crashReportingEnabled = settingsDataStore.crashReportingEnabled.first()
            
            FirebaseApp.initializeApp(this@CatRecApp)
            // Crashlytics tied to crash reporting consent for privacy
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(crashReportingEnabled)
            FirebaseAnalytics.getInstance(this@CatRecApp).setAnalyticsCollectionEnabled(analyticsEnabled)
            
            // Set user properties for crash reports
            if (crashReportingEnabled) {
                val versionName = packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
                FirebaseCrashlytics.getInstance().setCustomKey("app_version", versionName)
                FirebaseCrashlytics.getInstance().setCustomKey("build_type", "release")
            }
        }
    }
} 