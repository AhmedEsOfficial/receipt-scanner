package com.pharadev.receiptscanner.data.repository

import com.pharadev.receiptscanner.data.database.dao.CategoryDao
import com.pharadev.receiptscanner.data.repository.mappers.toDomain
import com.pharadev.receiptscanner.data.repository.mappers.toEntity
import com.pharadev.receiptscanner.domain.model.CategoryDomain
import com.pharadev.receiptscanner.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao
) : CategoryRepository {
    
    override fun getAllCategories(): Flow<List<CategoryDomain>> {
        return categoryDao.getAllCategories().map { categories ->
            categories.map { it.toDomain() }
        }
    }
    
    override suspend fun getCategoryById(id: Long): CategoryDomain? {
        return categoryDao.getCategoryById(id)?.toDomain()
    }
    
    override suspend fun getCategoryByName(name: String): CategoryDomain? {
        return categoryDao.getCategoryByName(name)?.toDomain()
    }
    
    override suspend fun insertCategory(category: CategoryDomain): Long {
        return categoryDao.insertCategory(category.toEntity())
    }
    
    override suspend fun updateCategory(category: CategoryDomain) {
        categoryDao.updateCategory(category.toEntity())
    }
    
    override suspend fun deleteCategory(category: CategoryDomain) {
        if (!category.isDefault) {
            categoryDao.deleteCategory(category.toEntity())
        }
    }
}