# libsignal JNI bridge - CRITICAL
-keep class org.signal.libsignal.** { *; }
-keepclassmembers class org.signal.libsignal.** { *; }
-dontwarn org.signal.libsignal.**

# Own crypto layer - Hilt injects these
-keep class com.offnetic.data.crypto.** { *; }
-keep class com.offnetic.data.crypto.SignalProtocolStoreImpl { *; }
-keepclassmembers class com.offnetic.data.crypto.** { *; }
-keep class com.offnetic.data.nearby.** { *; }
-keep class com.offnetic.data.local.** { *; }
-keep class com.offnetic.data.repository.** { *; }
-keep class com.offnetic.domain.** { *; }
-keep class com.offnetic.di.** { *; }
-keep class com.offnetic.ui.** { *; }
-keep class com.offnetic.service.** { *; }

# Hilt - must not be stripped
-keep class dagger.hilt.** { *; }
-keep class com.google.dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep class * extends dagger.hilt.android.internal.managers.ActivityComponentManager { *; }
-keep class * extends dagger.hilt.android.internal.managers.ServiceComponentManager { *; }
-keepattributes *Annotation*
-keep class javax.inject.** { *; }

# SQLCipher
-keep class net.sqlcipher.** { *; }

# DataStore
-keep class androidx.datastore.** { *; }

# Nearby Connections
-keep class com.google.android.gms.nearby.** { *; }

# WebRTC
-keep class org.webrtc.** { *; }

# CameraX
-keep class androidx.camera.** { *; }

# ML Kit
-keep class com.google.mlkit.** { *; }

# Biometric
-keep class androidx.biometric.** { *; }

# Media3
-keep class androidx.media3.** { *; }

# Timber
-keep class timber.log.** { *; }

# Kotlin Coroutines
-keep class kotlinx.coroutines.** { *; }

# Prevent obfuscation of Parcelable/Serializable
-keep class * implements android.os.Parcelable { *; }
-keep class * implements java.io.Serializable { *; }

# Keep enum values
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}