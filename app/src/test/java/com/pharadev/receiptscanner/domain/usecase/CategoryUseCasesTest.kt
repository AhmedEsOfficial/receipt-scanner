package com.pharadev.receiptscanner.domain.usecase

import com.pharadev.receiptscanner.domain.model.CategoryDomain
import com.pharadev.receiptscanner.domain.service.AutoCategorizationService
import com.pharadev.receiptscanner.domain.service.CategoryService
import com.pharadev.receiptscanner.domain.service.CategorySuggestion
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class CategoryUseCasesTest {

    @MockK
    private lateinit var categoryService: CategoryService

    @MockK
    private lateinit var autoCategorizationService: AutoCategorizationService

    private lateinit var getCategoriesUseCase: GetCategoriesUseCase
    private lateinit var suggestCategoryUseCase: SuggestCategoryUseCase
    private lateinit var learnFromCategoryCorrectionUseCase: LearnFromCategoryCorrectionUseCase
    private lateinit var initializeCategoriesUseCase: InitializeCategoriesUseCase

    private val testCategories = listOf(
        CategoryDomain(1L, "Groceries", "#4CAF50", "grocery_cart", true),
        CategoryDomain(2L, "Fuel", "#FF9800", "local_gas_station", true)
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        
        getCategoriesUseCase = GetCategoriesUseCase(categoryService)
        suggestCategoryUseCase = SuggestCategoryUseCase(autoCategorizationService)
        learnFromCategoryCorrectionUseCase = LearnFromCategoryCorrectionUseCase(autoCategorizationService)
        initializeCategoriesUseCase = InitializeCategoriesUseCase(categoryService)
    }

    @Test
    fun `GetCategoriesUseCase should return categories from service`() = runTest {
        // Given
        coEvery { categoryService.getAllCategories() } returns flowOf(testCategories)

        // When
        val result = getCategoriesUseCase()

        // Then
        result.collect { categories ->
            assertEquals(2, categories.size)
            assertEquals("Groceries", categories[0].name)
            assertEquals("Fuel", categories[1].name)
        }
    }

    @Test
    fun `SuggestCategoryUseCase should return suggestion from service`() = runTest {
        // Given
        val expectedSuggestion = CategorySuggestion(
            category = testCategories[0],
            confidence = 0.85,
            reason = "Keyword matching"
        )
        coEvery { 
            autoCategorizationService.suggestCategory("walmart receipt", "walmart", 45.67) 
        } returns expectedSuggestion

        // When
        val result = suggestCategoryUseCase("walmart receipt", "walmart", 45.67)

        // Then
        assertEquals(testCategories[0], result.category)
        assertEquals(0.85, result.confidence)
        assertEquals("Keyword matching", result.reason)
    }

    @Test
    fun `SuggestCategoryUseCase should handle null parameters correctly`() = runTest {
        // Given
        val expectedSuggestion = CategorySuggestion(
            category = testCategories[0],
            confidence = 0.75,
            reason = "OCR text analysis"
        )
        coEvery { 
            autoCategorizationService.suggestCategory("receipt text", null, null) 
        } returns expectedSuggestion

        // When
        val result = suggestCategoryUseCase("receipt text", null, null)

        // Then
        assertEquals(testCategories[0], result.category)
        assertEquals(0.75, result.confidence)
    }

    @Test
    fun `LearnFromCategoryCorrectionUseCase should call service with correct parameters`() = runTest {
        // Given
        val ocrText = "walmart receipt"
        val vendorName = "walmart"
        val originalSuggestion = testCategories[1] // Fuel
        val userSelection = testCategories[0] // Groceries

        coEvery { 
            autoCategorizationService.learnFromUserCorrection(
                ocrText, vendorName, originalSuggestion, userSelection
            ) 
        } returns Unit

        // When
        learnFromCategoryCorrectionUseCase(ocrText, vendorName, originalSuggestion, userSelection)

        // Then
        coVerify { 
            autoCategorizationService.learnFromUserCorrection(
                ocrText, vendorName, originalSuggestion, userSelection
            ) 
        }
    }

    @Test
    fun `LearnFromCategoryCorrectionUseCase should handle null vendor name`() = runTest {
        // Given
        val ocrText = "receipt text"
        val vendorName = null
        val originalSuggestion = null
        val userSelection = testCategories[0]

        coEvery { 
            autoCategorizationService.learnFromUserCorrection(
                ocrText, vendorName, originalSuggestion, userSelection
            ) 
        } returns Unit

        // When
        learnFromCategoryCorrectionUseCase(ocrText, vendorName, originalSuggestion, userSelection)

        // Then
        coVerify { 
            autoCategorizationService.learnFromUserCorrection(
                ocrText, null, null, userSelection
            ) 
        }
    }

    @Test
    fun `InitializeCategoriesUseCase should call service initialization`() = runTest {
        // Given
        coEvery { categoryService.initializeDefaultCategories() } returns Unit

        // When
        initializeCategoriesUseCase()

        // Then
        coVerify { categoryService.initializeDefaultCategories() }
    }
}