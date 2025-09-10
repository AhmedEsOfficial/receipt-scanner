package com.pharadev.receiptscanner.data.repository

import com.pharadev.receiptscanner.data.database.dao.CategoryDao
import com.pharadev.receiptscanner.data.database.dao.ReceiptDao
import com.pharadev.receiptscanner.data.repository.mappers.toDomain
import com.pharadev.receiptscanner.data.repository.mappers.toEntity
import com.pharadev.receiptscanner.domain.model.DashboardStatistics
import com.pharadev.receiptscanner.domain.model.ReceiptDomain
import com.pharadev.receiptscanner.domain.repository.ReceiptRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReceiptRepositoryImpl @Inject constructor(
    private val receiptDao: ReceiptDao,
    private val categoryDao: CategoryDao
) : ReceiptRepository {
    
    override fun getAllReceipts(): Flow<List<ReceiptDomain>> {
        return receiptDao.getAllReceiptsWithCategory().map { receipts ->
            receipts.map { it.toDomain() }
        }
    }
    
    override fun getReceiptsPaged(limit: Int, offset: Int): Flow<List<ReceiptDomain>> {
        return receiptDao.getReceiptsWithCategoryPaged(limit, offset).map { receipts ->
            receipts.map { it.toDomain() }
        }
    }
    
    override suspend fun getReceiptById(id: Long): ReceiptDomain? {
        return receiptDao.getReceiptWithCategoryById(id)?.toDomain()
    }
    
    override fun getReceiptsByCategory(categoryId: Long): Flow<List<ReceiptDomain>> {
        return receiptDao.getReceiptsByCategory(categoryId).map { receipts ->
            receipts.map { it.toDomain() }
        }
    }
    
    override fun searchReceipts(query: String): Flow<List<ReceiptDomain>> {
        return receiptDao.searchReceipts(query).map { receipts ->
            receipts.map { it.toDomain() }
        }
    }
    
    override fun getReceiptsByDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<ReceiptDomain>> {
        return receiptDao.getReceiptsByDateRange(startDate, endDate).map { receipts ->
            receipts.map { it.toDomain() }
        }
    }
    
    override suspend fun getTotalAmountByDateRange(startDate: LocalDate, endDate: LocalDate): BigDecimal {
        return receiptDao.getTotalAmountByDateRange(startDate, endDate) ?: BigDecimal.ZERO
    }
    
    override suspend fun getReceiptCountByDateRange(startDate: LocalDate, endDate: LocalDate): Int {
        return receiptDao.getReceiptCountByDateRange(startDate, endDate)
    }
    
    override suspend fun getTopCategoryByDateRange(startDate: LocalDate, endDate: LocalDate): Long? {
        return receiptDao.getTopCategoryByDateRange(startDate, endDate)
    }
    
    override suspend fun getRecentReceipts(limit: Int): List<ReceiptDomain> {
        val receipts = receiptDao.getRecentReceipts(limit)
        return receipts.map { receipt ->
            val category = categoryDao.getCategoryById(receipt.categoryId)
            if (category != null) {
                ReceiptDomain(
                    id = receipt.id,
                    imagePath = receipt.imagePath,
                    vendor = receipt.vendor,
                    amount = receipt.amount,
                    date = receipt.date,
                    category = category.toDomain(),
                    notes = receipt.notes,
                    createdAt = receipt.createdAt,
                    modifiedAt = receipt.modifiedAt
                )
            } else {
                // Fallback to default category if category not found
                val defaultCategory = categoryDao.getCategoryById(8L)!!
                ReceiptDomain(
                    id = receipt.id,
                    imagePath = receipt.imagePath,
                    vendor = receipt.vendor,
                    amount = receipt.amount,
                    date = receipt.date,
                    category = defaultCategory.toDomain(),
                    notes = receipt.notes,
                    createdAt = receipt.createdAt,
                    modifiedAt = receipt.modifiedAt
                )
            }
        }
    }
    
    override suspend fun getDashboardStatistics(startDate: LocalDate, endDate: LocalDate): DashboardStatistics {
        val totalAmount = getTotalAmountByDateRange(startDate, endDate)
        val receiptCount = getReceiptCountByDateRange(startDate, endDate)
        val topCategoryId = getTopCategoryByDateRange(startDate, endDate)
        val topCategory = topCategoryId?.let { categoryDao.getCategoryById(it)?.toDomain() }
        val recentReceipts = getRecentReceipts(5)
        
        return DashboardStatistics(
            totalAmount = totalAmount,
            receiptCount = receiptCount,
            topCategory = topCategory,
            recentReceipts = recentReceipts
        )
    }
    
    override suspend fun insertReceipt(receipt: ReceiptDomain): Long {
        return receiptDao.insertReceipt(receipt.toEntity())
    }
    
    override suspend fun updateReceipt(receipt: ReceiptDomain) {
        receiptDao.updateReceipt(receipt.toEntity())
    }
    
    override suspend fun deleteReceipt(receipt: ReceiptDomain) {
        receiptDao.deleteReceipt(receipt.toEntity())
    }
    
    override suspend fun deleteReceiptById(id: Long) {
        receiptDao.deleteReceiptById(id)
    }
}