package com.pharadev.receiptscanner.presentation.viewmodel

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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReceiptListViewModel @Inject constructor(
    private val receiptRepository: ReceiptRepository,
    private val categoryRepository: CategoryRepository
) : BaseViewModel() {
    
    private val _receipts = MutableStateFlow<List<ReceiptDomain>>(emptyList())
    val receipts: StateFlow<List<ReceiptDomain>> = _receipts.asStateFlow()
    
    private val _categories = MutableStateFlow<List<CategoryDomain>>(emptyList())
    val categories: StateFlow<List<CategoryDomain>> = _categories.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _selectedCategory = MutableStateFlow<CategoryDomain?>(null)
    val selectedCategory: StateFlow<CategoryDomain?> = _selectedCategory.asStateFlow()
    
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    
    init {
        loadCategories()
        loadReceipts()
    }
    
    private fun loadCategories() {
        viewModelScope.launch {
            categoryRepository.getAllCategories().collect { categoryList ->
                _categories.value = categoryList
            }
        }
    }
    
    private fun loadReceipts() {
        viewModelScope.launch {
            combine(
                _searchQuery,
                _selectedCategory
            ) { query, category ->
                query to category
            }.collect { (query, category) ->
                when {
                    query.isNotBlank() -> {
                        receiptRepository.searchReceipts(query).collect { searchResults ->
                            _receipts.value = searchResults
                        }
                    }
                    category != null -> {
                        receiptRepository.getReceiptsByCategory(category.id).collect { categoryReceipts ->
                            _receipts.value = categoryReceipts
                        }
                    }
                    else -> {
                        receiptRepository.getAllReceipts().collect { allReceipts ->
                            _receipts.value = allReceipts
                        }
                    }
                }
            }
        }
    }
    
    fun searchReceipts(query: String) {
        _searchQuery.value = query
    }
    
    fun filterByCategory(category: CategoryDomain?) {
        _selectedCategory.value = category
        _searchQuery.value = "" // Clear search when filtering by category
    }
    
    fun clearFilters() {
        _selectedCategory.value = null
        _searchQuery.value = ""
    }
    
    fun refreshReceipts() {
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                // Force reload by clearing and reloading
                _receipts.value = emptyList()
                loadReceipts()
            } finally {
                _isRefreshing.value = false
            }
        }
    }
    
    fun deleteReceipt(receipt: ReceiptDomain) {
        launchWithLoading {
            receiptRepository.deleteReceipt(receipt)
        }
    }
}