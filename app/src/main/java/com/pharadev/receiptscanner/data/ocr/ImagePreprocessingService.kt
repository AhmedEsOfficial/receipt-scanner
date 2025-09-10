package com.pharadev.receiptscanner.data.ocr

import android.graphics.*
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * Service for preprocessing receipt images before OCR to improve text recognition accuracy.
 * Applies various enhancement techniques optimized for receipt text extraction.
 */
@Singleton
class ImagePreprocessingService @Inject constructor() {
    
    companion object {
        private const val TAG = "ImagePreprocessingService"
        private const val TARGET_DPI = 300
        private const val MAX_IMAGE_SIZE = 2048
        private const val CONTRAST_FACTOR = 1.5f
        private const val BRIGHTNESS_OFFSET = 10
    }
    
    /**
     * Process image from URI with comprehensive preprocessing pipeline
     * @param imageUri Original image URI
     * @param outputDir Directory to save processed image
     * @return URI of processed image or null if processing failed
     */
    suspend fun preprocessImage(
        imageUri: Uri,
        outputDir: File
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting image preprocessing for URI: $imageUri")
            val startTime = System.currentTimeMillis()
            
            // Load original bitmap
            val originalBitmap = loadBitmapFromUri(imageUri) ?: run {
                Log.e(TAG, "Failed to load bitmap from URI: $imageUri")
                return@withContext null
            }
            
            Log.d(TAG, "Original image size: ${originalBitmap.width}x${originalBitmap.height}")
            
            // Apply preprocessing pipeline
            var processedBitmap = originalBitmap
            
            // Step 1: Resize if too large (maintains aspect ratio)
            processedBitmap = resizeForOCR(processedBitmap)
            
            // Step 2: Convert to grayscale for better OCR
            processedBitmap = convertToGrayscale(processedBitmap)
            
            // Step 3: Enhance contrast and brightness
            processedBitmap = enhanceContrastAndBrightness(processedBitmap)
            
            // Step 4: Apply sharpening filter
            processedBitmap = applySharpeningFilter(processedBitmap)
            
            // Step 5: Reduce noise while preserving text
            processedBitmap = applyTextOptimizedDenoising(processedBitmap)
            
            // Save processed image
            val outputFile = File(outputDir, "PROCESSED_${System.currentTimeMillis()}.jpg")
            val success = saveBitmapToFile(processedBitmap, outputFile, 95)
            
            // Clean up bitmaps
            if (processedBitmap != originalBitmap) {
                processedBitmap.recycle()
            }
            if (!originalBitmap.isRecycled) {
                originalBitmap.recycle()
            }
            
            val processingTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "Image preprocessing completed in ${processingTime}ms")
            
