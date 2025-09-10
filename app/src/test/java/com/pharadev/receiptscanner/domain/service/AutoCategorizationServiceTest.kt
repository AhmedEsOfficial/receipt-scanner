package com.pharadev.receiptscanner.domain.service

import com.pharadev.receiptscanner.domain.model.CategoryDomain
import com.pharadev.receiptscanner.domain.repository.CategoryRepository
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AutoCategorizationServiceTest {

    @MockK
    private lateinit var categoryRepository: CategoryRepository

    @MockK
    private lateinit var categoryLearningService: CategoryLearningService

    private lateinit var autoCategorizationService: AutoCategorizationService

    private val groceriesCategory = CategoryDomain(1L, "Groceries", "#4CAF50", "grocery_cart", true)
    private val fuelCategory = CategoryDomain(2L, "Fuel", "#FF9800", "local_gas_station", true)
    private val restaurantsCategory = CategoryDomain(3L, "Restaurants", "#F44336", "restaurant", true)
    private val healthcareCategory = CategoryDomain(5L, "Healthcare", "#E91E63", "medical_services", true)

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        autoCategorizationService = AutoCategorizationService(categoryRepository, categoryLearningService)
    }

    @Test
    fun `suggestCategory should return learned suggestion when confidence is high`() = runTest {
        // Given
        val learnedSuggestion = CategorySuggestion(groceriesCategory, 0.8, "Learned from vendor")
        coEvery { 
            categoryLearningService.getSuggestedCategory("walmart", "walmart receipt total 45.67") 
        } returns learnedSuggestion

        // When
        val result = autoCategorizationService.suggestCategory(
            ocrText = "walmart receipt total 45.67",
            vendorName = "walmart"
        )

        // Then
        assertEquals(groceriesCategory, result.category)
        assertEquals(0.8, result.confidence)
        assertEquals("Learned from vendor", result.reason)
    }

    @Test
    fun `suggestCategory should use keyword matching when no learned suggestion`() = runTest {
        // Given
        coEvery { 
            categoryLearningService.getSuggestedCategory("unknown vendor", "receipt from grocery store") 
        } returns null
        coEvery { categoryRepository.getCategoryByName("Groceries") } returns groceriesCategory

        // When
        val result = autoCategorizationService.suggestCategory(
            ocrText = "receipt from grocery store",
            vendorName = "unknown vendor"
        )

        // Then
        assertEquals(groceriesCategory, result.category)
        assertTrue(result.confidence >= 0.7)
        assertEquals("Keyword matching", result.reason)
    }

    @Test
    fun `suggestCategory should identify grocery stores correctly`() = runTest {
        // Given
        coEvery { categoryLearningService.getSuggestedCategory(any(), any()) } returns null
        coEvery { categoryRepository.getCategoryByName("Groceries") } returns groceriesCategory

        val testCases = listOf(
            "WALMART STORE #1234" to "walmart",
            "TARGET T-1456" to "target",
            "TRADER JOES" to "trader joe",
            "Thank you for shopping at KROGER" to "kroger"
        )

        testCases.forEach { (ocrText, vendorName) ->
            // When
            val result = autoCategorizationService.suggestCategory(ocrText, vendorName)

            // Then
            assertEquals(groceriesCategory, result.category, "Failed for: $ocrText")
            assertTrue(result.confidence >= 0.7, "Low confidence for: $ocrText")
        }
    }

    @Test
    fun `suggestCategory should identify fuel stations correctly`() = runTest {
        // Given
        coEvery { categoryLearningService.getSuggestedCategory(any(), any()) } returns null
        coEvery { categoryRepository.getCategoryByName("Fuel") } returns fuelCategory

        val testCases = listOf(
            "SHELL GAS STATION" to "shell",
            "EXXON MOBIL" to "exxon",
            "CHEVRON #12345" to "chevron",
            "Gas purchase BP" to "bp"
        )

        testCases.forEach { (ocrText, vendorName) ->
            // When
            val result = autoCategorizationService.suggestCategory(ocrText, vendorName)

            // Then
            assertEquals(fuelCategory, result.category, "Failed for: $ocrText")
            assertTrue(result.confidence >= 0.7, "Low confidence for: $ocrText")
        }
    }

    @Test
    fun `suggestCategory should identify restaurants correctly`() = runTest {
        // Given
        coEvery { categoryLearningService.getSuggestedCategory(any(), any()) } returns null
        coEvery { categoryRepository.getCategoryByName("Restaurants") } returns restaurantsCategory

        val testCases = listOf(
            "MCDONALDS #1234" to "mcdonald",
            "STARBUCKS COFFEE" to "starbucks",
            "PIZZA HUT DELIVERY" to "pizza hut",
            "dining at restaurant" to "dining"
        )

        testCases.forEach { (ocrText, vendorName) ->
            // When
            val result = autoCategorizationService.suggestCategory(ocrText, vendorName)

            // Then
            assertEquals(restaurantsCategory, result.category, "Failed for: $ocrText")
            assertTrue(result.confidence >= 0.7, "Low confidence for: $ocrText")
        }
    }

    @Test
    fun `suggestCategory should identify healthcare correctly`() = runTest {
        // Given
        coEvery { categoryLearningService.getSuggestedCategory(any(), any()) } returns null
        coEvery { categoryRepository.getCategoryByName("Healthcare") } returns healthcareCategory

        val testCases = listOf(
            "CVS PHARMACY" to "cvs",
            "WALGREENS #2345" to "walgreens",
            "MEDICAL CENTER VISIT" to "medical",
            "prescription medication" to "prescription"
        )

        testCases.forEach { (ocrText, vendorName) ->
            // When
            val result = autoCategorizationService.suggestCategory(ocrText, vendorName)

            // Then
            assertEquals(healthcareCategory, result.category, "Failed for: $ocrText")
            assertTrue(result.confidence >= 0.7, "Low confidence for: $ocrText")
        }
    }

    @Test
    fun `suggestCategory should return default category for unknown text`() = runTest {
        // Given
        coEvery { categoryLearningService.getSuggestedCategory(any(), any()) } returns null
        coEvery { categoryRepository.getCategoryById(1L) } returns groceriesCategory

        // When
        val result = autoCategorizationService.suggestCategory(
            ocrText = "some random receipt text",
            vendorName = "unknown vendor"
        )

        // Then
        assertEquals(groceriesCategory, result.category)
        assertEquals(0.3, result.confidence)
        assertEquals("Default suggestion", result.reason)
    }

    @Test
    fun `suggestCategory should handle mixed content correctly`() = runTest {
        // Given - text that contains multiple category keywords but gas station should dominate
        coEvery { categoryLearningService.getSuggestedCategory(any(), any()) } returns null
        coEvery { categoryRepository.getCategoryByName("Fuel") } returns fuelCategory

        // When
        val result = autoCategorizationService.suggestCategory(
            ocrText = "SHELL STATION food mart snacks gas fuel",
            vendorName = "shell"
        )

        // Then
        assertEquals(fuelCategory, result.category)
        assertTrue(result.confidence >= 0.7)
    }

    @Test
    fun `learnFromUserCorrection should call categoryLearningService`() = runTest {
        // Given
        val ocrText = "walmart receipt"
        val vendorName = "walmart"
        val originalSuggestion = restaurantsCategory
        val userSelectedCategory = groceriesCategory
        
        coEvery { 
            categoryLearningService.recordUserCorrection(any(), any(), any(), any()) 
        } returns Unit

        // When
        autoCategorizationService.learnFromUserCorrection(
            ocrText, vendorName, originalSuggestion, userSelectedCategory
        )

        // Then
        coVerify { 
            categoryLearningService.recordUserCorrection(
                ocrText, vendorName, originalSuggestion, userSelectedCategory
            ) 
        }
    }

    @Test
    fun `suggestCategory should prioritize brand names over generic keywords`() = runTest {
        // Given
        coEvery { categoryLearningService.getSuggestedCategory(any(), any()) } returns null
        coEvery { categoryRepository.getCategoryByName("Fuel") } returns fuelCategory

        // When - brand name should get higher weight than generic terms
        val result = autoCategorizationService.suggestCategory(
            ocrText = "CHEVRON FOOD MART convenience store snacks",
            vendorName = "chevron"
        )

        // Then
        assertEquals(fuelCategory, result.category)
        assertTrue(result.confidence >= 0.7)
    }

    @Test
    fun `suggestCategory should handle exact word matches with higher confidence`() = runTest {
        // Given
        coEvery { categoryLearningService.getSuggestedCategory(any(), any()) } returns null
        coEvery { categoryRepository.getCategoryByName("Groceries") } returns groceriesCategory

        // When
        val exactMatchResult = autoCategorizationService.suggestCategory(
            ocrText = "Thank you for shopping at grocery store",
            vendorName = null
        )
        
        val partialMatchResult = autoCategorizationService.suggestCategory(
            ocrText = "Thank you for shopping at grocerystore", // no space
            vendorName = null
        )

        // Then
        assertEquals(groceriesCategory, exactMatchResult.category)
        assertEquals(groceriesCategory, partialMatchResult.category)
        // Exact word match should have higher or equal confidence
        assertTrue(exactMatchResult.confidence >= partialMatchResult.confidence)
    }
}