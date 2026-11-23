# Core Libraries Integration Documentation

## Overview
This document details the core libraries integrated as part of MVP task 1.2 - Core Dependencies & Libraries Setup.

## Integrated Libraries

### 1. Google ML Kit for OCR (Text Recognition API)

**Version:** `16.0.0`  
**Gradle Dependency:**
```kotlin
implementation(libs.google.mlkit.text.recognition)
```

**Features:**
- On-device text recognition
- High accuracy for receipt text extraction
- Multiple language support
- Real-time processing capabilities

**Integration Benefits:**
- ✅ **Privacy-focused**: All processing happens on-device
- ✅ **Performance**: Fast text recognition without network calls
- ✅ **Offline capability**: Works without internet connection
- ✅ **Cost-effective**: No per-API-call charges

### 2. CameraX for Camera Functionality

**Version:** `1.3.1`  
**Gradle Dependencies:**
```kotlin
implementation(libs.androidx.camera.core)
implementation(libs.androidx.camera.camera2)
implementation(libs.androidx.camera.lifecycle)
implementation(libs.androidx.camera.view)
implementation(libs.androidx.camera.extensions)
```

**Features:**
- Modern camera API with consistent behavior across devices
- Lifecycle-aware camera operations
- Built-in use cases for preview, image capture, and analysis
- Auto-focus, exposure control, and flash management

**Integration Benefits:**
- ✅ **Consistent UX**: Uniform camera behavior across Android versions
- ✅ **Lifecycle Integration**: Automatic camera resource management
- ✅ **Advanced Features**: Portrait mode, night mode extensions
- ✅ **Testing Support**: Easy to mock and test camera operations

### 3. Room Database for Local Storage

**Version:** `2.6.1`  
**Gradle Dependencies:**
```kotlin
implementation(libs.androidx.room.runtime)
implementation(libs.androidx.room.ktx)
kapt(libs.androidx.room.compiler)
```

**Configuration:**
```kotlin
javaCompileOptions {
    annotationProcessorOptions {
        arguments["room.schemaLocation"] = "$projectDir/schemas"
    }
}
```

**Features:**
- SQLite abstraction with compile-time verification
- Coroutines and Flow support for reactive programming
- Migration support for database schema changes
- Type converters for complex data types

**Integration Benefits:**
- ✅ **Type Safety**: Compile-time SQL query verification
- ✅ **Reactive Queries**: Flow-based data observation
- ✅ **Migration Support**: Automatic schema versioning
- ✅ **Performance**: Optimized SQLite operations

### 4. Retrofit for Future API Calls

**Version:** `2.9.0`  
**Gradle Dependencies:**
```kotlin
implementation(libs.retrofit)
implementation(libs.retrofit.converter.gson)
implementation(libs.okhttp3.logging.interceptor)
```

**Features:**
- Type-safe HTTP client for REST APIs
- JSON serialization with Gson converter
- Interceptor support for logging and authentication
- Coroutines support for async operations

**Integration Benefits:**
- ✅ **Type Safety**: Compile-time API interface validation
- ✅ **Easy Configuration**: Simple annotation-based API definitions
- ✅ **Security Integration**: Works with certificate pinning
- ✅ **Testing Support**: Mock server integration capabilities

## Architecture Integration

### Dependency Injection with Hilt

**Version:** `2.48`  
**Configuration:**
```kotlin
// Build plugins
alias(libs.plugins.dagger.hilt.android)
alias(libs.plugins.kotlin.kapt)

// Dependencies
implementation(libs.hilt.android)
kapt(libs.hilt.compiler)
```

**Integration Modules:**
- `DatabaseModule.kt` - Room database and DAO providers
- `RepositoryModule.kt` - Repository implementations
- `SecurityModule.kt` - Security components (KeystoreManager, NetworkSecurityHelper)

### Coroutines Integration

**Version:** `1.7.3`  
**Dependencies:**
```kotlin
implementation(libs.kotlinx.coroutines.android)
implementation(libs.kotlinx.coroutines.core)
```

**Features:**
- Asynchronous programming for camera, OCR, and database operations
- Flow-based reactive programming for UI updates
- Structured concurrency for error handling

### Image Loading with Glide

**Version:** `4.16.0`  
**Dependencies:**
```kotlin
implementation(libs.glide)
kapt(libs.glide.compiler)
```

**Features:**
- Efficient image loading and caching
- Memory management for receipt images
- Transformation support for image processing

## Testing Infrastructure

### Unit Testing
```kotlin
testImplementation(libs.junit)
testImplementation(libs.mockk)
testImplementation(libs.kotlinx.coroutines.test)
testImplementation(libs.androidx.arch.core.testing)
```

### Integration Testing
```kotlin
androidTestImplementation(libs.androidx.junit)
androidTestImplementation(libs.androidx.espresso.core)
androidTestImplementation(libs.hilt.android.testing)
kaptAndroidTest(libs.hilt.compiler)
```

## Library Compatibility Matrix

| Library | Min SDK | Target SDK | Kotlin | AGP |
|---------|---------|------------|---------|-----|
| ML Kit Text Recognition | 19 | 35 | 1.9.24 | 8.8.0 |
| CameraX | 21 | 35 | 1.9.24 | 8.8.0 |
| Room | 16 | 35 | 1.9.24 | 8.8.0 |
| Retrofit | 21 | 35 | 1.9.24 | 8.8.0 |
| Hilt | 19 | 35 | 1.9.24 | 8.8.0 |

**Project Configuration:**
- **Min SDK:** 24 (Android 7.0)
- **Target SDK:** 35 (Android 15)
- **Compile SDK:** 35

## Build Configuration

### Gradle Configuration (app/build.gradle.kts)
```kotlin
android {
    compileSdk = 35
    
    defaultConfig {
        minSdk = 24
        targetSdk = 35
        
        // Room schema export location
        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }
    }
    
    buildFeatures {
        viewBinding = true  // For efficient view binding
    }
}
```

### Version Catalog (gradle/libs.versions.toml)
All library versions are centrally managed in the version catalog for consistency and easy updates.

## Security Integration

All core libraries are integrated with the security framework:

- **Room**: Database encryption support ready
- **Retrofit**: Certificate pinning integration
- **CameraX**: Secure image storage paths
- **ML Kit**: On-device processing for privacy

## Performance Considerations

### Memory Management
- Glide for efficient image loading and caching
- Room query optimization with Flow
- Coroutines for non-blocking operations

### Battery Optimization
- CameraX lifecycle awareness
- Background processing with WorkManager
- Efficient database queries

### Storage Efficiency
- Room database optimization
- Image compression strategies
- ProGuard resource shrinking

## Development Workflow

### Local Development
```bash
# Build project with all dependencies
./gradlew build

# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest
```

### Dependency Updates
Regular dependency updates should be performed:
1. Check for library updates using Gradle version catalogs
2. Test compatibility with new versions
3. Update ProGuard rules if needed
4. Verify security configurations remain valid

## Future Considerations

### Planned Enhancements
- **Jetpack Compose**: Migration from View system
- **DataStore**: Migration from SharedPreferences
- **WorkManager**: Background OCR processing
- **Paging 3**: Efficient receipt list loading

### Maintenance
- Regular dependency security audits
- Library version compatibility testing
- Performance monitoring and optimization

---

**Integration Date:** September 9, 2025  
**Task:** docs/MVP-Tasks.md - Section 1.2 Core Dependencies & Libraries  
**Status:** ✅ Completed  
**Libraries Status:** All dependencies integrated and ready for development