package com.pharadev.receiptscanner.data.ocr

import android.util.Log
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for extracting structured data from OCR text results.
 * Focuses on parsing receipt-specific information: amount, vendor, and date.
 */
@Singleton
class DataExtractionService @Inject constructor() {
    
    private companion object {
        const val TAG = "DataExtractionService"
        
        // Currency symbols and patterns
        val CURRENCY_SYMBOLS = listOf("$", "€", "£", "¥", "₹", "₽", "¢")
        
        // Amount patterns - matches various currency formats
        val AMOUNT_PATTERNS = listOf(
            // $12.34, €12,34, £12.34
            "([${CURRENCY_SYMBOLS.joinToString("")}]?)\\s*([0-9]{1,4}[,.]?[0-9]{0,2}[.,][0-9]{2})".toPattern(),
            // $123, €123, 123.00
            "([${CURRENCY_SYMBOLS.joinToString("")}]?)\\s*([0-9]{1,4}[.,][0-9]{2})".toPattern(),
            // TOTAL: 12.34, Total $12.34
            "(?i)(?:total|amount|sum)\\s*:?\\s*([${CURRENCY_SYMBOLS.joinToString("")}]?)\\s*([0-9]{1,4}[.,][0-9]{2})".toPattern(),
            // 12.34 at end of line (likely total)
            "([0-9]{1,4}[.,][0-9]{2})\\s*$".toPattern()
        )
        
        // Date patterns - various formats
        val DATE_PATTERNS = listOf(
            // MM/dd/yyyy, dd/MM/yyyy
            "([0-1]?[0-9])[/\\-.]([0-3]?[0-9])[/\\-.](20[0-9]{2}|[0-9]{2})".toPattern(),
            // yyyy-MM-dd
            "(20[0-9]{2})[/\\-.]([0-1]?[0-9])[/\\-.]([0-3]?[0-9])".toPattern(),
            // dd-MMM-yyyy (e.g., 15-Jan-2023)
            "([0-3]?[0-9])[/\\-.]([A-Za-z]{3})[/\\-.](20[0-9]{2}|[0-9]{2})".toPattern(),
            // MMM dd, yyyy (e.g., Jan 15, 2023)
            "([A-Za-z]{3})\\s+([0-3]?[0-9]),?\\s+(20[0-9]{2}|[0-9]{2})".toPattern()
        )
        
        // Common receipt keywords for vendor identification
        val VENDOR_KEYWORDS = listOf(
            "store", "market", "shop", "restaurant", "cafe", "gas", "station",
            "pharmacy", "hospital", "hotel", "mall", "center", "inc", "ltd", "llc"
        )
        
        // Words to exclude from vendor names
        val VENDOR_EXCLUDE_WORDS = listOf(
            "receipt", "invoice", "bill", "total", "amount", "date", "time",
            "thank", "you", "visit", "again", "customer", "copy", "merchant"
        )
    }
    
    /**
     * Extract structured receipt data from OCR text
     * @param ocrResult OCR processing result
     * @return ReceiptData with extracted information
     */
    fun extractReceiptData(ocrResult: OCRResult.Success): ReceiptData {
        val startTime = System.currentTimeMillis()
        
        try {
            val fullText = ocrResult.fullText
            val lines = fullText.split("\n").filter { it.trim().isNotEmpty() }
            
            // Extract individual components
            val amount = extractAmount(fullText, lines, ocrResult.textBlocks)
            val vendor = extractVendor(lines, ocrResult.textBlocks)
            val date = extractDate(fullText, lines)
            
            val processingTime = System.currentTimeMillis() - startTime
            
            Log.d(TAG, "Data extraction completed in ${processingTime}ms")
            Log.d(TAG, "Extracted - Amount: $amount, Vendor: $vendor, Date: $date")
            
            return ReceiptData(
                amount = amount,
                vendor = vendor,
                date = date,
                confidence = calculateExtractionConfidence(amount, vendor, date, ocrResult.averageConfidence),
                processingTimeMs = processingTime,
                rawText = fullText
            )
            
        } catch (exception: Exception) {
            Log.e(TAG, "Data extraction failed", exception)
            return ReceiptData(
                amount = null,
                vendor = null,
                date = null,
                confidence = 0.0f,
                processingTimeMs = System.currentTimeMillis() - startTime,
                rawText = ocrResult.fullText
            )
        }
    }
    
