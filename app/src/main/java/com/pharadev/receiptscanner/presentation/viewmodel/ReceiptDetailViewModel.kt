package com.pharadev.receiptscanner.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
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
import javax.inject.Inject

@HiltViewModel
class ReceiptDetailViewModel @Inject constructor(
    private val receiptRepository: ReceiptRepository,
    private val categoryRepository: CategoryRepository,
    savedStateHandle: SavedStateHandle
) : BaseViewModel() {
    
    private val receiptId: Long = savedStateHandle.get<Long>("receiptId") ?: -1
    
    private val _receipt = MutableStateFlow<ReceiptDomain?>(null)
    val receipt: StateFlow<ReceiptDomain?> = _receipt.asStateFlow()
    
    private val _categories = MutableStateFlow<List<CategoryDomain>>(emptyList())
    val categories: StateFlow<List<CategoryDomain>> = _categories.asStateFlow()
    
    private val _isEditing = MutableStateFlow(false)
    val isEditing: StateFlow<Boolean> = _isEditing.asStateFlow()
    
    private val _editableVendor = MutableStateFlow("")
    val editableVendor: StateFlow<String> = _editableVendor.asStateFlow()
    
    private val _editableAmount = MutableStateFlow("")
    val editableAmount: StateFlow<String> = _editableAmount.asStateFlow()
    
    private val _editableDate = MutableStateFlow<LocalDate?>(null)
    val editableDate: StateFlow<LocalDate?> = _editableDate.asStateFlow()
    
    private val _editableNotes = MutableStateFlow("")
    val editableNotes: StateFlow<String> = _editableNotes.asStateFlow()
    
    private val _editableCategory = MutableStateFlow<CategoryDomain?>(null)
    val editableCategory: StateFlow<CategoryDomain?> = _editableCategory.asStateFlow()
    
    init {
        loadReceipt()
        loadCategories()
    }
    
    private fun loadReceipt() {
        if (receiptId != -1L) {
            launchWithLoading {
                val receiptData = receiptRepository.getReceiptById(receiptId)
                _receipt.value = receiptData
                receiptData?.let { populateEditableFields(it) }
            }
        }
    }
    
    private fun loadCategories() {
        viewModelScope.launch {
            categoryRepository.getAllCategories().collect { categoryList ->
                _categories.value = categoryList
            }
        }
    }
    
    private fun populateEditableFields(receipt: ReceiptDomain) {
        _editableVendor.value = receipt.vendor
        _editableAmount.value = receipt.amount.toString()
        _editableDate.value = receipt.date
        _editableNotes.value = receipt.notes ?: ""
        _editableCategory.value = receipt.category
    }
    
    fun startEditing() {
        _isEditing.value = true
        _receipt.value?.let { populateEditableFields(it) }
    }
    
    fun cancelEditing() {
        _isEditing.value = false
        _receipt.value?.let { populateEditableFields(it) }
    }
    
    fun updateVendor(vendor: String) {
        _editableVendor.value = vendor
    }
    
    fun updateAmount(amount: String) {
        _editableAmount.value = amount
    }
    
    fun updateDate(date: LocalDate) {
        _editableDate.value = date
    }
    
    fun updateNotes(notes: String) {
        _editableNotes.value = notes
    }
    
    fun updateCategory(category: CategoryDomain) {
        _editableCategory.value = category
    }
    
    fun saveChanges() {
        val currentReceipt = _receipt.value ?: return
        val category = _editableCategory.value ?: return
        
        try {
            val amount = BigDecimal(_editableAmount.value)
            val date = _editableDate.value ?: LocalDate.now()
            
            val updatedReceipt = currentReceipt.copy(
                vendor = _editableVendor.value.trim(),
                amount = amount,
                date = date,
                category = category,
                notes = _editableNotes.value.trim().takeIf { it.isNotEmpty() },
                modifiedAt = LocalDateTime.now()
            )
            
            launchWithLoading {
                receiptRepository.updateReceipt(updatedReceipt)
                _receipt.value = updatedReceipt
                _isEditing.value = false
            }
        } catch (e: NumberFormatException) {
            // Handle invalid amount format
        }
    }
    
    fun deleteReceipt() {
        val currentReceipt = _receipt.value ?: return
        launchWithLoading {
            receiptRepository.deleteReceipt(currentReceipt)
        }
    }
}