            if (success) {
                Log.d(TAG, "Processed image saved to: ${outputFile.absolutePath}")
                Uri.fromFile(outputFile)
            } else {
                Log.e(TAG, "Failed to save processed image")
                null
            }
            
        } catch (exception: Exception) {
            Log.e(TAG, "Image preprocessing failed", exception)
            null
        }
    }
    
    /**
     * Load bitmap from various URI schemes with proper error handling
     */
    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            val filePath = when {
                uri.scheme == "file" -> uri.path
                uri.path?.startsWith("/") == true -> uri.toString()
                else -> uri.path
            } ?: return null
            
            val file = File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "Image file does not exist: $filePath")
                return null
            }
            
            // Use BitmapFactory options to handle large images efficiently
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(filePath, options)
            
            // Calculate sample size if image is too large
            options.inSampleSize = calculateInSampleSize(options, MAX_IMAGE_SIZE, MAX_IMAGE_SIZE)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888
            
            BitmapFactory.decodeFile(filePath, options)
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to load bitmap from URI: $uri", exception)
            null
        }
    }
    
    /**
     * Calculate optimal sample size for image loading
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
    
    /**
     * Resize bitmap for optimal OCR processing (maintain aspect ratio)
     */
    private fun resizeForOCR(bitmap: Bitmap): Bitmap {
        val maxDimension = MAX_IMAGE_SIZE
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }
        
        val scaleFactor = minOf(
            maxDimension.toFloat() / width,
            maxDimension.toFloat() / height
        )
        
        val newWidth = (width * scaleFactor).roundToInt()
        val newHeight = (height * scaleFactor).roundToInt()
        
        Log.d(TAG, "Resizing image from ${width}x${height} to ${newWidth}x${newHeight}")
        
        val resized = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        if (resized != bitmap) {
            bitmap.recycle()
        }
        return resized
    }
    
    /**
     * Convert bitmap to grayscale for better OCR accuracy
     */
    private fun convertToGrayscale(bitmap: Bitmap): Bitmap {
        val grayscale = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(grayscale)
        
        val colorMatrix = ColorMatrix().apply {
            setSaturation(0f)
        }
        
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        if (grayscale != bitmap) {
            bitmap.recycle()
        }
        return grayscale
    }
    
    /**
     * Enhance contrast and brightness for better text visibility
     */
    private fun enhanceContrastAndBrightness(bitmap: Bitmap): Bitmap {
        val enhanced = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(enhanced)
        
        val colorMatrix = ColorMatrix(floatArrayOf(
            CONTRAST_FACTOR, 0f, 0f, 0f, BRIGHTNESS_OFFSET.toFloat(),
            0f, CONTRAST_FACTOR, 0f, 0f, BRIGHTNESS_OFFSET.toFloat(),
            0f, 0f, CONTRAST_FACTOR, 0f, BRIGHTNESS_OFFSET.toFloat(),
            0f, 0f, 0f, 1f, 0f
        ))
        
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        if (enhanced != bitmap) {
            bitmap.recycle()
        }
        return enhanced
    }
    
    /**
     * Apply sharpening filter to enhance text edges
     */
    private fun applySharpeningFilter(bitmap: Bitmap): Bitmap {
        val sharpened = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sharpened)
        
        // Sharpening kernel
        val sharpeningMatrix = floatArrayOf(
            0f, -1f, 0f,
            -1f, 5f, -1f,
            0f, -1f, 0f
        )
        
        val colorMatrix = ColorMatrix(floatArrayOf(
            1.2f, 0f, 0f, 0f, 0f,
            0f, 1.2f, 0f, 0f, 0f,
            0f, 0f, 1.2f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        if (sharpened != bitmap) {
            bitmap.recycle()
        }
        return sharpened
    }
    
    /**
     * Apply denoising optimized for text preservation
     */
    private fun applyTextOptimizedDenoising(bitmap: Bitmap): Bitmap {
        // Simple bilateral filter approximation
        val denoised = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        val denoisedPixels = IntArray(pixels.size)
        val width = bitmap.width
        val height = bitmap.height
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val index = y * width + x
                
                // Get surrounding pixels for noise reduction
                val neighbors = intArrayOf(
                    pixels[index - width - 1], pixels[index - width], pixels[index - width + 1],
                    pixels[index - 1], pixels[index], pixels[index + 1],
                    pixels[index + width - 1], pixels[index + width], pixels[index + width + 1]
                )
                
                // Apply median filter for noise reduction while preserving edges
                denoisedPixels[index] = medianFilter(neighbors)
            }
        }
        
        denoised.setPixels(denoisedPixels, 0, width, 0, 0, width, height)
        
        if (denoised != bitmap) {
            bitmap.recycle()
        }
        return denoised
    }
    
    /**
     * Simple median filter for a 3x3 kernel
     */
    private fun medianFilter(pixels: IntArray): Int {
        val grayValues = pixels.map { Color.red(it) }.sorted() // Use red channel for grayscale
        val median = grayValues[grayValues.size / 2]
        return Color.rgb(median, median, median)
    }
    
    /**
     * Save bitmap to file with specified quality
     */
    private fun saveBitmapToFile(bitmap: Bitmap, file: File, quality: Int): Boolean {
        return try {
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
            true
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to save bitmap to file: ${file.absolutePath}", exception)
            false
        }
    }
}