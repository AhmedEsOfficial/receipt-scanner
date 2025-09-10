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
        
        // Amount patterns - matches various currency formats (improved and more flexible)
        val AMOUNT_PATTERNS = listOf(
            // TOTAL/AMOUNT keywords with currency: TOTAL: $12.34, Amount €12,34
            "(?i)(?:total|amount|sum|subtotal|grand\\s*total)\\s*:?\\s*([${CURRENCY_SYMBOLS.joinToString("")}]?)\\s*([0-9]{1,6}[.,][0-9]{2})".toPattern(),
            // Currency symbol followed by amount: $12.34, €12,34, £123.45
            "([${CURRENCY_SYMBOLS.joinToString("")}])\\s*([0-9]{1,6}[.,]?[0-9]{0,3}[.,]?[0-9]{2})".toPattern(),
            // Amount with decimal: 12.34, 123.45, 1234.56 (strict decimal format)
            "\\b([0-9]{1,6}\\.[0-9]{2})\\b".toPattern(),
            // Amount with comma as decimal separator: 12,34, 123,45
            "\\b([0-9]{1,6},[0-9]{2})\\b".toPattern(),
            // Integer amounts (likely totals): 15, 25, 125 but not phone numbers or dates
            "(?<!\\d)([1-9][0-9]{1,4})(?![-/\\d])".toPattern(),
            // Amount at end of line (common for totals): 12.34 EOL
            "([0-9]{1,6}[.,][0-9]{2})\\s*$".toPattern(),
            // Amounts with thousands separators: 1,234.56 or 1.234,56
            "\\b([0-9]{1,3}[,.][0-9]{3}[.,][0-9]{2})\\b".toPattern()
        )
        
        // Date patterns - comprehensive formats (improved to catch more variations)
        val DATE_PATTERNS = listOf(
            // MM/dd/yyyy, MM/dd/yy, dd/MM/yyyy, dd/MM/yy (most common)
            "\\b([0-1]?[0-9])[/\\-.]([0-3]?[0-9])[/\\-.](20[0-9]{2}|[0-9]{2})\\b".toPattern(),
            // yyyy-MM-dd, yyyy/MM/dd (ISO format)
            "\\b(20[0-9]{2})[/\\-.]([0-1]?[0-9])[/\\-.]([0-3]?[0-9])\\b".toPattern(),
            // dd-MMM-yyyy, dd MMM yyyy (e.g., 15-Jan-2023, 15 Jan 2023)
            "\\b([0-3]?[0-9])\\s*[/\\-.]?\\s*([A-Za-z]{3,9})\\s*[/\\-.]?\\s*(20[0-9]{2}|[0-9]{2})\\b".toPattern(),
            // MMM dd, yyyy, MMM dd yyyy (e.g., Jan 15, 2023, Jan 15 2023)
            "\\b([A-Za-z]{3,9})\\s+([0-3]?[0-9]),?\\s+(20[0-9]{2}|[0-9]{2})\\b".toPattern(),
            // Month dd, yyyy (e.g., January 15, 2023)
            "\\b([A-Za-z]{3,9})\\s+([0-3]?[0-9]),?\\s+(20[0-9]{2})\\b".toPattern(),
            // dd Month yyyy (e.g., 15 January 2023)
            "\\b([0-3]?[0-9])\\s+([A-Za-z]{3,9})\\s+(20[0-9]{2})\\b".toPattern(),
            // Compact formats: ddmmyyyy, ddmmyy, mmddyyyy, mmddyy
            "\\b([0-3][0-9])([0-1][0-9])(20[0-9]{2}|[0-9]{2})\\b".toPattern(),
            // Time-date combinations: MM/dd/yyyy HH:mm, dd/MM/yyyy HH:mm
            "\\b([0-1]?[0-9])[/\\-.]([0-3]?[0-9])[/\\-.](20[0-9]{2}|[0-9]{2})\\s+([0-2]?[0-9]):([0-5][0-9])".toPattern()
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
     * Extract monetary amount from receipt text (improved with better pattern handling)
     * Prioritizes larger amounts and those with "total" keywords
     */
    private fun extractAmount(fullText: String, lines: List<String>, textBlocks: List<TextBlock>): AmountData? {
        val potentialAmounts = mutableListOf<AmountCandidate>()
        
        // Search through all lines for amount patterns
        lines.forEachIndexed { lineIndex, line ->
            AMOUNT_PATTERNS.forEachIndexed { patternIndex, pattern ->
                val matcher = pattern.matcher(line)
                while (matcher.find()) {
                    try {
                        val matchedText = matcher.group()
                        var currencySymbol = ""
                        var amountText = matchedText
                        
                        // Handle different pattern types
                        when (patternIndex) {
                            0 -> { // Total/Amount keywords
                                currencySymbol = matcher.group(1) ?: ""
                                amountText = matcher.group(2)
                            }
                            1 -> { // Currency symbol patterns
                                currencySymbol = matcher.group(1)
                                amountText = matcher.group(2)
                            }
                            2, 3, 5, 6 -> { // Decimal amounts and end-of-line amounts
                                amountText = matcher.group(1)
                            }
                            4 -> { // Integer amounts (no decimal)
                                amountText = matcher.group(1) + ".00"
                            }
                        }
                        
                        // Normalize amount text (handle both comma and dot as decimal separator)
                        val normalizedAmount = normalizeAmountText(amountText)
                        val amount = BigDecimal(normalizedAmount)
                        
                        // Skip unreasonably large or small amounts
                        if (amount < BigDecimal("0.01") || amount > BigDecimal("999999.99")) {
                            continue
                        }
                        
                        // Calculate confidence based on context and pattern type
                        var confidence = 0.4f
                        val lowerLine = line.lowercase()
                        
                        // Higher confidence for total/amount keyword patterns
                        if (patternIndex == 0) {
                            confidence += 0.4f
                        }
                        
                        // Higher confidence for currency symbol patterns
                        if (patternIndex == 1) {
                            confidence += 0.2f
                        }
                        
                        // Higher confidence for lines with total-related keywords
                        when {
                            lowerLine.contains("total") -> confidence += 0.3f
                            lowerLine.contains("amount") || lowerLine.contains("sum") -> confidence += 0.25f
                            lowerLine.contains("subtotal") || lowerLine.contains("grand") -> confidence += 0.2f
                        }
                        
                        // Higher confidence for amounts near bottom of receipt (likely totals)
                        if (lineIndex > lines.size * 0.7) {
                            confidence += 0.15f
                        }
                        
                        // Higher confidence for reasonable amounts
                        when {
                            amount >= BigDecimal("5.00") -> confidence += 0.1f
                            amount >= BigDecimal("1.00") -> confidence += 0.05f
                        }
                        
                        // Lower confidence for very small amounts (likely not totals)
                        if (amount < BigDecimal("1.00")) {
                            confidence -= 0.2f
                        }
                        
                        potentialAmounts.add(
                            AmountCandidate(
                                amount = amount,
                                currency = currencySymbol.takeIf { it.isNotEmpty() } ?: "$",
                                confidence = confidence.coerceIn(0.0f, 1.0f),
                                context = line.trim()
                            )
                        )
                        
                    } catch (e: NumberFormatException) {
                        Log.w(TAG, "Failed to parse amount: ${matcher.group()}")
                    } catch (e: Exception) {
                        Log.w(TAG, "Error processing amount pattern: ${matcher.group()}", e)
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
     * Normalize amount text to handle different decimal separators and formats
     */
    private fun normalizeAmountText(amountText: String): String {
        var normalized = amountText.trim()
        
        // Handle thousands separators: 1,234.56 -> 1234.56, 1.234,56 -> 1234.56
        val commaCount = normalized.count { it == ',' }
        val dotCount = normalized.count { it == '.' }
        
        when {
            // Format: 1,234.56 (US format with thousands separator)
            commaCount == 1 && dotCount == 1 && normalized.indexOf(',') < normalized.indexOf('.') -> {
                normalized = normalized.replace(",", "")
            }
            // Format: 1.234,56 (European format with thousands separator)
            commaCount == 1 && dotCount == 1 && normalized.indexOf('.') < normalized.indexOf(',') -> {
                normalized = normalized.replace(".", "").replace(",", ".")
            }
            // Format: 12,34 (European decimal separator)
            commaCount == 1 && dotCount == 0 -> {
                normalized = normalized.replace(",", ".")
            }
            // Format: 1,234 (could be thousands or decimal)
            commaCount == 1 && dotCount == 0 -> {
                val parts = normalized.split(",")
                if (parts.size == 2 && parts[1].length == 2) {
                    // Likely decimal: 12,34 -> 12.34
                    normalized = normalized.replace(",", ".")
                } else {
                    // Likely thousands: 1,234 -> 1234.00
                    normalized = normalized.replace(",", "") + ".00"
                }
            }
            // Multiple commas: 1,234,567.89
            commaCount > 1 -> {
                val lastCommaIndex = normalized.lastIndexOf(',')
                val lastDotIndex = normalized.lastIndexOf('.')
                if (lastDotIndex > lastCommaIndex) {
                    // Remove all commas (thousands separators)
                    normalized = normalized.replace(",", "")
                } else {
                    // Last comma is decimal separator
                    normalized = normalized.substring(0, lastCommaIndex).replace(",", "") + 
                                "." + normalized.substring(lastCommaIndex + 1)
                }
            }
        }
        
        return normalized
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
     * Parse date string using multiple format attempts (enhanced with more formats)
     */
    private fun parseDate(dateStr: String): Date? {
        val formats = listOf(
            // Common slash formats
            "MM/dd/yyyy", "dd/MM/yyyy", "MM/dd/yy", "dd/MM/yy",
            "M/d/yyyy", "d/M/yyyy", "M/d/yy", "d/M/yy",
            // Common dash formats  
            "MM-dd-yyyy", "dd-MM-yyyy", "MM-dd-yy", "dd-MM-yy",
            "M-d-yyyy", "d-M-yyyy", "M-d-yy", "d-M-yy",
            // ISO formats
            "yyyy-MM-dd", "yyyy/MM/dd", "yyyy.MM.dd",
            // Dot formats
            "dd.MM.yyyy", "MM.dd.yyyy", "dd.MM.yy", "MM.dd.yy",
            // Month name formats
            "dd-MMM-yyyy", "dd MMM yyyy", "dd-MMM-yy", "dd MMM yy",
            "MMM dd, yyyy", "MMM dd yyyy", "MMM dd, yy", "MMM dd yy",
            "MMMM dd, yyyy", "MMMM dd yyyy", "dd MMMM yyyy",
            // Compact formats
            "ddMMyyyy", "ddMMyy", "MMddyyyy", "MMddyy",
            "yyyyMMdd", "yyMMdd",
            // With time
            "MM/dd/yyyy HH:mm", "dd/MM/yyyy HH:mm", "yyyy-MM-dd HH:mm",
            "MM/dd/yyyy hh:mm a", "dd/MM/yyyy hh:mm a"
        )
        
        formats.forEach { formatStr ->
            try {
                val formatter = SimpleDateFormat(formatStr, Locale.US)
                formatter.isLenient = false
                
                // Handle 2-digit years properly (assume 2000s for years 00-30, 1900s for 31-99)
                if (formatStr.contains("yy") && !formatStr.contains("yyyy")) {
                    formatter.set2DigitYearStart(Date(70, 0, 1)) // Start from 1970
                }
                
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