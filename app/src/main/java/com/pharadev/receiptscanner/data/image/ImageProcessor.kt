package com.pharadev.receiptscanner.data.image

import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class ImageProcessor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val MAX_WIDTH = 1920
        private const val MAX_HEIGHT = 1080
        private const val JPEG_QUALITY = 85
        private const val MAX_FILE_SIZE = 8 * 1024 * 1024 // 8MB
    }
    
    suspend fun processReceiptImage(uri: Uri): Uri = withContext(Dispatchers.IO) {
        try {
            // Load original bitmap
            val inputStream = context.contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            if (originalBitmap == null) {
                throw IllegalArgumentException("Could not decode image")
            }
            
            // Get EXIF orientation
            val orientation = getImageOrientation(uri)
            
            // Apply rotation correction
            val rotatedBitmap = rotateImage(originalBitmap, orientation)
            
            // Resize image if needed
            val resizedBitmap = resizeImage(rotatedBitmap, MAX_WIDTH, MAX_HEIGHT)
            
            // Apply auto-crop (detect receipt boundaries)
            val croppedBitmap = autoCropReceipt(resizedBitmap)
            
            // Enhance image for OCR
            val enhancedBitmap = enhanceForOCR(croppedBitmap)
            
            // Save processed image
            val processedFile = createProcessedImageFile()
            saveBitmapToFile(enhancedBitmap, processedFile, JPEG_QUALITY)
            
            // Clean up bitmaps
            if (originalBitmap != rotatedBitmap) originalBitmap.recycle()
            if (rotatedBitmap != resizedBitmap) rotatedBitmap.recycle()
            if (resizedBitmap != croppedBitmap) resizedBitmap.recycle()
            if (croppedBitmap != enhancedBitmap) croppedBitmap.recycle()
            enhancedBitmap.recycle()
            
            Uri.fromFile(processedFile)
            
        } catch (e: Exception) {
            throw ImageProcessingException("Failed to process image: ${e.message}", e)
        }
    }
    
    private fun getImageOrientation(uri: Uri): Int {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val exif = ExifInterface(inputStream!!)
            inputStream.close()
            
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }
    
    private fun rotateImage(bitmap: Bitmap, degrees: Int): Bitmap {
        return if (degrees == 0) {
            bitmap
        } else {
            val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    }
    
    private fun resizeImage(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxWidth && height <= maxHeight) {
            return bitmap
        }
        
        val aspectRatio = width.toFloat() / height.toFloat()
        val targetWidth: Int
        val targetHeight: Int
        
        if (aspectRatio > maxWidth.toFloat() / maxHeight.toFloat()) {
            targetWidth = maxWidth
            targetHeight = (maxWidth / aspectRatio).toInt()
        } else {
            targetHeight = maxHeight
            targetWidth = (maxHeight * aspectRatio).toInt()
        }
        
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }
    
    private fun autoCropReceipt(bitmap: Bitmap): Bitmap {
        return try {
            // Convert to grayscale for edge detection
            val grayscaleBitmap = toGrayscale(bitmap)
            
            // Apply edge detection to find receipt boundaries
            val edges = detectEdges(grayscaleBitmap)
            
            // Find the largest rectangle in the image
            val cropRect = findReceiptBounds(edges)
            
            // Apply crop with some padding
            val paddedRect = addPadding(cropRect, bitmap.width, bitmap.height)
            
            if (paddedRect.width() > bitmap.width * 0.3 && paddedRect.height() > bitmap.height * 0.3) {
                Bitmap.createBitmap(
                    bitmap,
                    paddedRect.left,
                    paddedRect.top,
                    paddedRect.width(),
                    paddedRect.height()
                )
            } else {
                // Fallback: return original bitmap if crop area is too small
                bitmap
            }
        } catch (e: Exception) {
            // If auto-crop fails, return original
            bitmap
        }
    }
    
    private fun toGrayscale(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val grayscale = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        
        val canvas = Canvas(grayscale)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                setSaturation(0f)
            })
        }
        
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return grayscale
    }
    
    private fun detectEdges(bitmap: Bitmap): IntArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // Simple edge detection using Sobel operator
        val edges = IntArray(width * height)
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val gx = (-1 * getGray(pixels, x - 1, y - 1, width) +
                         1 * getGray(pixels, x + 1, y - 1, width) +
                         -2 * getGray(pixels, x - 1, y, width) +
                         2 * getGray(pixels, x + 1, y, width) +
                         -1 * getGray(pixels, x - 1, y + 1, width) +
                         1 * getGray(pixels, x + 1, y + 1, width))
                
                val gy = (-1 * getGray(pixels, x - 1, y - 1, width) +
                         -2 * getGray(pixels, x, y - 1, width) +
                         -1 * getGray(pixels, x + 1, y - 1, width) +
                         1 * getGray(pixels, x - 1, y + 1, width) +
                         2 * getGray(pixels, x, y + 1, width) +
                         1 * getGray(pixels, x + 1, y + 1, width))
                
                val magnitude = sqrt((gx * gx + gy * gy).toDouble()).toInt()
                edges[y * width + x] = if (magnitude > 128) 255 else 0
            }
        }
        
        return edges
    }
    
    private fun getGray(pixels: IntArray, x: Int, y: Int, width: Int): Int {
        val pixel = pixels[y * width + x]
        return Color.red(pixel) // Grayscale, so all channels are equal
    }
    
    private fun findReceiptBounds(edges: IntArray): Rect {
        // Simplified receipt detection - find the largest contiguous rectangular region
        // This is a basic implementation. A more sophisticated approach would use
        // contour detection algorithms
        
        val width = sqrt(edges.size.toDouble()).toInt()
        val height = edges.size / width
        
        var minX = width
        var maxX = 0
        var minY = height
        var maxY = 0
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (edges[y * width + x] > 0) {
                    minX = minOf(minX, x)
                    maxX = maxOf(maxX, x)
                    minY = minOf(minY, y)
                    maxY = maxOf(maxY, y)
                }
            }
        }
        
        return Rect(minX, minY, maxX, maxY)
    }
    
    private fun addPadding(rect: Rect, imageWidth: Int, imageHeight: Int, paddingPercent: Float = 0.05f): Rect {
        val paddingX = (imageWidth * paddingPercent).toInt()
        val paddingY = (imageHeight * paddingPercent).toInt()
        
        return Rect(
            maxOf(0, rect.left - paddingX),
            maxOf(0, rect.top - paddingY),
            minOf(imageWidth, rect.right + paddingX),
            minOf(imageHeight, rect.bottom + paddingY)
        )
    }
    
    private fun enhanceForOCR(bitmap: Bitmap): Bitmap {
        // Apply image enhancements to improve OCR accuracy
        val enhanced = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(enhanced)
        
        // Increase contrast and adjust brightness for better text recognition
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                // Increase contrast
                val contrast = 1.2f
                val brightness = 10f
                
                set(floatArrayOf(
                    contrast, 0f, 0f, 0f, brightness,
                    0f, contrast, 0f, 0f, brightness,
                    0f, 0f, contrast, 0f, brightness,
                    0f, 0f, 0f, 1f, 0f
                ))
            })
        }
        
        canvas.drawBitmap(enhanced, 0f, 0f, paint)
        return enhanced
    }
    
    private fun createProcessedImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = File(context.filesDir, "processed_images")
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        return File(storageDir, "PROCESSED_${timeStamp}.jpg")
    }
    
    private fun saveBitmapToFile(bitmap: Bitmap, file: File, quality: Int) {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        
        // Check file size and compress further if needed
        if (file.length() > MAX_FILE_SIZE) {
            val reducedQuality = (quality * 0.8).toInt()
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, reducedQuality, out)
            }
        }
    }
    
    suspend fun getImageDimensions(uri: Uri): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        
        context.contentResolver.openInputStream(uri).use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, options)
        }
        
        Pair(options.outWidth, options.outHeight)
    }
    
    suspend fun generateThumbnail(uri: Uri, maxSize: Int = 200): Bitmap? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri).use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)
                
                val scale = maxOf(options.outWidth / maxSize, options.outHeight / maxSize)
                options.inSampleSize = scale
                options.inJustDecodeBounds = false
                
                context.contentResolver.openInputStream(uri).use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}

class ImageProcessingException(message: String, cause: Throwable? = null) : Exception(message, cause)