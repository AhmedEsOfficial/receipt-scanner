package com.pharadev.receiptscanner.domain.repository

import com.pharadev.receiptscanner.domain.model.CategoryDomain
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    
    fun getAllCategories(): Flow<List<CategoryDomain>>
    
    suspend fun getCategoryById(id: Long): CategoryDomain?
    
    suspend fun getCategoryByName(name: String): CategoryDomain?
    
    suspend fun insertCategory(category: CategoryDomain): Long
    
    suspend fun updateCategory(category: CategoryDomain)
    
    suspend fun deleteCategory(category: CategoryDomain)
}