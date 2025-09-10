package com.pharadev.receiptscanner.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

abstract class BaseViewModel : ViewModel() {
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    protected val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        _error.value = exception.message ?: "An unknown error occurred"
        _isLoading.value = false
    }
    
    protected fun launchWithLoading(block: suspend () -> Unit) {
        viewModelScope.launch(exceptionHandler) {
            _isLoading.value = true
            _error.value = null
            try {
                block()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    protected fun clearError() {
        _error.value = null
    }
    
    protected fun handleError(message: String) {
        _error.value = message
    }
}