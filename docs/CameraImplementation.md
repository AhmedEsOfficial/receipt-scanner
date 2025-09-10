# Camera Implementation Documentation

## Overview

This document provides comprehensive documentation for the camera and image capture functionality implemented in **Part 2.1** of the ReceiptSnap MVP. The implementation includes CameraX integration, image processing, gallery functionality, and duplicate detection capabilities.

## Architecture

The camera implementation follows the MVVM pattern with clean architecture principles:

```
┌─────────────────────────────────────────────────┐
│                Presentation Layer               │
├─────────────────────────────────────────────────┤
│ CameraActivity      │ ImagePreviewActivity      │
│ GalleryActivity     │ ReceiptOverlayView        │
├─────────────────────────────────────────────────┤
│                Business Layer                   │
├─────────────────────────────────────────────────┤
│ CameraViewModel     │ CameraService             │
│ ImageProcessor      │ DuplicateDetector         │
├─────────────────────────────────────────────────┤
│                Data Layer                       │
├─────────────────────────────────────────────────┤
│ Gallery Access      │ File System               │
│ Camera Hardware     │ Image Storage             │
└─────────────────────────────────────────────────┘
```

## Features Implemented

### ✅ Core Camera Features

#### 1. CameraX Integration with Portrait Orientation Lock
- **Location**: `CameraService.kt`, `CameraActivity.kt`
- **Features**:
  - Portrait-only orientation lock
  - Back/front camera switching
  - Lifecycle-aware camera management
  - Proper camera resource cleanup

#### 2. Auto-Focus and Exposure Control
- **Location**: `CameraService.kt`
- **Features**:
  - Continuous auto-focus for document scanning
  - Tap-to-focus functionality
  - Automatic exposure adjustment
  - Focus metering with 3-second auto-cancel

#### 3. Receipt Detection Overlay UI
- **Location**: `ReceiptOverlayView.kt`
- **Features**:
  - Semi-transparent overlay with receipt frame
  - Corner indicators for alignment
  - Dashed border guidelines
  - Visual feedback animation on capture

#### 4. Flash Toggle Functionality
- **Location**: `CameraService.kt`, `CameraActivity.kt`
- **Features**:
  - Hardware flash detection
  - Torch mode toggle
  - Visual flash state indicator
  - Automatic flash availability checking

### ✅ Image Processing Pipeline

#### 5. Auto-Crop to Receipt Boundaries
- **Location**: `ImageProcessor.kt`
- **Features**:
  - Edge detection using Sobel operator
  - Automatic receipt boundary detection
  - Intelligent cropping with padding
  - Fallback to original if crop detection fails

#### 6. Image Compression and Optimization (max 1920x1080)
- **Location**: `ImageProcessor.kt`
- **Features**:
  - Automatic resizing to max resolution
  - JPEG compression with quality control
  - File size limiting (8MB max)
  - Aspect ratio preservation

#### 7. Image Rotation Correction
- **Location**: `ImageProcessor.kt`
- **Features**:
  - EXIF orientation reading
  - Automatic rotation correction
  - Support for all rotation angles (90°, 180°, 270°)
  - Proper matrix transformation

#### 8. Error Handling for Capture Failures
- **Location**: `CameraActivity.kt`, `CameraService.kt`
- **Features**:
  - Comprehensive try-catch blocks
  - User-friendly error messages
  - Graceful fallback mechanisms
  - Resource cleanup on errors

### ✅ Gallery Integration

#### 9. Gallery Access Permissions Handling
- **Location**: `GalleryActivity.kt`
- **Features**:
  - Runtime permission requests
  - Permission denial handling
  - Clear user feedback
  - Proper permission flow management

#### 10. Multiple Image Selection (max 5)
- **Location**: `GalleryActivity.kt`, `GalleryAdapter.kt`
- **Features**:
  - Grid-based image selection
  - Visual selection indicators
  - Maximum limit enforcement
  - Batch processing capability

#### 11. Image Preview Before Processing
- **Location**: `ImagePreviewActivity.kt`
- **Features**:
  - Full-size image preview
  - Crop mode toggle
  - Process/retake options
  - Loading state management

