package com.pharadev.receiptscanner.domain.service

import com.pharadev.receiptscanner.domain.model.CategoryDomain
import com.pharadev.receiptscanner.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryService @Inject constructor(
    private val categoryRepository: CategoryRepository
) {
    
    companion object {
        private val DEFAULT_CATEGORIES = listOf(
            CategoryDomain(
                id = 1L,
                name = "Groceries",
                color = "#4CAF50",
                icon = "grocery_cart",
                isDefault = true
            ),
            CategoryDomain(
                id = 2L,
                name = "Fuel",
                color = "#FF9800",
                icon = "local_gas_station",
                isDefault = true
            ),
            CategoryDomain(
                id = 3L,
                name = "Restaurants",
                color = "#F44336",
                icon = "restaurant",
                isDefault = true
            ),
            CategoryDomain(
                id = 4L,
                name = "Transportation",
                color = "#2196F3",
                icon = "directions_car",
                isDefault = true
            ),
            CategoryDomain(
                id = 5L,
                name = "Healthcare",
                color = "#E91E63",
                icon = "medical_services",
                isDefault = true
            ),
            CategoryDomain(
                id = 6L,
                name = "Entertainment",
                color = "#9C27B0",
                icon = "movie",
                isDefault = true
            ),
            CategoryDomain(
                id = 7L,
                name = "Shopping",
                color = "#FF5722",
                icon = "shopping_bag",
                isDefault = true
            ),
            CategoryDomain(
                id = 8L,
                name = "Utilities",
                color = "#607D8B",
                icon = "build",
                isDefault = true
            )
        )
    }

    suspend fun initializeDefaultCategories() {
        val existingCategories = categoryRepository.getAllCategories().first()
        
        if (existingCategories.isEmpty()) {
            DEFAULT_CATEGORIES.forEach { category ->
                categoryRepository.insertCategory(category)
            }
        }
    }

    fun getAllCategories(): Flow<List<CategoryDomain>> {
        return categoryRepository.getAllCategories()
    }

    suspend fun getCategoryById(id: Long): CategoryDomain? {
        return categoryRepository.getCategoryById(id)
    }

    suspend fun getCategoryByName(name: String): CategoryDomain? {
        return categoryRepository.getCategoryByName(name)
    }

    suspend fun addCustomCategory(name: String, color: String, icon: String): Long {
        val maxId = categoryRepository.getAllCategories().first()
            .maxOfOrNull { it.id } ?: 0L
        
        val customCategory = CategoryDomain(
            id = maxId + 1,
            name = name,
            color = color,
            icon = icon,
            isDefault = false
        )
        
        return categoryRepository.insertCategory(customCategory)
    }

    suspend fun updateCategory(category: CategoryDomain) {
        categoryRepository.updateCategory(category)
    }

    suspend fun deleteCustomCategory(category: CategoryDomain) {
        if (!category.isDefault) {
            categoryRepository.deleteCategory(category)
        }
    }

    fun getDefaultCategories(): List<CategoryDomain> {
        return DEFAULT_CATEGORIES
    }
}