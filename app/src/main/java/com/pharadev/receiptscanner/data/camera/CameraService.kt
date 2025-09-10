package com.pharadev.receiptscanner.data.camera

import android.content.Context
import android.net.Uri
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class CameraService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var isFlashEnabled = false
    
    suspend fun initializeCamera(
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner,
        cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                this.cameraProvider = provider
                
                // Preview use case
                val preview = Preview.Builder()
                    .setTargetRotation(previewView.display.rotation)
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                
                // ImageCapture use case
                imageCapture = ImageCapture.Builder()
                    .setTargetRotation(previewView.display.rotation)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()
                
                // Unbind use cases before rebinding
                provider.unbindAll()
                
                // Bind use cases to camera with portrait orientation lock
                camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                
                // Configure camera control settings
                camera?.let { cam ->
                    // Enable torch/flash if supported
                    if (cam.cameraInfo.hasFlashUnit()) {
                        cam.cameraControl.enableTorch(isFlashEnabled)
                    }
                    
                    // Set auto-focus mode for optimal receipt scanning
                    if (cam.cameraInfo.isFocusMeteringSupported(FocusMeteringAction.Builder(
                            SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(0.5f, 0.5f)
                        ).build())) {
                        // Enable continuous auto-focus for document scanning
                        val action = FocusMeteringAction.Builder(
                            SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(0.5f, 0.5f),
                            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                        ).setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                         .build()
                        
                        cam.cameraControl.startFocusAndMetering(action)
                    }
                }
                
                continuation.resume(true)
            } catch (exc: Exception) {
                continuation.resumeWithException(exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    suspend fun capturePhoto(): Uri = suspendCancellableCoroutine { continuation ->
        val imageCapture = this.imageCapture ?: run {
            continuation.resumeWithException(IllegalStateException("Camera not initialized"))
            return@suspendCancellableCoroutine
        }
        
        // Create output file with timestamp
        val photoFile = createImageFile()
        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        
        imageCapture.takePicture(
            outputFileOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    continuation.resume(savedUri)
                }
                
                override fun onError(exception: ImageCaptureException) {
                    continuation.resumeWithException(exception)
                }
            }
        )
    }
    
    fun toggleFlash(): Boolean {
        isFlashEnabled = !isFlashEnabled
        camera?.let { cam ->
            try {
                if (cam.cameraInfo.hasFlashUnit()) {
                    cam.cameraControl.enableTorch(isFlashEnabled)
                }
            } catch (e: Exception) {
                // Handle error silently
            }
        }
        return isFlashEnabled
    }
    
    suspend fun focusOnPoint(x: Float, y: Float, width: Int, height: Int): Boolean = 
        suspendCancellableCoroutine { continuation ->
            camera?.let { cam ->
                try {
                    val factory = SurfaceOrientedMeteringPointFactory(width.toFloat(), height.toFloat())
                    val point = factory.createPoint(x, y)
                    
                    val action = FocusMeteringAction.Builder(
                        point,
                        FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                    ).setAutoCancelDuration(5, java.util.concurrent.TimeUnit.SECONDS)
                     .build()
                    
                    val future = cam.cameraControl.startFocusAndMetering(action)
                    future.addListener({
                        try {
                            val result = future.get()
                            continuation.resume(result.isFocusSuccessful)
                        } catch (e: Exception) {
                            continuation.resume(false)
                        }
                    }, ContextCompat.getMainExecutor(context))
                    
                } catch (e: Exception) {
                    continuation.resume(false)
                }
            } ?: continuation.resume(false)
        }
    
    fun hasFlashUnit(): Boolean {
        return cameraProvider?.availableCameraInfos?.any { it.hasFlashUnit() } ?: false
    }
    
    fun isFlashEnabled(): Boolean = isFlashEnabled
    
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = File(context.filesDir, "images")
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        return File(storageDir, "RECEIPT_${timeStamp}.jpg")
    }
    
    fun shutdown() {
        cameraProvider?.unbindAll()
        imageCapture = null
    }
}