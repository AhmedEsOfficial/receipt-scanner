package com.pharadev.receiptscanner.domain.usecase

import com.pharadev.receiptscanner.domain.service.AutoCategorizationService
import com.pharadev.receiptscanner.domain.service.CategorySuggestion
import javax.inject.Inject

class SuggestCategoryUseCase @Inject constructor(
    private val autoCategorizationService: AutoCategorizationService
) {
    suspend operator fun invoke(
        ocrText: String,
        vendorName: String? = null,
        amount: Double? = null
    ): CategorySuggestion {
        return autoCategorizationService.suggestCategory(ocrText, vendorName, amount)
    }
}