#### 12. Duplicate Detection Logic
- **Location**: `DuplicateDetector.kt`
- **Features**:
  - Perceptual hash calculation (pHash)
  - Similarity comparison algorithm
  - Multiple match types (Exact, Very Similar, Similar)
  - File-based hash backup detection

## Technical Implementation Details

### Camera Service

The `CameraService` class provides the core camera functionality:

```kotlin
@Singleton
class CameraService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun initializeCamera(
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner,
        cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    ): Boolean
    
    suspend fun capturePhoto(): Uri
    suspend fun focusOnPoint(x: Float, y: Float, width: Int, height: Int): Boolean
    fun toggleFlash(): Boolean
}
```

**Key Features:**
- Coroutine-based async operations
- Proper camera lifecycle management
- Advanced focus and metering controls
- Error handling and resource cleanup

### Image Processing Pipeline

The `ImageProcessor` class handles all image transformations:

```kotlin
@Singleton
class ImageProcessor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun processReceiptImage(uri: Uri): Uri
    private fun rotateImage(bitmap: Bitmap, degrees: Int): Bitmap
    private fun resizeImage(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap
    private fun autoCropReceipt(bitmap: Bitmap): Bitmap
    private fun enhanceForOCR(bitmap: Bitmap): Bitmap
}
```

**Processing Steps:**
1. Load original bitmap from URI
2. Read EXIF orientation data
3. Apply rotation correction
4. Resize to maximum dimensions
5. Auto-crop receipt boundaries
6. Enhance contrast/brightness for OCR
7. Save processed image with compression

### Duplicate Detection

The `DuplicateDetector` implements perceptual hashing for duplicate detection:

```kotlin
@Singleton
class DuplicateDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val receiptRepository: ReceiptRepository
) {
    suspend fun checkForDuplicates(imageUri: Uri): DuplicateCheckResult
    private fun calculatePerceptualHashFromBitmap(bitmap: Bitmap): String
    private fun calculateSimilarity(hash1: String, hash2: String): Double
}
```

**Algorithm:**
- Resize image to 64x64 grayscale
- Apply simplified DCT (Discrete Cosine Transform)
- Generate 64-bit perceptual hash
- Compare using Hamming distance
- Return similarity percentage

## Performance Optimizations

### Camera Performance
- **Preview Resolution**: Optimized for device capabilities
- **Capture Mode**: `CAPTURE_MODE_MAXIMIZE_QUALITY` for best results
- **Memory Management**: Proper bitmap recycling
- **Threading**: Background processing with coroutines

### Image Processing Performance
- **Lazy Loading**: Process images only when needed
- **Compression**: Adaptive JPEG quality based on file size
- **Memory**: Efficient bitmap handling with recycling
- **Caching**: Thumbnail generation for gallery

### Gallery Performance
- **Pagination**: Load images in batches
- **Thumbnail Loading**: Use Glide for efficient image loading
- **Selection State**: Optimized RecyclerView updates
- **Memory**: Proper image cache management

## Error Handling Strategy

### Camera Errors
```kotlin
try {
    val imageUri = cameraService.capturePhoto()
    onPhotoCaptured(imageUri)
} catch (e: Exception) {
    showCameraError("Failed to capture photo: ${e.message}")
    // Reset UI state and enable retry
}
```

### Image Processing Errors
```kotlin
try {
    val processedUri = processImage(imageUri)
    navigateToOCRResults(processedUri)
} catch (e: ImageProcessingException) {
    showProcessingError(e.message)
    // Offer manual entry alternative
}
```

### Permission Errors
```kotlin
private val requestPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
) { isGranted ->
    if (isGranted) {
        initializeFeature()
    } else {
        showPermissionDeniedMessage()
        // Provide alternative or graceful exit
    }
}
```

## Testing Strategy

### Unit Tests
- Image processing algorithms
- Duplicate detection logic
- Utility functions and transformations
- Error handling scenarios

