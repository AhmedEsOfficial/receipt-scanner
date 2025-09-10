package com.pharadev.receiptscanner.presentation.ui.camera

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.pharadev.receiptscanner.data.image.ImageProcessor
import com.pharadev.receiptscanner.databinding.ActivityImagePreviewBinding
import com.pharadev.receiptscanner.presentation.viewmodel.CameraViewModel
import com.pharadev.receiptscanner.presentation.ui.manual.ManualEntryActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ImagePreviewActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityImagePreviewBinding
    private val viewModel: CameraViewModel by viewModels()
    
    @Inject
    lateinit var imageProcessor: ImageProcessor
    
    private var originalImageUri: Uri? = null
    private var processedImageUri: Uri? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImagePreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Lock to portrait orientation
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        
        setupUI()
        loadImage()
        observeViewModel()
    }
    
    private fun setupUI() {
        binding.apply {
            // Back button
            backButton.setOnClickListener {
                finish()
            }
            
            // Retake button
            retakeButton.setOnClickListener {
                finish()
            }
            
            // Process & Continue button
            continueButton.setOnClickListener {
                processAndContinue()
            }
            
            // Enable crop mode toggle
            cropModeToggle.setOnClickListener {
                toggleCropMode()
            }
        }
    }
    
    private fun loadImage() {
        val imageUriString = intent.getStringExtra(EXTRA_IMAGE_URI)
        if (imageUriString != null) {
            originalImageUri = Uri.parse(imageUriString)
            displayImage(originalImageUri!!)
        } else {
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    private fun displayImage(uri: Uri) {
        Glide.with(this)
            .load(uri)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .into(binding.imagePreview)
    }
    
    private fun observeViewModel() {
        // Observe processing state
        lifecycleScope.launch {
            viewModel.isProcessingOCR.collect { isProcessing ->
                binding.progressBar.visibility = if (isProcessing) View.VISIBLE else View.GONE
                binding.continueButton.isEnabled = !isProcessing
            }
        }
    }
    
    private fun processAndContinue() {
        val imageUri = processedImageUri ?: originalImageUri
        if (imageUri != null) {
            binding.progressBar.visibility = View.VISIBLE
            binding.continueButton.isEnabled = false
            
            lifecycleScope.launch {
                try {
                    // Process image (compression, rotation correction, etc.)
                    val processedUri = processImage(imageUri)
                    
                    // Pass to ViewModel for OCR processing
                    viewModel.onImageCaptured(processedUri.path ?: "")
                    
                    // Navigate to OCR results screen
                    navigateToOCRResults(processedUri)
                    
                } catch (e: Exception) {
                    Toast.makeText(this@ImagePreviewActivity, 
                        "Failed to process image: ${e.message}", 
                        Toast.LENGTH_LONG).show()
                    binding.progressBar.visibility = View.GONE
                    binding.continueButton.isEnabled = true
                }
            }
        }
    }
    
    private suspend fun processImage(uri: Uri): Uri {
        return imageProcessor.processReceiptImage(uri)
    }
    
    private fun toggleCropMode() {
        // Toggle between normal view and crop mode
        binding.cropModeToggle.isSelected = !binding.cropModeToggle.isSelected
        
        if (binding.cropModeToggle.isSelected) {
            // Enable crop mode - show crop overlay
            binding.cropOverlay.visibility = View.VISIBLE
            binding.cropInstructions.visibility = View.VISIBLE
        } else {
            // Disable crop mode
            binding.cropOverlay.visibility = View.GONE
            binding.cropInstructions.visibility = View.GONE
        }
    }
    
    private fun navigateToOCRResults(imageUri: Uri) {
        val intent = Intent(this, ManualEntryActivity::class.java).apply {
            putExtra(ManualEntryActivity.EXTRA_IMAGE_URI, imageUri.toString())
        }
        startActivity(intent)
        finish()
    }
    
    companion object {
        const val EXTRA_IMAGE_URI = "extra_image_uri"
    }
}