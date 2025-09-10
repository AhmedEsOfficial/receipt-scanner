package com.pharadev.receiptscanner.domain.service

import android.content.SharedPreferences
import com.pharadev.receiptscanner.domain.model.CategoryDomain
import com.pharadev.receiptscanner.domain.repository.CategoryRepository
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CategoryLearningServiceTest {

    @MockK
    private lateinit var sharedPreferences: SharedPreferences

    @MockK
    private lateinit var editor: SharedPreferences.Editor

    @MockK
    private lateinit var categoryRepository: CategoryRepository

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private lateinit var categoryLearningService: CategoryLearningService

    private val groceriesCategory = CategoryDomain(1L, "Groceries", "#4CAF50", "grocery_cart", true)
    private val fuelCategory = CategoryDomain(2L, "Fuel", "#FF9800", "local_gas_station", true)

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        
        every { sharedPreferences.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor
        every { editor.apply() } returns Unit

        categoryLearningService = CategoryLearningService(sharedPreferences, categoryRepository, json)
    }

    @Test
    fun `getSuggestedCategory should return null when no learning data exists`() = runTest {
        // Given
        every { sharedPreferences.getString("category_learning_data", null) } returns null

        // When
        val result = categoryLearningService.getSuggestedCategory("walmart", "receipt text")

        // Then
        assertNull(result)
    }

    @Test
    fun `getSuggestedCategory should return vendor-based suggestion with high confidence`() = runTest {
        // Given
        val learningData = CategoryLearningService.LearningData(
            vendorLearnings = mapOf(
                "walmart" to CategoryLearningService.VendorLearning(
                    vendorName = "walmart",
                    categoryId = 1L,
                    correctionCount = 3,
                    confidence = 0.9
                )
            )
        )
        val jsonString = json.encodeToString(CategoryLearningService.LearningData.serializer(), learningData)
        
        every { sharedPreferences.getString("category_learning_data", null) } returns jsonString
        coEvery { categoryRepository.getCategoryById(1L) } returns groceriesCategory

        // When
        val result = categoryLearningService.getSuggestedCategory("walmart", "some receipt text")

        // Then
        assertNotNull(result)
        assertEquals(groceriesCategory, result.category)
        assertEquals(0.9, result.confidence)
        assertTrue(result.reason.contains("vendor: walmart"))
        assertTrue(result.reason.contains("3 corrections"))
    }

    @Test
    fun `getSuggestedCategory should return keyword-based suggestion when vendor not found`() = runTest {
        // Given
        val learningData = CategoryLearningService.LearningData(
            vendorLearnings = emptyMap(),
            keywordLearnings = mapOf(
                "groceries" to CategoryLearningService.KeywordLearning(
                    keyword = "groceries",
                    categoryId = 1L,
                    correctionCount = 2,
                    confidence = 0.8
                )
            )
        )
        val jsonString = json.encodeToString(CategoryLearningService.LearningData.serializer(), learningData)
        
        every { sharedPreferences.getString("category_learning_data", null) } returns jsonString
        coEvery { categoryRepository.getCategoryById(1L) } returns groceriesCategory

        // When
        val result = categoryLearningService.getSuggestedCategory(null, "bought groceries today")

        // Then
        assertNotNull(result)
        assertEquals(groceriesCategory, result.category)
        assertEquals(0.8, result.confidence)
        assertTrue(result.reason.contains("keywords: groceries"))
    }

    @Test
    fun `getSuggestedCategory should prioritize vendor over keyword learning`() = runTest {
        // Given
        val learningData = CategoryLearningService.LearningData(
            vendorLearnings = mapOf(
                "shell" to CategoryLearningService.VendorLearning(
                    vendorName = "shell",
                    categoryId = 2L, // Fuel
                    correctionCount = 5,
                    confidence = 0.95
                )
            ),
            keywordLearnings = mapOf(
                "food" to CategoryLearningService.KeywordLearning(
                    keyword = "food",
                    categoryId = 1L, // Groceries
                    correctionCount = 3,
                    confidence = 0.8
                )
            )
        )
        val jsonString = json.encodeToString(CategoryLearningService.LearningData.serializer(), learningData)
        
        every { sharedPreferences.getString("category_learning_data", null) } returns jsonString
        coEvery { categoryRepository.getCategoryById(2L) } returns fuelCategory

        // When - text contains keyword but vendor should take priority
        val result = categoryLearningService.getSuggestedCategory("shell", "shell food mart snacks")

        // Then
        assertNotNull(result)
        assertEquals(fuelCategory, result.category) // Should be fuel, not groceries
        assertEquals(0.95, result.confidence)
        assertTrue(result.reason.contains("vendor: shell"))
    }

    @Test
    fun `recordUserCorrection should create new vendor learning`() = runTest {
        // Given
        every { sharedPreferences.getString("category_learning_data", null) } returns null

        // When
        categoryLearningService.recordUserCorrection(
            ocrText = "receipt from walmart",
            vendorName = "walmart",
            originalSuggestion = fuelCategory,
            userChoice = groceriesCategory
        )

        // Then
        verify {
            editor.putString("category_learning_data", any())
            editor.apply()
        }
    }

    @Test
    fun `recordUserCorrection should reinforce existing vendor learning`() = runTest {
        // Given
        val existingData = CategoryLearningService.LearningData(
            vendorLearnings = mapOf(
                "walmart" to CategoryLearningService.VendorLearning(
                    vendorName = "walmart",
                    categoryId = 1L,
                    correctionCount = 2,
                    confidence = 0.87
                )
            )
        )
        val jsonString = json.encodeToString(CategoryLearningService.LearningData.serializer(), existingData)
        every { sharedPreferences.getString("category_learning_data", null) } returns jsonString

        // When
        categoryLearningService.recordUserCorrection(
            ocrText = "another walmart receipt",
            vendorName = "walmart",
            originalSuggestion = null,
            userChoice = groceriesCategory
        )

        // Then - should save updated data with incremented count and confidence
        verify {
            editor.putString("category_learning_data", match { savedJson ->
                val savedData = json.decodeFromString<CategoryLearningService.LearningData>(savedJson)
                val vendorLearning = savedData.vendorLearnings["walmart"]
                vendorLearning != null && 
                vendorLearning.correctionCount == 3 &&
                vendorLearning.confidence > 0.87
            })
            editor.apply()
        }
    }

    @Test
    fun `recordUserCorrection should create keyword learning for significant words`() = runTest {
        // Given
        every { sharedPreferences.getString("category_learning_data", null) } returns null

        // When
        categoryLearningService.recordUserCorrection(
            ocrText = "bought groceries and food items",
            vendorName = null,
            originalSuggestion = null,
            userChoice = groceriesCategory
        )

        // Then - should save keyword learning for relevant words
        verify {
            editor.putString("category_learning_data", match { savedJson ->
                val savedData = json.decodeFromString<CategoryLearningService.LearningData>(savedJson)
                savedData.keywordLearnings.containsKey("groceries") &&
                savedData.keywordLearnings.containsKey("food") &&
                savedData.keywordLearnings.containsKey("items")
            })
            editor.apply()
        }
    }

    @Test
    fun `recordUserCorrection should not learn from short words or numbers`() = runTest {
        // Given
        every { sharedPreferences.getString("category_learning_data", null) } returns null

        // When
        categoryLearningService.recordUserCorrection(
            ocrText = "tx id 123 at 5pm on 12/25",
            vendorName = null,
            originalSuggestion = null,
            userChoice = groceriesCategory
        )

        // Then - should not save learning for short words or numbers
        verify {
            editor.putString("category_learning_data", match { savedJson ->
                val savedData = json.decodeFromString<CategoryLearningService.LearningData>(savedJson)
                // Should not contain short words like "tx", "id", "at", "on" or numbers
                !savedData.keywordLearnings.containsKey("tx") &&
                !savedData.keywordLearnings.containsKey("id") &&
                !savedData.keywordLearnings.containsKey("123") &&
                !savedData.keywordLearnings.containsKey("12")
            })
        }
    }

    @Test
    fun `clearLearningData should remove all learning data`() = runTest {
        // When
        categoryLearningService.clearLearningData()

        // Then
        verify {
            editor.remove("category_learning_data")
            editor.apply()
        }
    }

    @Test
    fun `getLearningStats should return correct statistics`() = runTest {
        // Given
        val learningData = CategoryLearningService.LearningData(
            vendorLearnings = mapOf(
                "walmart" to CategoryLearningService.VendorLearning("walmart", 1L, 3, 0.9),
                "shell" to CategoryLearningService.VendorLearning("shell", 2L, 2, 0.8)
            ),
            keywordLearnings = mapOf(
                "groceries" to CategoryLearningService.KeywordLearning("groceries", 1L, 4, 0.85),
                "gas" to CategoryLearningService.KeywordLearning("gas", 2L, 1, 0.7)
            )
        )
        val jsonString = json.encodeToString(CategoryLearningService.LearningData.serializer(), learningData)
        every { sharedPreferences.getString("category_learning_data", null) } returns jsonString

        // When
        val stats = categoryLearningService.getLearningStats()

        // Then
        assertEquals(2, stats.vendorLearningsCount)
        assertEquals(2, stats.keywordLearningsCount)
        assertEquals(10, stats.totalCorrections) // 3+2+4+1 = 10
    }

    @Test
    fun `getSuggestedCategory should handle corrupted JSON gracefully`() = runTest {
        // Given
        every { sharedPreferences.getString("category_learning_data", null) } returns "invalid json"

        // When
        val result = categoryLearningService.getSuggestedCategory("walmart", "receipt text")

        // Then
        assertNull(result) // Should return null instead of crashing
    }

    @Test
    fun `getSuggestedCategory should combine multiple keyword matches correctly`() = runTest {
        // Given
        val learningData = CategoryLearningService.LearningData(
            vendorLearnings = emptyMap(),
            keywordLearnings = mapOf(
                "groceries" to CategoryLearningService.KeywordLearning("groceries", 1L, 3, 0.8),
                "food" to CategoryLearningService.KeywordLearning("food", 1L, 2, 0.7),
                "organic" to CategoryLearningService.KeywordLearning("organic", 1L, 1, 0.75)
            )
        )
        val jsonString = json.encodeToString(CategoryLearningService.LearningData.serializer(), learningData)
        
        every { sharedPreferences.getString("category_learning_data", null) } returns jsonString
        coEvery { categoryRepository.getCategoryById(1L) } returns groceriesCategory

        // When
        val result = categoryLearningService.getSuggestedCategory(null, "bought organic food at groceries store")

        // Then
        assertNotNull(result)
        assertEquals(groceriesCategory, result.category)
        assertTrue(result.confidence >= 0.7) // Combined confidence should be good
        assertTrue(result.reason.contains("groceries, food, organic"))
    }
}