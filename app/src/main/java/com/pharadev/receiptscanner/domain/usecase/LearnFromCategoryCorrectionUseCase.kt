package com.pharadev.receiptscanner.domain.usecase

import com.pharadev.receiptscanner.domain.model.CategoryDomain
import com.pharadev.receiptscanner.domain.service.AutoCategorizationService
import javax.inject.Inject

class LearnFromCategoryCorrectionUseCase @Inject constructor(
    private val autoCategorizationService: AutoCategorizationService
) {
    suspend operator fun invoke(
        ocrText: String,
        vendorName: String?,
        originalSuggestion: CategoryDomain?,
        userSelectedCategory: CategoryDomain
    ) {
        return autoCategorizationService.learnFromUserCorrection(
            ocrText = ocrText,
            vendorName = vendorName,
            originalSuggestion = originalSuggestion,
            userSelectedCategory = userSelectedCategory
        )
    }
}