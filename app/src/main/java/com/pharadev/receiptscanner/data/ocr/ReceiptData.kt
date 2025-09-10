package com.pharadev.receiptscanner.data.ocr

import java.math.BigDecimal
import java.util.*

/**
 * Data class representing extracted receipt information from OCR processing
 */
data class ReceiptData(
    val amount: AmountData? = null,
    val vendor: VendorData? = null,
    val date: DateData? = null,
    val confidence: Float = 0.0f,
    val processingTimeMs: Long = 0L,
    val rawText: String = ""
) {
    /**
     * Check if the extraction has sufficient data quality
     * @param minimumConfidence Minimum confidence threshold (default: 0.5)
     * @return true if extraction quality is sufficient
     */
    fun hasGoodQuality(minimumConfidence: Float = 0.5f): Boolean {
        return confidence >= minimumConfidence && 
               (amount != null || vendor != null || date != null)
    }
    
    /**
     * Check if manual entry is recommended due to poor extraction quality
     */
    fun requiresManualEntry(): Boolean {
        return confidence < 0.3f || 
               (amount == null && vendor == null && date == null)
    }
    
    /**
     * Get extraction summary for logging/debugging
     */
    fun getSummary(): String {
        return buildString {
            append("ReceiptData(")
            append("confidence=${String.format("%.2f", confidence)}")
            if (amount != null) append(", amount=${amount.amount}${amount.currency}")
            if (vendor != null) append(", vendor='${vendor.name}'")
            if (date != null) append(", date=${date.date}")
            append(", processing=${processingTimeMs}ms")
            append(")")
        }
    }
}

/**
 * Extracted monetary amount information
 */
data class AmountData(
    val amount: BigDecimal,
    val currency: String = "$",
    val confidence: Float,
    val context: String // Original line where amount was found
) {
    /**
     * Get formatted amount string
     */
    fun getFormattedAmount(): String {
        return "$currency${String.format("%.2f", amount.toDouble())}"
    }
    
    /**
     * Check if amount seems reasonable for a receipt
     */
    fun isReasonableAmount(): Boolean {
        return amount > BigDecimal.ZERO && amount <= BigDecimal("10000.00")
    }
}

/**
 * Extracted vendor/business name information
 */
data class VendorData(
    val name: String,
    val confidence: Float,
    val lineIndex: Int // Position in the text where vendor was found
) {
    /**
     * Get cleaned vendor name (trimmed and title case)
     */
    fun getCleanName(): String {
        return name.trim().split(" ").joinToString(" ") { word ->
            if (word.isNotEmpty()) {
                word.lowercase().replaceFirstChar { 
                    if (it.isLowerCase()) it.titlecase() else it.toString() 
                }
            } else word
        }
    }
    
    /**
     * Check if vendor name seems valid
     */
    fun isValidVendor(): Boolean {
        val cleanName = name.trim()
        return cleanName.length >= 2 && 
               cleanName.any { it.isLetter() } &&
               cleanName.length <= 50
    }
}

/**
 * Extracted date information
 */
data class DateData(
    val date: Date,
    val confidence: Float,
    val originalText: String // Original date string found in text
) {
    /**
     * Check if date is within reasonable range for a receipt
     */
    fun isReasonableDate(): Boolean {
        val now = Date()
        val oneYearAgo = Date(now.time - (365L * 24 * 60 * 60 * 1000))
        val tomorrow = Date(now.time + (24L * 60 * 60 * 1000))
        
        return date.after(oneYearAgo) && date.before(tomorrow)
    }
    
    /**
     * Get days ago from current date
     */
    fun getDaysAgo(): Long {
        val now = System.currentTimeMillis()
        val difference = now - date.time
        return difference / (24 * 60 * 60 * 1000)
    }
    
    /**
     * Check if date is today
     */
    fun isToday(): Boolean {
        val today = Calendar.getInstance()
        val dateCalendar = Calendar.getInstance()
        dateCalendar.time = date
        
        return today.get(Calendar.YEAR) == dateCalendar.get(Calendar.YEAR) &&
               today.get(Calendar.DAY_OF_YEAR) == dateCalendar.get(Calendar.DAY_OF_YEAR)
    }
}

/**
 * OCR processing statistics
 */
data class OCRStatistics(
    val totalProcessingTimeMs: Long,
    val textRecognitionTimeMs: Long,
    val dataExtractionTimeMs: Long,
    val totalTextBlocks: Int,
    val totalWords: Int,
    val averageConfidence: Float,
    val extractionSuccess: Boolean
)