package com.pharadev.receiptscanner.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import java.io.File
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.common.MlKitException
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.ConnectionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Service for ML Kit text recognition from receipt images.
 * Provides OCR functionality with confidence scoring and structured text extraction.
 */
@Singleton
class TextRecognitionService @Inject constructor(
    private val context: Context,
    private val imagePreprocessingService: ImagePreprocessingService
) {
    
    companion object {
        private const val TAG = "TextRecognitionService"
    }
    
    init {
        // Check Google Play Services availability for MLKit
        checkGooglePlayServicesAvailability()
    }
    
    private fun checkGooglePlayServicesAvailability() {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)
        
        when (resultCode) {
            ConnectionResult.SUCCESS -> {
                Log.d(TAG, "Google Play Services is available and up to date")
            }
            ConnectionResult.SERVICE_MISSING -> {
                Log.w(TAG, "Google Play Services is missing - MLKit may not work properly")
            }
            ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> {
                Log.w(TAG, "Google Play Services needs to be updated - MLKit may not work properly")
            }
            ConnectionResult.SERVICE_DISABLED -> {
                Log.w(TAG, "Google Play Services is disabled - MLKit may not work properly")
            }
            else -> {
                Log.w(TAG, "Google Play Services status: $resultCode - MLKit may not work properly")
            }
        }
    }
    
    // Primary recognizer with default options
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    // Backup recognizer for fallback (CPU-only processing)
    private val backupTextRecognizer by lazy {
        Log.d(TAG, "Creating backup text recognizer for CPU-only processing")
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    
    private var useBackupRecognizer = false
    
    /**
     * Process image from URI and extract text using ML Kit with preprocessing and fallback
     * @param imageUri URI of the image to process
     * @param rotation Rotation degrees for the image
     * @param enablePreprocessing Whether to apply image preprocessing (default: true)
     * @return OCRResult containing extracted text and confidence data
     */
    suspend fun processImageFromUri(
        imageUri: Uri, 
        rotation: Int = 0,
        enablePreprocessing: Boolean = true
    ): OCRResult = withContext(Dispatchers.IO) {
        try {
            val processedImageUri = if (enablePreprocessing) {
                // Apply image preprocessing for better OCR accuracy
                val cacheDir = File(context.cacheDir, "processed_images")
                cacheDir.mkdirs()
                
                imagePreprocessingService.preprocessImage(imageUri, cacheDir) ?: imageUri
            } else {
                imageUri
            }
            
            val inputImage = when {
                // Handle file:// URIs and direct file paths
                processedImageUri.scheme == "file" || processedImageUri.path?.startsWith("/") == true -> {
                    val filePath = if (processedImageUri.scheme == "file") {
                        processedImageUri.path ?: throw IllegalArgumentException("Invalid file URI: $processedImageUri")
                    } else {
                        processedImageUri.toString()
                    }
                    val file = File(filePath)
                    if (!file.exists()) {
                        throw java.io.FileNotFoundException("File does not exist: $filePath")
                    }
                    InputImage.fromFilePath(context, Uri.fromFile(file))
                }
                // Handle content:// URIs
                processedImageUri.scheme == "content" -> {
                    InputImage.fromFilePath(context, processedImageUri)
                }
                // Fallback for other schemes
                else -> {
                    InputImage.fromFilePath(context, processedImageUri)
                }
            }
            
            // Try primary recognizer first, then fallback if needed
            var result = recognizeText(inputImage, usePrimary = !useBackupRecognizer)
            
            // If primary failed with NNAPI issues, try backup
            if (result is OCRResult.Error && !useBackupRecognizer && 
                (result.message?.contains("NNAPI", ignoreCase = true) == true ||
                 result.message?.contains("hardware", ignoreCase = true) == true ||
                 result.message?.contains("ML Kit service error", ignoreCase = true) == true)) {
                
                Log.w(TAG, "Primary recognizer failed, trying backup CPU-only recognizer")
                useBackupRecognizer = true
                result = recognizeText(inputImage, usePrimary = false)
            }
            
            result
        } catch (exception: java.io.FileNotFoundException) {
            OCRResult.Error("Image file not found: ${exception.message}", exception)
        } catch (exception: java.io.IOException) {
            OCRResult.Error("Error reading image file: ${exception.message}", exception)
        } catch (exception: OutOfMemoryError) {
            OCRResult.Error("Out of memory while processing image. Image may be too large.", exception)
        } catch (exception: SecurityException) {
            OCRResult.Error("Permission denied to access image: ${exception.message}", exception)
        } catch (exception: Exception) {
            OCRResult.Error("Failed to process image from URI: ${exception.message}", exception)
        }
    }
    
    /**
     * Process bitmap image and extract text using ML Kit
     * @param bitmap Bitmap image to process
     * @param rotation Rotation degrees for the image
     * @return OCRResult containing extracted text and confidence data
     */
    suspend fun processImageFromBitmap(
        bitmap: Bitmap,
        rotation: Int = 0
    ): OCRResult = withContext(Dispatchers.IO) {
        try {
            if (bitmap.isRecycled) {
                return@withContext OCRResult.Error("Bitmap is recycled and cannot be processed")
            }
            
            val inputImage = InputImage.fromBitmap(bitmap, rotation)
            recognizeText(inputImage)
        } catch (exception: IllegalArgumentException) {
            OCRResult.Error("Invalid bitmap format: ${exception.message}", exception)
        } catch (exception: OutOfMemoryError) {
            OCRResult.Error("Out of memory while processing bitmap. Image may be too large.", exception)
        } catch (exception: Exception) {
            OCRResult.Error("Failed to process bitmap image: ${exception.message}", exception)
        }
    }
    
    /**
     * Core text recognition function using ML Kit with enhanced error handling and fallback
     * @param inputImage InputImage prepared for ML Kit processing
     * @param usePrimary Whether to use primary recognizer (true) or backup (false)
     * @return OCRResult containing extracted text and confidence data
     */
    private suspend fun recognizeText(inputImage: InputImage, usePrimary: Boolean = true): OCRResult {
        return suspendCancellableCoroutine { continuation ->
            val startTime = System.currentTimeMillis()
            
            val recognizer = if (usePrimary) textRecognizer else backupTextRecognizer
            val recognizerType = if (usePrimary) "primary" else "backup"
            
            Log.d(TAG, "Starting MLKit text recognition ($recognizerType) for image: ${inputImage.width}x${inputImage.height}")
            Log.d(TAG, "Image format: ${inputImage.format}, rotation: ${inputImage.rotationDegrees}")
            
            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    try {
                        Log.d(TAG, "MLKit processing successful ($recognizerType). Text blocks found: ${visionText.textBlocks.size}")
                        Log.d(TAG, "Full extracted text length: ${visionText.text.length}")
                        if (visionText.text.isNotEmpty()) {
                            Log.d(TAG, "Full extracted text preview: ${visionText.text.take(100)}...")
                        }
                        
                        if (visionText.text.isEmpty()) {
                            Log.w(TAG, "MLKit ($recognizerType) returned empty text - possible NNAPI or image quality issue")
                        }
                        
                        val extractedBlocks = mutableListOf<TextBlock>()
                        
                        for (block in visionText.textBlocks) {
                            val textBlock = TextBlock(
                                text = block.text,
                                confidence = calculateTextConfidence(block.text),
                                boundingBox = block.boundingBox,
                                cornerPoints = block.cornerPoints?.toList(),
                                lines = block.lines.map { line ->
                                    TextLine(
                                        text = line.text,
                                        confidence = calculateTextConfidence(line.text),
                                        boundingBox = line.boundingBox,
                                        cornerPoints = line.cornerPoints?.toList(),
                                        elements = line.elements.map { element ->
                                            TextElement(
                                                text = element.text,
                                                confidence = calculateTextConfidence(element.text),
                                                boundingBox = element.boundingBox,
                                                cornerPoints = element.cornerPoints?.toList()
                                            )
                                        }
                                    )
                                }
                            )
                            extractedBlocks.add(textBlock)
                        }
                        
                        val processingTime = System.currentTimeMillis() - startTime
                        val avgConfidence = calculateAverageConfidence(extractedBlocks)
                        
                        Log.d(TAG, "OCR ($recognizerType) completed in ${processingTime}ms")
                        Log.d(TAG, "Average confidence: $avgConfidence")
                        Log.d(TAG, "Extracted ${extractedBlocks.size} text blocks")
                        
                        val result = OCRResult.Success(
                            fullText = visionText.text,
                            textBlocks = extractedBlocks,
                            averageConfidence = avgConfidence,
                            processingTimeMs = processingTime
                        )
                        
                        continuation.resume(result)
                        
                    } catch (exception: Exception) {
                        continuation.resumeWithException(
                            OCRException("Error processing ML Kit results: ${exception.message}", exception)
                        )
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "MLKit text recognition failed ($recognizerType)", exception)
                    Log.e(TAG, "Exception type: ${exception.javaClass.simpleName}")
                    Log.e(TAG, "Exception message: ${exception.message}")
                    
                    val errorMessage = when {
                        exception is MlKitException -> {
                            Log.e(TAG, "MLKit specific error - this might be related to NNAPI issues")
                            "ML Kit service error (possible NNAPI/hardware issue): ${exception.message}"
                        }
                        exception.message?.contains("NNAPI", ignoreCase = true) == true -> {
                            Log.e(TAG, "NNAPI error detected - hardware acceleration issue")
                            "Hardware acceleration error (NNAPI): ${exception.message}"
                        }
                        exception.javaClass.simpleName == "NetworkException" -> "Network error during text recognition: ${exception.message}"
                        exception.javaClass.simpleName == "ServiceUnavailableException" -> "ML Kit service is temporarily unavailable"
                        else -> "Text recognition failed: ${exception.message}"
                    }
                    
                    continuation.resumeWithException(
                        OCRException(errorMessage, exception)
                    )
                }
                .addOnCanceledListener {
                    continuation.resumeWithException(
                        OCRException("Text recognition was cancelled")
                    )
                }
        }
    }
    
    /**
     * Calculate text confidence based on text characteristics since MLKit doesn't provide confidence
     * @param text The recognized text
     * @return Estimated confidence score (0.0 - 1.0)
     */
    private fun calculateTextConfidence(text: String): Float {
        if (text.isEmpty()) return 0.0f
        
        var confidence = 0.5f // Base confidence
        
        // Higher confidence for longer text (more context)
        when {
            text.length >= 20 -> confidence += 0.3f
            text.length >= 10 -> confidence += 0.2f
            text.length >= 5 -> confidence += 0.1f
        }
        
        // Higher confidence for text with alphanumeric characters
        val alphaNumCount = text.count { it.isLetterOrDigit() }
        val alphaNumRatio = alphaNumCount.toFloat() / text.length
        confidence += alphaNumRatio * 0.2f
        
        // Higher confidence for proper capitalization patterns
        val words = text.split("\\s+".toRegex())
        val properCapCount = words.count { word -> 
            word.isNotEmpty() && (word[0].isUpperCase() || word.all { it.isDigit() })
        }
        if (words.isNotEmpty()) {
            confidence += (properCapCount.toFloat() / words.size) * 0.1f
        }
        
        // Lower confidence for text with many special characters or symbols
        val specialCharCount = text.count { !it.isLetterOrDigit() && !it.isWhitespace() }
        val specialCharRatio = specialCharCount.toFloat() / text.length
        confidence -= specialCharRatio * 0.2f
        
        return confidence.coerceIn(0.0f, 1.0f)
    }
    
    /**
     * Calculate average confidence across all detected text blocks
     * @param textBlocks List of detected text blocks
     * @return Average confidence score (0.0 - 1.0)
     */
    private fun calculateAverageConfidence(textBlocks: List<TextBlock>): Float {
        if (textBlocks.isEmpty()) return 0.0f
        
        val totalConfidence = textBlocks.sumOf { block ->
            val blockConfidence = block.lines.sumOf { line ->
                line.elements.sumOf { it.confidence.toDouble() }
            }
            blockConfidence / block.lines.size.coerceAtLeast(1)
        }
        
        return (totalConfidence / textBlocks.size).toFloat()
    }
    
    /**
     * Release resources used by both text recognizers
     */
    fun release() {
        try {
            textRecognizer.close()
            // Only close backup recognizer if it was actually used
            if (useBackupRecognizer) {
                backupTextRecognizer.close()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing text recognizers", e)
        }
    }
}

/**
 * Custom exception for OCR-related errors
 */
class OCRException(message: String, cause: Throwable? = null) : Exception(message, cause)