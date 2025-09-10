package com.pharadev.receiptscanner.presentation.ui.gallery

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.pharadev.receiptscanner.databinding.ActivityGalleryBinding
import com.pharadev.receiptscanner.presentation.ui.camera.ImagePreviewActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class GalleryActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityGalleryBinding
    private lateinit var galleryAdapter: GalleryAdapter
    private val selectedImages = mutableSetOf<Uri>()
    
    companion object {
        const val MAX_SELECTION = 5
        const val EXTRA_SELECTED_IMAGES = "extra_selected_images"
    }
    
    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            loadGalleryImages()
        } else {
            showPermissionDeniedMessage()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        checkStoragePermission()
    }
    
    private fun setupUI() {
        binding.apply {
            // Setup RecyclerView
            recyclerView.layoutManager = GridLayoutManager(this@GalleryActivity, 3)
            galleryAdapter = GalleryAdapter(
                onImageClick = { uri, isSelected ->
                    handleImageSelection(uri, isSelected)
                }
            )
            recyclerView.adapter = galleryAdapter
            
            // Back button
            backButton.setOnClickListener {
                finish()
            }
            
            // Continue button
            continueButton.setOnClickListener {
                proceedWithSelectedImages()
            }
            
            // Select all button
            selectAllButton.setOnClickListener {
                selectAllImages()
            }
            
            // Clear selection button
            clearSelectionButton.setOnClickListener {
                clearSelection()
            }
            
            updateSelectionUI()
        }
    }
    
    private fun checkStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
            == PackageManager.PERMISSION_GRANTED) {
            loadGalleryImages()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    
    private fun loadGalleryImages() {
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val images = loadImagesFromGallery()
                galleryAdapter.submitList(images)
                
                binding.emptyState.visibility = if (images.isEmpty()) View.VISIBLE else View.GONE
                
            } catch (e: Exception) {
                Toast.makeText(this@GalleryActivity, 
                    "Failed to load gallery images: ${e.message}", 
                    Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }
    
    private suspend fun loadImagesFromGallery(): List<GalleryImage> = withContext(Dispatchers.IO) {
        val images = mutableListOf<GalleryImage>()
        
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED
        )
        
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val size = cursor.getLong(sizeColumn)
                val dateAdded = cursor.getLong(dateColumn)
                
                val contentUri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )
                
                images.add(GalleryImage(contentUri, name, size, dateAdded))
            }
        }
        
        images
    }
    
    private fun handleImageSelection(uri: Uri, isSelected: Boolean) {
        if (isSelected) {
            if (selectedImages.size >= MAX_SELECTION) {
                Toast.makeText(this, "Maximum $MAX_SELECTION images allowed", Toast.LENGTH_SHORT).show()
                galleryAdapter.deselectImage(uri)
                return
            }
            selectedImages.add(uri)
        } else {
            selectedImages.remove(uri)
        }
        
        updateSelectionUI()
    }
    
    private fun updateSelectionUI() {
        binding.apply {
            val selectionCount = selectedImages.size
            
            selectionCountText.text = "$selectionCount / $MAX_SELECTION selected"
            continueButton.isEnabled = selectionCount > 0
            continueButton.text = if (selectionCount > 0) {
                "Process $selectionCount image${if (selectionCount > 1) "s" else ""}"
            } else {
                "Select images"
            }
            
            clearSelectionButton.isEnabled = selectionCount > 0
            selectAllButton.isEnabled = selectionCount < MAX_SELECTION && 
                    galleryAdapter.itemCount > 0
        }
    }
    
    private fun selectAllImages() {
        val availableSlots = MAX_SELECTION - selectedImages.size
        val imagesToSelect = galleryAdapter.getUnselectedImages()
            .take(availableSlots)
        
        imagesToSelect.forEach { uri ->
            selectedImages.add(uri)
            galleryAdapter.selectImage(uri)
        }
        
        updateSelectionUI()
    }
    
    private fun clearSelection() {
        selectedImages.clear()
        galleryAdapter.clearSelection()
        updateSelectionUI()
    }
    
    private fun proceedWithSelectedImages() {
        if (selectedImages.isEmpty()) return
        
        if (selectedImages.size == 1) {
            // Single image - go to preview activity
            val intent = Intent(this, ImagePreviewActivity::class.java).apply {
                putExtra(ImagePreviewActivity.EXTRA_IMAGE_URI, selectedImages.first().toString())
            }
            startActivity(intent)
        } else {
            // Multiple images - for now, process first image only
            val intent = Intent(this, ImagePreviewActivity::class.java).apply {
                putExtra(ImagePreviewActivity.EXTRA_IMAGE_URI, selectedImages.first().toString())
            }
            startActivity(intent)
        }
        
        finish()
    }
    
    private fun showPermissionDeniedMessage() {
        Toast.makeText(
            this,
            "Storage permission is required to access gallery images",
            Toast.LENGTH_LONG
        ).show()
        finish()
    }
}

data class GalleryImage(
    val uri: Uri,
    val name: String,
    val size: Long,
    val dateAdded: Long
)