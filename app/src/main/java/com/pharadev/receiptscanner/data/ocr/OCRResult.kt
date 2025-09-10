package com.pharadev.receiptscanner.data.ocr

import android.graphics.Point
import android.graphics.Rect

/**
 * Sealed class representing the result of OCR text recognition
 */
sealed class OCRResult {
    
    /**
     * Successful OCR processing result
     * @param fullText Complete text extracted from the image
     * @param textBlocks Structured text blocks with positioning and confidence
     * @param averageConfidence Overall confidence score (0.0 - 1.0)
     * @param processingTimeMs Time taken for OCR processing
     */
    data class Success(
        val fullText: String,
        val textBlocks: List<TextBlock>,
        val averageConfidence: Float,
        val processingTimeMs: Long
    ) : OCRResult()
    
    /**
     * OCR processing error
     * @param message Error description
     * @param exception Optional exception details
     */
    data class Error(
        val message: String,
        val exception: Throwable? = null
    ) : OCRResult()
    
    /**
     * OCR processing in progress (for async operations)
     */
    object Processing : OCRResult()
}

/**
 * Represents a block of text with positioning and confidence information
 */
data class TextBlock(
    val text: String,
    val confidence: Float,
    val boundingBox: Rect?,
    val cornerPoints: List<Point>?,
    val lines: List<TextLine>
)

/**
 * Represents a line of text within a text block
 */
data class TextLine(
    val text: String,
    val confidence: Float,
    val boundingBox: Rect?,
    val cornerPoints: List<Point>?,
    val elements: List<TextElement>
)

/**
 * Represents individual text elements (words/characters) within a line
 */
data class TextElement(
    val text: String,
    val confidence: Float,
    val boundingBox: Rect?,
    val cornerPoints: List<Point>?
)