    /**
     * Extract monetary amount from receipt text
     * Prioritizes larger amounts and those with "total" keywords
     */
    private fun extractAmount(fullText: String, lines: List<String>, textBlocks: List<TextBlock>): AmountData? {
        val potentialAmounts = mutableListOf<AmountCandidate>()
        
        // Search through all lines for amount patterns
        lines.forEachIndexed { lineIndex, line ->
            AMOUNT_PATTERNS.forEach { pattern ->
                val matcher = pattern.matcher(line)
                while (matcher.find()) {
                    try {
                        val currencySymbol = matcher.group(1) ?: ""
                        val amountText = matcher.group(matcher.groupCount())
                        val normalizedAmount = amountText.replace(",", ".")
                        val amount = BigDecimal(normalizedAmount)
                        
                        // Calculate confidence based on context
                        var confidence = 0.5f
                        val lowerLine = line.lowercase()
                        
                        // Higher confidence for lines with "total" keywords
                        if (lowerLine.contains("total") || lowerLine.contains("amount") || 
                            lowerLine.contains("sum") || lowerLine.contains("subtotal")) {
                            confidence += 0.3f
                        }
                        
                        // Higher confidence for amounts near bottom of receipt
                        if (lineIndex > lines.size * 0.7) {
                            confidence += 0.2f
                        }
                        
                        // Higher confidence for larger amounts (likely totals)
                        if (amount >= BigDecimal("10.00")) {
                            confidence += 0.1f
                        }
                        
                        potentialAmounts.add(
                            AmountCandidate(
                                amount = amount,
                                currency = currencySymbol.takeIf { it.isNotEmpty() } ?: "$",
                                confidence = confidence.coerceAtMost(1.0f),
                                context = line.trim()
                            )
                        )
                        
                    } catch (e: NumberFormatException) {
                        Log.w(TAG, "Failed to parse amount: ${matcher.group()}")
                    }
                }
            }
        }
        
        // Return the amount with highest confidence, or largest amount if confidence is tied
        return potentialAmounts
            .sortedWith(compareByDescending<AmountCandidate> { it.confidence }.thenByDescending { it.amount })
            .firstOrNull()
            ?.let { candidate ->
                AmountData(
                    amount = candidate.amount,
                    currency = candidate.currency,
                    confidence = candidate.confidence,
                    context = candidate.context
                )
            }
    }
    
    /**
     * Extract vendor name from receipt text
     * Looks for business names typically at the top of receipts
     */
    private fun extractVendor(lines: List<String>, textBlocks: List<TextBlock>): VendorData? {
        val potentialVendors = mutableListOf<VendorCandidate>()
        
        // Focus on top portion of receipt (first 30% of lines)
        val topLines = lines.take((lines.size * 0.3).toInt().coerceAtLeast(3))
        
        topLines.forEachIndexed { index, line ->
            val cleanLine = line.trim()
            if (cleanLine.length < 3 || cleanLine.length > 50) return@forEachIndexed
            
            // Skip lines that are obviously not vendor names
            val lowerLine = cleanLine.lowercase()
            if (VENDOR_EXCLUDE_WORDS.any { lowerLine.contains(it) }) {
                return@forEachIndexed
            }
            
            // Skip lines that are mostly numbers or symbols
            val letterCount = cleanLine.count { it.isLetter() }
            if (letterCount < cleanLine.length * 0.5) {
                return@forEachIndexed
            }
            
            var confidence = 0.3f
            
            // Higher confidence for lines near the top
            confidence += (1.0f - (index.toFloat() / topLines.size)) * 0.3f
            
            // Higher confidence for lines with business keywords
            if (VENDOR_KEYWORDS.any { lowerLine.contains(it) }) {
                confidence += 0.2f
            }
            
            // Higher confidence for properly capitalized text
            if (cleanLine.split(" ").all { word -> word.firstOrNull()?.isUpperCase() == true }) {
                confidence += 0.1f
            }
            
            // Higher confidence for lines with reasonable length
            if (cleanLine.length in 8..25) {
                confidence += 0.1f
            }
            
            potentialVendors.add(
                VendorCandidate(
                    name = cleanLine,
                    confidence = confidence.coerceAtMost(1.0f),
                    lineIndex = index
                )
            )
        }
        
        return potentialVendors
            .sortedByDescending { it.confidence }
            .firstOrNull()
            ?.let { candidate ->
                VendorData(
                    name = candidate.name,
                    confidence = candidate.confidence,
                    lineIndex = candidate.lineIndex
                )
            }
    }
    
