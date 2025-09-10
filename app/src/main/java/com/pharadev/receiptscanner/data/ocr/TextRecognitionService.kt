package com.pharadev.receiptscanner.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
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
    private val context: Context
) {
    
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    /**
     * Process image from URI and extract text using ML Kit
     * @param imageUri URI of the image to process
     * @param rotation Rotation degrees for the image
     * @return OCRResult containing extracted text and confidence data
     */
    suspend fun processImageFromUri(
        imageUri: Uri, 
        rotation: Int = 0
    ): OCRResult = withContext(Dispatchers.IO) {
        try {
            val inputImage = InputImage.fromFilePath(context, imageUri)
            recognizeText(inputImage)
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
     * Core text recognition function using ML Kit
     * @param inputImage InputImage prepared for ML Kit processing
     * @return OCRResult containing extracted text and confidence data
     */
    private suspend fun recognizeText(inputImage: InputImage): OCRResult {
        return suspendCancellableCoroutine { continuation ->
            val startTime = System.currentTimeMillis()
            
            textRecognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    try {
                        val extractedBlocks = mutableListOf<TextBlock>()
                        
                        for (block in visionText.textBlocks) {
                            val textBlock = TextBlock(
                                text = block.text,
                                confidence = 0.8f, // ML Kit doesn't provide confidence scores, use default
                                boundingBox = block.boundingBox,
                                cornerPoints = block.cornerPoints?.toList(),
                                lines = block.lines.map { line ->
                                    TextLine(
                                        text = line.text,
                                        confidence = 0.8f, // ML Kit doesn't provide confidence scores, use default
                                        boundingBox = line.boundingBox,
                                        cornerPoints = line.cornerPoints?.toList(),
                                        elements = line.elements.map { element ->
                                            TextElement(
                                                text = element.text,
                                                confidence = 0.8f, // ML Kit doesn't provide confidence scores, use default
                                                boundingBox = element.boundingBox,
                                                cornerPoints = element.cornerPoints?.toList()
                                            )
                                        }
                                    )
                                }
                            )
                            extractedBlocks.add(textBlock)
                        }
                        
                        val result = OCRResult.Success(
                            fullText = visionText.text,
                            textBlocks = extractedBlocks,
                            averageConfidence = calculateAverageConfidence(extractedBlocks),
                            processingTimeMs = System.currentTimeMillis() - startTime
                        )
                        
                        continuation.resume(result)
                        
                    } catch (exception: Exception) {
                        continuation.resumeWithException(
                            OCRException("Error processing ML Kit results: ${exception.message}", exception)
                        )
                    }
                }
                .addOnFailureListener { exception ->
                    val errorMessage = when (exception.javaClass.simpleName) {
                        "MlKitException" -> "ML Kit service error: ${exception.message}"
                        "NetworkException" -> "Network error during text recognition: ${exception.message}"
                        "ServiceUnavailableException" -> "ML Kit service is temporarily unavailable"
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
     * Release resources used by the text recognizer
     */
    fun release() {
        textRecognizer.close()
    }
}

/**
 * Custom exception for OCR-related errors
 */
class OCRException(message: String, cause: Throwable? = null) : Exception(message, cause)