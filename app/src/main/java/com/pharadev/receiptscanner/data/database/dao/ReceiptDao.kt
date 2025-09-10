package com.pharadev.receiptscanner.data.database.dao

import androidx.room.*
import com.pharadev.receiptscanner.data.database.entities.Receipt
import com.pharadev.receiptscanner.data.database.entities.ReceiptWithCategory
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDate

@Dao
interface ReceiptDao {
    
    @Transaction
    @Query("SELECT * FROM receipts ORDER BY createdAt DESC")
    fun getAllReceiptsWithCategory(): Flow<List<ReceiptWithCategory>>
    
    @Transaction
    @Query("SELECT * FROM receipts ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    fun getReceiptsWithCategoryPaged(limit: Int, offset: Int): Flow<List<ReceiptWithCategory>>
    
    @Transaction
    @Query("SELECT * FROM receipts WHERE id = :id")
    suspend fun getReceiptWithCategoryById(id: Long): ReceiptWithCategory?
    
    @Query("SELECT * FROM receipts WHERE id = :id")
    suspend fun getReceiptById(id: Long): Receipt?
    
    @Transaction
    @Query("SELECT * FROM receipts WHERE categoryId = :categoryId ORDER BY createdAt DESC")
    fun getReceiptsByCategory(categoryId: Long): Flow<List<ReceiptWithCategory>>
    
    @Transaction
    @Query("""
        SELECT * FROM receipts 
        WHERE vendor LIKE '%' || :query || '%' 
        OR notes LIKE '%' || :query || '%'
        ORDER BY createdAt DESC 
        LIMIT 50
    """)
    fun searchReceipts(query: String): Flow<List<ReceiptWithCategory>>
    
    @Transaction
    @Query("""
        SELECT * FROM receipts 
        WHERE date BETWEEN :startDate AND :endDate 
        ORDER BY createdAt DESC
    """)
    fun getReceiptsByDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<ReceiptWithCategory>>
    
    @Query("SELECT SUM(amount) FROM receipts WHERE date BETWEEN :startDate AND :endDate")
    suspend fun getTotalAmountByDateRange(startDate: LocalDate, endDate: LocalDate): BigDecimal?
    
    @Query("SELECT COUNT(*) FROM receipts WHERE date BETWEEN :startDate AND :endDate")
    suspend fun getReceiptCountByDateRange(startDate: LocalDate, endDate: LocalDate): Int
    
    @Query("""
        SELECT categoryId 
        FROM receipts 
        WHERE date BETWEEN :startDate AND :endDate 
        GROUP BY categoryId 
        ORDER BY COUNT(*) DESC 
        LIMIT 1
    """)
    suspend fun getTopCategoryByDateRange(startDate: LocalDate, endDate: LocalDate): Long?
    
    @Query("SELECT * FROM receipts ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentReceipts(limit: Int): List<Receipt>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceipt(receipt: Receipt): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceipts(receipts: List<Receipt>)
    
    @Update
    suspend fun updateReceipt(receipt: Receipt)
    
    @Delete
    suspend fun deleteReceipt(receipt: Receipt)
    
    @Query("DELETE FROM receipts WHERE id = :id")
    suspend fun deleteReceiptById(id: Long)
}