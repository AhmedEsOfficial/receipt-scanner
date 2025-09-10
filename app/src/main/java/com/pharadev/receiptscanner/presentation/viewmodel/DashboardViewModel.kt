package com.pharadev.receiptscanner.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import com.pharadev.receiptscanner.domain.model.DashboardStatistics
import com.pharadev.receiptscanner.domain.repository.ReceiptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val receiptRepository: ReceiptRepository
) : BaseViewModel() {
    
    private val _statistics = MutableStateFlow<DashboardStatistics?>(null)
    val statistics: StateFlow<DashboardStatistics?> = _statistics.asStateFlow()
    
    init {
        loadDashboardStatistics()
    }
    
    fun loadDashboardStatistics() {
        launchWithLoading {
            val currentMonth = getCurrentMonthDateRange()
            val stats = receiptRepository.getDashboardStatistics(
                startDate = currentMonth.first,
                endDate = currentMonth.second
            )
            _statistics.value = stats
        }
    }
    
    fun refreshData() {
        loadDashboardStatistics()
    }
    
    private fun getCurrentMonthDateRange(): Pair<LocalDate, LocalDate> {
        val now = LocalDate.now()
        val firstDayOfMonth = now.withDayOfMonth(1)
        val lastDayOfMonth = now.withDayOfMonth(now.lengthOfMonth())
        return firstDayOfMonth to lastDayOfMonth
    }
}