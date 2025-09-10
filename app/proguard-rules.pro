# ReceiptScanner ProGuard Configuration
# Security-focused obfuscation rules for production builds

# Keep debugging information for crash reports (disable for maximum security)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# =============================================================================
# ANDROID & ANDROIDX RULES
# =============================================================================

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelables
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep view constructors
-keepclasseswithmembernames class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembernames class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# =============================================================================
# HILT/DAGGER RULES
# =============================================================================

# Keep Hilt generated classes
-keep class dagger.hilt.android.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ApplicationComponentManager { *; }
-keep class **_HiltModules** { *; }
-keep class **_Provide** { *; }
-keep class **_MembersInjector { *; }

# Keep Hilt entry points
-keep @dagger.hilt.android.HiltAndroidApp class *
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }

# =============================================================================
# ROOM DATABASE RULES
# =============================================================================

# Keep Room entities and DAOs
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# Keep Room generated classes
-keep class **_Impl { *; }
-keepclassmembers class **_Impl {
    *;
}

# =============================================================================
# RETROFIT & NETWORKING RULES
# =============================================================================

# Keep Retrofit interfaces and data classes
-keep interface * {
    @retrofit2.http.* <methods>;
}

# Keep Gson/JSON serialization classes
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep data classes used for serialization
-keep class com.pharadev.receiptscanner.data.network.** { *; }

# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-dontwarn okio.**

# =============================================================================
# ML KIT & CAMERA RULES
# =============================================================================

# Keep ML Kit classes
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Keep CameraX classes
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# =============================================================================
# SECURITY RULES
# =============================================================================

# Keep security classes (but obfuscate internals)
-keep class com.pharadev.receiptscanner.security.KeystoreManager {
    public <methods>;
}
-keep class com.pharadev.receiptscanner.security.NetworkSecurityHelper {
    public <methods>;
}

# Keep encryption data classes
-keep class com.pharadev.receiptscanner.security.EncryptedData { *; }

# =============================================================================
# APPLICATION SPECIFIC RULES
# =============================================================================

# Keep Application class
-keep class com.pharadev.receiptscanner.ReceiptScannerApplication { *; }

# Keep domain models (for database consistency)
-keep class com.pharadev.receiptscanner.domain.model.** { *; }

# Keep database entities structure
-keep class com.pharadev.receiptscanner.data.database.entities.** { *; }

# =============================================================================
# OBFUSCATION CONFIGURATION
# =============================================================================

# Enable aggressive obfuscation
-overloadaggressively
-allowaccessmodification
-repackageclasses 'obfuscated'

# Remove logging in release builds (security measure)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# Remove debug information and unused code
-dontwarn **
-ignorewarnings