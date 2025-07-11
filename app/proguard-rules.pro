# Add project specific ProGuard rules here.
# You can control the set of set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# FFmpeg Kit ProGuard Rules
-keep class com.arthenica.ffmpegkit.** { *; }
-dontwarn com.arthenica.ffmpegkit.**

# ExoPlayer ProGuard Rules
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**

# WorkManager ProGuard Rules
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# Jetpack Compose ProGuard Rules
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Navigation Compose ProGuard Rules
-keep class androidx.navigation.** { *; }
-dontwarn androidx.navigation.**

# DataStore ProGuard Rules
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# AdMob ProGuard Rules
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# Firebase ProGuard Rules
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Play Services ProGuard Rules
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# App Update ProGuard Rules
-keep class com.google.android.play.** { *; }
-dontwarn com.google.android.play.**

# Kotlin Coroutines ProGuard Rules
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Preserve native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Preserve enum values
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Preserve Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Preserve Serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Preserve R classes
-keep class **.R$* {
    public static <fields>;
}

# Preserve custom application class
-keep class com.ibbie.catrec_gamingscreenrecorder.CatRecApp { *; }

# Preserve service classes
-keep class com.ibbie.catrec_gamingscreenrecorder.ScreenRecorderService { *; }
-keep class com.ibbie.catrec_gamingscreenrecorder.overlay.RecordingOverlay { *; }

# Preserve worker classes
-keep class com.ibbie.catrec_gamingscreenrecorder.*Worker { *; }

# Preserve activity classes
-keep class com.ibbie.catrec_gamingscreenrecorder.MainActivity { *; }
-keep class com.ibbie.catrec_gamingscreenrecorder.PlaybackActivity { *; }

# Preserve receiver classes
-keep class com.ibbie.catrec_gamingscreenrecorder.StopRecordingReceiver { *; }

# Preserve audio recorder classes
-keep class com.ibbie.catrec_gamingscreenrecorder.audio.** { *; }
-keep class com.ibbie.catrec_gamingscreenrecorder.InternalAudioRecorder { *; }

# Preserve UI theme classes
-keep class com.ibbie.catrec_gamingscreenrecorder.ui.theme.** { *; }

# Preserve screen classes
-keep class com.ibbie.catrec_gamingscreenrecorder.ui.screens.** { *; }

# Preserve overlay classes
-keep class com.ibbie.catrec_gamingscreenrecorder.ui.** { *; }

# Preserve utility classes
-keep class com.ibbie.catrec_gamingscreenrecorder.AnalyticsManager { *; }
-keep class com.ibbie.catrec_gamingscreenrecorder.SettingsDataStore { *; }
-keep class com.ibbie.catrec_gamingscreenrecorder.UpdateManager { *; }