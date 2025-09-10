package com.pharadev.receiptscanner.domain.repository

import com.pharadev.receiptscanner.domain.model.DashboardStatistics
import com.pharadev.receiptscanner.domain.model.ReceiptDomain
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDate

interface ReceiptRepository {
    
    fun getAllReceipts(): Flow<List<ReceiptDomain>>
    
    fun getReceiptsPaged(limit: Int, offset: Int): Flow<List<ReceiptDomain>>
    
    suspend fun getReceiptById(id: Long): ReceiptDomain?
    
    fun getReceiptsByCategory(categoryId: Long): Flow<List<ReceiptDomain>>
    
    fun searchReceipts(query: String): Flow<List<ReceiptDomain>>
    
    fun getReceiptsByDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<ReceiptDomain>>
    
    suspend fun getTotalAmountByDateRange(startDate: LocalDate, endDate: LocalDate): BigDecimal
    
    suspend fun getReceiptCountByDateRange(startDate: LocalDate, endDate: LocalDate): Int
    
    suspend fun getTopCategoryByDateRange(startDate: LocalDate, endDate: LocalDate): Long?
    
    suspend fun getRecentReceipts(limit: Int): List<ReceiptDomain>
    
    suspend fun getDashboardStatistics(startDate: LocalDate, endDate: LocalDate): DashboardStatistics
    
    suspend fun insertReceipt(receipt: ReceiptDomain): Long
    
    suspend fun updateReceipt(receipt: ReceiptDomain)
    
    suspend fun deleteReceipt(receipt: ReceiptDomain)
    
    suspend fun deleteReceiptById(id: Long)
}