    /**
     * Extract date from receipt text
     * Looks for various date formats throughout the receipt
     */
    private fun extractDate(fullText: String, lines: List<String>): DateData? {
        val potentialDates = mutableListOf<DateCandidate>()
        
        lines.forEach { line ->
            DATE_PATTERNS.forEach { pattern ->
                val matcher = pattern.matcher(line)
                while (matcher.find()) {
                    try {
                        val dateStr = matcher.group()
                        val parsedDate = parseDate(dateStr)
                        
                        if (parsedDate != null) {
                            var confidence = 0.7f
                            
                            // Higher confidence for recent dates (within last 30 days)
                            val daysDifference = (System.currentTimeMillis() - parsedDate.time) / (24 * 60 * 60 * 1000)
                            if (daysDifference in 0..30) {
                                confidence += 0.2f
                            }
                            
                            potentialDates.add(
                                DateCandidate(
                                    date = parsedDate,
                                    confidence = confidence.coerceAtMost(1.0f),
                                    originalText = dateStr
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse date: ${matcher.group()}")
                    }
                }
            }
        }
        
        return potentialDates
            .sortedByDescending { it.confidence }
            .firstOrNull()
            ?.let { candidate ->
                DateData(
                    date = candidate.date,
                    confidence = candidate.confidence,
                    originalText = candidate.originalText
                )
            }
    }
    
    /**
     * Parse date string using multiple format attempts
     */
    private fun parseDate(dateStr: String): Date? {
        val formats = listOf(
            "MM/dd/yyyy", "dd/MM/yyyy", "MM-dd-yyyy", "dd-MM-yyyy",
            "yyyy/MM/dd", "yyyy-MM-dd", "dd.MM.yyyy", "MM.dd.yyyy",
            "dd-MMM-yyyy", "MMM dd, yyyy", "dd MMM yyyy"
        )
        
        formats.forEach { formatStr ->
            try {
                val formatter = SimpleDateFormat(formatStr, Locale.US)
                formatter.isLenient = false
                return formatter.parse(dateStr)
            } catch (e: Exception) {
                // Try next format
            }
        }
        
        return null
    }
    
    /**
     * Calculate overall extraction confidence based on individual component confidences
     */
    private fun calculateExtractionConfidence(
        amount: AmountData?, 
        vendor: VendorData?, 
        date: DateData?,
        ocrConfidence: Float
    ): Float {
        val componentConfidences = listOfNotNull(
            amount?.confidence,
            vendor?.confidence,
            date?.confidence
        )
        
        return if (componentConfidences.isNotEmpty()) {
            val avgComponentConfidence = componentConfidences.average().toFloat()
            // Combine OCR confidence with extraction confidence
            (ocrConfidence * 0.4f + avgComponentConfidence * 0.6f)
        } else {
            ocrConfidence * 0.3f // Low confidence if no data extracted
        }
    }
    
    // Helper data classes for internal processing
    private data class AmountCandidate(
        val amount: BigDecimal,
        val currency: String,
        val confidence: Float,
        val context: String
    )
    
    private data class VendorCandidate(
        val name: String,
        val confidence: Float,
        val lineIndex: Int
    )
    
    private data class DateCandidate(
        val date: Date,
        val confidence: Float,
        val originalText: String
    )
}