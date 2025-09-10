package com.pharadev.receiptscanner.presentation.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.pharadev.receiptscanner.R
import com.pharadev.receiptscanner.data.camera.CameraService
import com.pharadev.receiptscanner.databinding.ActivityCameraBinding
import com.pharadev.receiptscanner.presentation.viewmodel.CameraViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CameraActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityCameraBinding
    private val viewModel: CameraViewModel by viewModels()
    
    @Inject
    lateinit var cameraService: CameraService
    
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    
    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            showPermissionDeniedMessage()
        }
    }
    
    // Gallery launcher for importing images
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                handleGalleryImage(uri)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        checkCameraPermission()
    }
    
    private fun setupUI() {
        // Set portrait orientation lock
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        
        binding.apply {
            // Capture button
            captureButton.setOnClickListener {
                capturePhoto()
            }
            
            // Flash toggle button
            flashButton.setOnClickListener {
                toggleFlash()
            }
            
            // Gallery button
            galleryButton.setOnClickListener {
                openGallery()
            }
            
            // Close button
            closeButton.setOnClickListener {
                finish()
            }
            
            // Camera flip button (if front camera available)
            flipCameraButton.setOnClickListener {
                flipCamera()
            }
        }
        
        // Update flash button visibility based on flash unit availability
        updateFlashButtonVisibility()
    }
    
    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    private fun startCamera() {
        lifecycleScope.launch {
            try {
                val success = cameraService.initializeCamera(
                    binding.previewView,
                    this@CameraActivity,
                    cameraSelector
                )
                
                if (success) {
                    updateFlashButtonVisibility()
                    binding.progressBar.visibility = View.GONE
                    binding.cameraControls.visibility = View.VISIBLE
                } else {
                    showCameraError("Failed to initialize camera")
                }
            } catch (e: Exception) {
                showCameraError("Camera initialization error: ${e.message}")
            }
        }
    }
    
    private fun capturePhoto() {
        // Disable capture button to prevent multiple captures
        binding.captureButton.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val imageUri = cameraService.capturePhoto()
                onPhotoCaptured(imageUri)
            } catch (e: Exception) {
                showCameraError("Failed to capture photo: ${e.message}")
                binding.captureButton.isEnabled = true
                binding.progressBar.visibility = View.GONE
            }
        }
    }
    
    private fun toggleFlash() {
        if (cameraService.hasFlashUnit()) {
            val isEnabled = cameraService.toggleFlash()
            updateFlashButtonIcon(isEnabled)
        }
    }
    
    private fun flipCamera() {
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        
        // Restart camera with new selector
        binding.progressBar.visibility = View.VISIBLE
        binding.cameraControls.visibility = View.GONE
        startCamera()
    }
    
    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/*"
        }
        galleryLauncher.launch(intent)
    }
    
    private fun handleGalleryImage(uri: Uri) {
        // Convert URI to file path and process
        onPhotoCaptured(uri)
    }
    
    private fun onPhotoCaptured(imageUri: Uri) {
        // Pass the captured image to the ViewModel for processing
        viewModel.onImageCaptured(imageUri.path ?: "")
        
        // Navigate to image preview/confirmation screen
        val intent = Intent(this, ImagePreviewActivity::class.java).apply {
            putExtra(ImagePreviewActivity.EXTRA_IMAGE_URI, imageUri.toString())
        }
        startActivity(intent)
        
        // Reset UI state
        binding.captureButton.isEnabled = true
        binding.progressBar.visibility = View.GONE
    }
    
    private fun updateFlashButtonVisibility() {
        binding.flashButton.visibility = if (cameraService.hasFlashUnit()) {
            View.VISIBLE
        } else {
            View.GONE
        }
        updateFlashButtonIcon(cameraService.isFlashEnabled())
    }
    
    private fun updateFlashButtonIcon(isEnabled: Boolean) {
        binding.flashButton.setImageResource(
            if (isEnabled) R.drawable.ic_flash_on else R.drawable.ic_flash_off
        )
    }
    
    private fun showPermissionDeniedMessage() {
        Toast.makeText(
            this,
            "Camera permission is required to scan receipts",
            Toast.LENGTH_LONG
        ).show()
        finish()
    }
    
    private fun showCameraError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraService.shutdown()
    }
    
    companion object {
        const val REQUEST_CODE_CAMERA = 1001
    }
}