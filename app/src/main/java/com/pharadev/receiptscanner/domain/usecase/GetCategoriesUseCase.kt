package com.pharadev.receiptscanner.domain.usecase

import com.pharadev.receiptscanner.domain.model.CategoryDomain
import com.pharadev.receiptscanner.domain.service.CategoryService
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetCategoriesUseCase @Inject constructor(
    private val categoryService: CategoryService
) {
    operator fun invoke(): Flow<List<CategoryDomain>> {
        return categoryService.getAllCategories()
    }
}