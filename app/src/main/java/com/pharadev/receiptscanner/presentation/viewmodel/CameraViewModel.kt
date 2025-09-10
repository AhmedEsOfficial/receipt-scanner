package com.pharadev.receiptscanner.presentation.viewmodel

import android.net.Uri
import androidx.lifecycle.viewModelScope
import java.io.File
import com.pharadev.receiptscanner.data.ocr.OCRProcessingService
import com.pharadev.receiptscanner.data.ocr.ProcessingResult
import com.pharadev.receiptscanner.data.ocr.QualityAssessment
import com.pharadev.receiptscanner.domain.model.CategoryDomain
import com.pharadev.receiptscanner.domain.model.ReceiptDomain
import com.pharadev.receiptscanner.domain.repository.CategoryRepository
import com.pharadev.receiptscanner.domain.repository.ReceiptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import javax.inject.Inject

data class ExtractedReceiptData(
    val vendor: String = "",
    val amount: BigDecimal = BigDecimal.ZERO,
    val date: LocalDate = LocalDate.now(),
    val confidence: Float = 0.0f,
    val qualityAssessment: QualityAssessment = QualityAssessment.Poor,
    val processingTime: Long = 0L
)

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val receiptRepository: ReceiptRepository,
    private val categoryRepository: CategoryRepository,
    private val ocrProcessingService: OCRProcessingService
) : BaseViewModel() {
    
    private val _capturedImagePath = MutableStateFlow<String?>(null)
    val capturedImagePath: StateFlow<String?> = _capturedImagePath.asStateFlow()
    
    private val _extractedData = MutableStateFlow<ExtractedReceiptData?>(null)
    val extractedData: StateFlow<ExtractedReceiptData?> = _extractedData.asStateFlow()
    
    private val _categories = MutableStateFlow<List<CategoryDomain>>(emptyList())
    val categories: StateFlow<List<CategoryDomain>> = _categories.asStateFlow()
    
    private val _selectedCategory = MutableStateFlow<CategoryDomain?>(null)
    val selectedCategory: StateFlow<CategoryDomain?> = _selectedCategory.asStateFlow()
    
    private val _isProcessingOCR = MutableStateFlow(false)
    val isProcessingOCR: StateFlow<Boolean> = _isProcessingOCR.asStateFlow()
    
    private val _showManualEntry = MutableStateFlow(false)
    val showManualEntry: StateFlow<Boolean> = _showManualEntry.asStateFlow()
    
    // Manual entry fields
    private val _manualVendor = MutableStateFlow("")
    val manualVendor: StateFlow<String> = _manualVendor.asStateFlow()
    
    private val _manualAmount = MutableStateFlow("")
    val manualAmount: StateFlow<String> = _manualAmount.asStateFlow()
    
    private val _manualDate = MutableStateFlow(LocalDate.now())
    val manualDate: StateFlow<LocalDate> = _manualDate.asStateFlow()
    
    private val _manualNotes = MutableStateFlow("")
    val manualNotes: StateFlow<String> = _manualNotes.asStateFlow()
    
    init {
        loadCategories()
    }
    
    private fun loadCategories() {
        viewModelScope.launch {
            categoryRepository.getAllCategories().collect { categoryList ->
                _categories.value = categoryList
                // Set default category if none selected
                if (_selectedCategory.value == null && categoryList.isNotEmpty()) {
                    _selectedCategory.value = categoryList.find { it.name == "Other" } ?: categoryList.last()
                }
            }
        }
    }
    
    fun onImageCaptured(imagePath: String) {
        _capturedImagePath.value = imagePath
        processImageWithOCR(imagePath)
    }
    
    private fun processImageWithOCR(imagePath: String) {
        _isProcessingOCR.value = true
        viewModelScope.launch {
            try {
                val imageUri = if (imagePath.startsWith("content://") || imagePath.startsWith("file://")) {
                    Uri.parse(imagePath)
                } else {
                    // Create proper file URI for absolute file paths
                    Uri.fromFile(File(imagePath))
                }
                val processingResult = ocrProcessingService.processReceiptFromUri(imageUri)
                
                when (processingResult) {
                    is ProcessingResult.Success -> {
                        val extractedData = ExtractedReceiptData(
                            vendor = processingResult.extractedData.vendor?.getCleanName() ?: "",
                            amount = processingResult.extractedData.amount?.amount ?: BigDecimal.ZERO,
                            date = processingResult.extractedData.date?.date?.let { date ->
                                val calendar = Calendar.getInstance()
                                calendar.time = date
                                LocalDate.of(
                                    calendar.get(Calendar.YEAR),
                                    calendar.get(Calendar.MONTH) + 1,
                                    calendar.get(Calendar.DAY_OF_MONTH)
                                )
                            } ?: LocalDate.now(),
                            confidence = processingResult.extractedData.confidence,
                            qualityAssessment = ocrProcessingService.getQualityAssessment(processingResult),
                            processingTime = processingResult.statistics.totalProcessingTimeMs
                        )
                        
                        _extractedData.value = extractedData
                        
                        // Auto-suggest category based on vendor name
                        suggestCategory(extractedData.vendor)
                        
                        // Show manual entry if quality is poor or data is insufficient
                        val shouldShowManualEntry = ocrProcessingService.shouldUseManualEntry(processingResult) ||
                                                   extractedData.vendor.isBlank() || extractedData.amount <= BigDecimal.ZERO
                        if (shouldShowManualEntry) {
                            _showManualEntry.value = true
                            // Pre-populate manual fields with any extracted data
                            _manualVendor.value = extractedData.vendor
                            _manualAmount.value = if (extractedData.amount > BigDecimal.ZERO) extractedData.amount.toString() else ""
                            _manualDate.value = extractedData.date
                        }
                        
                    }
                    
                    is ProcessingResult.Error -> {
                        // OCR failed, show manual entry
                        _showManualEntry.value = true
                        handleError("OCR processing failed: ${processingResult.message}")
                    }
                    
                    is ProcessingResult.Processing -> {
                        // Still processing (shouldn't happen with current implementation)
                    }
                }
                
            } catch (e: Exception) {
                // OCR failed, show manual entry
                _showManualEntry.value = true
                handleError("OCR processing failed: ${e.message}")
            } finally {
                _isProcessingOCR.value = false
            }
        }
    }
    
    private suspend fun suggestCategory(vendor: String) {
        // Simple keyword-based category suggestion
        val categories = _categories.value
        val suggestedCategory = when {
            vendor.lowercase().contains("restaurant") || 
            vendor.lowercase().contains("cafe") ||
            vendor.lowercase().contains("food") -> categories.find { it.name == "Food & Dining" }
            
            vendor.lowercase().contains("grocery") ||
            vendor.lowercase().contains("market") ||
            vendor.lowercase().contains("supermarket") -> categories.find { it.name == "Groceries" }
            
            vendor.lowercase().contains("gas") ||
            vendor.lowercase().contains("fuel") ||
            vendor.lowercase().contains("uber") ||
            vendor.lowercase().contains("taxi") -> categories.find { it.name == "Transportation" }
            
            else -> categories.find { it.name == "Other" }
        }
        
        suggestedCategory?.let { _selectedCategory.value = it }
    }
    
    fun selectCategory(category: CategoryDomain) {
        _selectedCategory.value = category
    }
    
    fun showManualEntry() {
        _showManualEntry.value = true
        // Pre-populate manual fields with extracted data if available
        _extractedData.value?.let { extracted ->
            _manualVendor.value = extracted.vendor
            _manualAmount.value = extracted.amount.toString()
            _manualDate.value = extracted.date
        }
    }
    
    fun hideManualEntry() {
        _showManualEntry.value = false
        clearManualFields()
    }
    
    fun updateManualVendor(vendor: String) {
        _manualVendor.value = vendor
    }
    
    fun updateManualAmount(amount: String) {
        _manualAmount.value = amount
    }
    
    fun updateManualDate(date: LocalDate) {
        _manualDate.value = date
    }
    
    fun updateManualNotes(notes: String) {
        _manualNotes.value = notes
    }
    
    fun saveReceipt() {
        val imagePath = _capturedImagePath.value ?: return
        val category = _selectedCategory.value ?: return
        
        val vendor: String
        val amount: BigDecimal
        val date: LocalDate
        val notes: String?
        val needsReview: Boolean
        
        if (_showManualEntry.value) {
            // Use manual entry data
            vendor = _manualVendor.value.trim()
            amount = try {
                if (_manualAmount.value.isBlank()) BigDecimal.ZERO else BigDecimal(_manualAmount.value)
            } catch (e: NumberFormatException) {
                BigDecimal.ZERO
            }
            date = _manualDate.value
            notes = _manualNotes.value.trim().takeIf { it.isNotEmpty() }
            needsReview = vendor.isEmpty() || amount <= BigDecimal.ZERO
        } else {
            // Use extracted data
            val extracted = _extractedData.value
            vendor = extracted?.vendor?.takeIf { it.isNotBlank() } ?: "Unknown Vendor"
            amount = extracted?.amount?.takeIf { it > BigDecimal.ZERO } ?: BigDecimal.ZERO
            date = extracted?.date ?: LocalDate.now()
            needsReview = vendor == "Unknown Vendor" || amount <= BigDecimal.ZERO || 
                         extracted?.confidence ?: 0f < 0.5f
            notes = if (needsReview) "Needs review - OCR confidence: ${extracted?.confidence ?: 0f}" else null
        }
        
        // Create receipt even with incomplete data, but flag for review
        val receipt = ReceiptDomain(
            imagePath = imagePath,
            vendor = vendor,
            amount = amount,
            date = date,
            category = category,
            notes = notes,
            createdAt = LocalDateTime.now(),
            modifiedAt = LocalDateTime.now()
        )
        
        launchWithLoading {
            receiptRepository.insertReceipt(receipt)
            
            // Show feedback to user about data quality
            if (needsReview) {
                handleError("Receipt saved but needs review - some information may be incomplete")
            }
            
            clearState()
        }
    }
    
    private fun clearState() {
        _capturedImagePath.value = null
        _extractedData.value = null
        _showManualEntry.value = false
        clearManualFields()
    }
    
    private fun clearManualFields() {
        _manualVendor.value = ""
        _manualAmount.value = ""
        _manualDate.value = LocalDate.now()
        _manualNotes.value = ""
    }
    
    fun discardImage() {
        clearState()
    }
}