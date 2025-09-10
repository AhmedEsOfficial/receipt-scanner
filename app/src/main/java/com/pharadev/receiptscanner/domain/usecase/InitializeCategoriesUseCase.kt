package com.pharadev.receiptscanner.domain.usecase

import com.pharadev.receiptscanner.domain.service.CategoryService
import javax.inject.Inject

class InitializeCategoriesUseCase @Inject constructor(
    private val categoryService: CategoryService
) {
    suspend operator fun invoke() {
        categoryService.initializeDefaultCategories()
    }
}