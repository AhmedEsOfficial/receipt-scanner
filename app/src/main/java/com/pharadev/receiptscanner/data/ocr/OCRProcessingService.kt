package com.pharadev.receiptscanner.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level OCR processing service that coordinates text recognition and data extraction.
 * This service provides the main interface for OCR operations in the application.
 */
@Singleton
class OCRProcessingService @Inject constructor(
    private val context: Context,
    private val textRecognitionService: TextRecognitionService,
    private val dataExtractionService: DataExtractionService
) {
    
    companion object {
        private const val TAG = "OCRProcessingService"
        private const val MIN_CONFIDENCE_THRESHOLD = 0.3f
        private const val MAX_PROCESSING_TIME_MS = 30000L // 30 seconds timeout
    }
    
    /**
     * Process receipt image from URI with full OCR pipeline
     * @param imageUri URI of the image to process
     * @param rotation Optional rotation degrees
     * @return ProcessingResult containing both OCR and extracted data
     */
    suspend fun processReceiptFromUri(
        imageUri: Uri,
        rotation: Int = 0
    ): ProcessingResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        try {
            Log.d(TAG, "Starting OCR processing for URI: $imageUri")
            
            // Step 1: Text Recognition with timeout
            val ocrResult = withTimeoutOrNull(MAX_PROCESSING_TIME_MS) {
                textRecognitionService.processImageFromUri(imageUri, rotation)
            } ?: return@withContext ProcessingResult.Error(
                message = "OCR processing timed out after ${MAX_PROCESSING_TIME_MS}ms",
                processingTimeMs = System.currentTimeMillis() - startTime
            )
            
            val textRecognitionTime = System.currentTimeMillis() - startTime
            
            when (ocrResult) {
                is OCRResult.Success -> {
                    Log.d(TAG, "Text recognition successful in ${textRecognitionTime}ms")
                    Log.d(TAG, "Extracted text length: ${ocrResult.fullText.length} characters")
                    
                    // Step 2: Data Extraction
                    val dataExtractionStart = System.currentTimeMillis()
                    val extractedData = dataExtractionService.extractReceiptData(ocrResult)
                    val dataExtractionTime = System.currentTimeMillis() - dataExtractionStart
                    val totalTime = System.currentTimeMillis() - startTime
                    
                    Log.d(TAG, "Data extraction completed in ${dataExtractionTime}ms")
                    Log.d(TAG, "Total processing time: ${totalTime}ms")
                    Log.d(TAG, extractedData.getSummary())
                    
                    val statistics = OCRStatistics(
                        totalProcessingTimeMs = totalTime,
                        textRecognitionTimeMs = textRecognitionTime,
                        dataExtractionTimeMs = dataExtractionTime,
                        totalTextBlocks = ocrResult.textBlocks.size,
                        totalWords = ocrResult.fullText.split("\\s+".toRegex()).size,
                        averageConfidence = ocrResult.averageConfidence,
                        extractionSuccess = extractedData.hasGoodQuality()
                    )
                    
                    ProcessingResult.Success(
                        ocrResult = ocrResult,
                        extractedData = extractedData,
                        statistics = statistics
                    )
                }
                
                is OCRResult.Error -> {
                    Log.e(TAG, "Text recognition failed: ${ocrResult.message}")
                    ProcessingResult.Error(
                        message = "Text recognition failed: ${ocrResult.message}",
                        exception = ocrResult.exception,
                        processingTimeMs = System.currentTimeMillis() - startTime
                    )
                }
                
                else -> {
                    ProcessingResult.Error(
                        message = "Unexpected OCR result state",
                        processingTimeMs = System.currentTimeMillis() - startTime
                    )
                }
            }
            
        } catch (exception: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            Log.e(TAG, "OCR processing failed after ${processingTime}ms", exception)
            
            ProcessingResult.Error(
                message = "Processing failed: ${exception.message}",
                exception = exception,
                processingTimeMs = processingTime
            )
        }
    }
    
    /**
     * Process receipt image from Bitmap with full OCR pipeline
     * @param bitmap Bitmap image to process
     * @param rotation Optional rotation degrees
     * @return ProcessingResult containing both OCR and extracted data
     */
    suspend fun processReceiptFromBitmap(
        bitmap: Bitmap,
        rotation: Int = 0
    ): ProcessingResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        try {
            Log.d(TAG, "Starting OCR processing for bitmap (${bitmap.width}x${bitmap.height})")
            
            // Step 1: Text Recognition
            val ocrResult = textRecognitionService.processImageFromBitmap(bitmap, rotation)
            val textRecognitionTime = System.currentTimeMillis() - startTime
            
            when (ocrResult) {
                is OCRResult.Success -> {
                    Log.d(TAG, "Text recognition successful in ${textRecognitionTime}ms")
                    
                    // Step 2: Data Extraction
                    val dataExtractionStart = System.currentTimeMillis()
                    val extractedData = dataExtractionService.extractReceiptData(ocrResult)
                    val dataExtractionTime = System.currentTimeMillis() - dataExtractionStart
                    val totalTime = System.currentTimeMillis() - startTime
                    
                    Log.d(TAG, "Processing completed in ${totalTime}ms")
                    Log.d(TAG, extractedData.getSummary())
                    
                    val statistics = OCRStatistics(
                        totalProcessingTimeMs = totalTime,
                        textRecognitionTimeMs = textRecognitionTime,
                        dataExtractionTimeMs = dataExtractionTime,
                        totalTextBlocks = ocrResult.textBlocks.size,
                        totalWords = ocrResult.fullText.split("\\s+".toRegex()).size,
                        averageConfidence = ocrResult.averageConfidence,
                        extractionSuccess = extractedData.hasGoodQuality()
                    )
                    
                    ProcessingResult.Success(
                        ocrResult = ocrResult,
                        extractedData = extractedData,
                        statistics = statistics
                    )
                }
                
                is OCRResult.Error -> {
                    Log.e(TAG, "Text recognition failed: ${ocrResult.message}")
                    ProcessingResult.Error(
                        message = "Text recognition failed: ${ocrResult.message}",
                        exception = ocrResult.exception,
                        processingTimeMs = System.currentTimeMillis() - startTime
                    )
                }
                
                else -> {
                    ProcessingResult.Error(
                        message = "Unexpected OCR result state",
                        processingTimeMs = System.currentTimeMillis() - startTime
                    )
                }
            }
            
        } catch (exception: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            Log.e(TAG, "OCR processing failed after ${processingTime}ms", exception)
            
            ProcessingResult.Error(
                message = "Processing failed: ${exception.message}",
                exception = exception,
                processingTimeMs = processingTime
            )
        }
    }
    
    /**
     * Quick text-only extraction without full data parsing
     * Useful for previews or simple text extraction
     */
    suspend fun extractTextOnly(imageUri: Uri): String {
        return try {
            val ocrResult = textRecognitionService.processImageFromUri(imageUri)
            when (ocrResult) {
                is OCRResult.Success -> ocrResult.fullText
                is OCRResult.Error -> ""
                else -> ""
            }
        } catch (exception: Exception) {
            Log.w(TAG, "Text-only extraction failed", exception)
            ""
        }
    }
    
    /**
     * Check if the extraction result should trigger manual entry
     * @param result ProcessingResult to evaluate
     * @return true if manual entry is recommended
     */
    fun shouldUseManualEntry(result: ProcessingResult): Boolean {
        return when (result) {
            is ProcessingResult.Success -> {
                result.extractedData.requiresManualEntry() ||
                result.statistics.averageConfidence < MIN_CONFIDENCE_THRESHOLD
            }
            is ProcessingResult.Error -> true
            is ProcessingResult.Processing -> false
        }
    }
    
    /**
     * Get user-friendly quality assessment
     */
    fun getQualityAssessment(result: ProcessingResult): QualityAssessment {
        return when (result) {
            is ProcessingResult.Success -> {
                val confidence = result.statistics.averageConfidence
                val hasValidData = result.extractedData.hasGoodQuality()
                
                when {
                    confidence >= 0.8f && hasValidData -> QualityAssessment.Excellent
                    confidence >= 0.6f && hasValidData -> QualityAssessment.Good
                    confidence >= 0.4f || hasValidData -> QualityAssessment.Fair
                    else -> QualityAssessment.Poor
                }
            }
            is ProcessingResult.Error -> QualityAssessment.Failed
            is ProcessingResult.Processing -> QualityAssessment.Processing
        }
    }
    
    /**
     * Release resources used by OCR services
     */
    fun release() {
        textRecognitionService.release()
    }
}

/**
 * Sealed class representing the complete OCR processing result
 */
sealed class ProcessingResult {
    data class Success(
        val ocrResult: OCRResult.Success,
        val extractedData: ReceiptData,
        val statistics: OCRStatistics
    ) : ProcessingResult()
    
    data class Error(
        val message: String,
        val exception: Throwable? = null,
        val processingTimeMs: Long = 0L
    ) : ProcessingResult()
    
    object Processing : ProcessingResult()
}

/**
 * Quality assessment levels for user feedback
 */
enum class QualityAssessment {
    Excellent,    // High confidence, good data extraction
    Good,         // Decent confidence, most data extracted
    Fair,         // Some issues, partial data extraction
    Poor,         // Low confidence, minimal data extracted
    Failed,       // Processing failed
    Processing    // Still processing
}