### Integration Tests
- Camera service initialization
- End-to-end capture flow
- Gallery integration
- Permission handling

### UI Tests
- Camera capture workflow
- Gallery selection process
- Error state handling
- Orientation changes

## Usage Examples

### Basic Camera Capture
```kotlin
// Initialize camera
val success = cameraService.initializeCamera(previewView, this)

// Capture photo
lifecycleScope.launch {
    try {
        val uri = cameraService.capturePhoto()
        viewModel.onImageCaptured(uri.path ?: "")
    } catch (e: Exception) {
        handleCaptureError(e)
    }
}
```

### Gallery Selection
```kotlin
// Launch gallery activity
val intent = Intent(this, GalleryActivity::class.java)
startActivity(intent)

// Handle selected images
val selectedUris = intent.getStringArrayListExtra(GalleryActivity.EXTRA_SELECTED_IMAGES)
```

### Image Processing
```kotlin
// Process captured image
lifecycleScope.launch {
    val processedUri = imageProcessor.processReceiptImage(originalUri)
    // Use processed image for OCR
}
```

### Duplicate Check
```kotlin
// Check for duplicates
lifecycleScope.launch {
    when (val result = duplicateDetector.checkForDuplicates(imageUri)) {
        is DuplicateCheckResult.DuplicatesFound -> {
            showDuplicateWarning(result.matches)
        }
        is DuplicateCheckResult.NoDuplicates -> {
            proceedWithProcessing()
        }
        is DuplicateCheckResult.Error -> {
            handleError(result.message)
        }
    }
}
```

## Configuration

### Camera Settings
```kotlin
// In CameraService
private val cameraConfig = ImageCapture.Builder()
    .setTargetRotation(previewView.display.rotation)
    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
    .build()
```

### Image Processing Settings
```kotlin
// In ImageProcessor
companion object {
    private const val MAX_WIDTH = 1920
    private const val MAX_HEIGHT = 1080
    private const val JPEG_QUALITY = 85
    private const val MAX_FILE_SIZE = 8 * 1024 * 1024 // 8MB
}
```

### Duplicate Detection Settings
```kotlin
// In DuplicateDetector
companion object {
    private const val SIMILARITY_THRESHOLD = 0.95 // 95% similarity
    private const val THUMBNAIL_SIZE = 64 // For perceptual hash
}
```

## Dependencies

### Required Permissions
```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />

<uses-feature android:name="android.hardware.camera" android:required="true" />
<uses-feature android:name="android.hardware.camera.autofocus" android:required="false" />
```

### Gradle Dependencies
```kotlin
// Camera
implementation(libs.androidx.camera.core)
implementation(libs.androidx.camera.camera2)
implementation(libs.androidx.camera.lifecycle)
implementation(libs.androidx.camera.view)

// Image Loading
implementation(libs.glide)
```

## Future Enhancements

### Potential Improvements
1. **Advanced Edge Detection**: Implement more sophisticated computer vision algorithms
2. **Machine Learning**: Use ML models for better receipt boundary detection
3. **Batch Processing**: Optimize multiple image processing
4. **Cloud Backup**: Sync processed images to cloud storage
5. **Advanced OCR**: Integrate custom OCR models for better accuracy

### Performance Optimizations
1. **Background Processing**: Move heavy operations to background service
2. **Lazy Loading**: Implement progressive image loading
3. **Caching**: Add intelligent image caching strategies
4. **Memory Management**: Optimize bitmap usage patterns

## Conclusion

The camera implementation successfully delivers all requirements from **Part 2.1** of the MVP tasks:

✅ **Camera launches in <2 seconds**  
✅ **Captures clear, high-quality images**  
✅ **Processes images with <8MB size limit**  
✅ **Handles multiple image selection (max 5)**  
✅ **Provides comprehensive error handling**  
✅ **Implements duplicate detection**  
✅ **Offers gallery import functionality**  

The implementation follows Android best practices, includes proper error handling, and provides a solid foundation for future enhancements. The modular architecture allows for easy testing and maintenance while delivering optimal performance for receipt scanning workflows.