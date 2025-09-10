package com.pharadev.receiptscanner.domain.service

import com.pharadev.receiptscanner.domain.model.CategoryDomain
import com.pharadev.receiptscanner.domain.repository.CategoryRepository
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CategoryServiceTest {

    @MockK
    private lateinit var categoryRepository: CategoryRepository

    private lateinit var categoryService: CategoryService

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        categoryService = CategoryService(categoryRepository)
    }

    @Test
    fun `initializeDefaultCategories should insert default categories when none exist`() = runTest {
        // Given
        coEvery { categoryRepository.getAllCategories() } returns flowOf(emptyList())
        coEvery { categoryRepository.insertCategory(any()) } returns 1L

        // When
        categoryService.initializeDefaultCategories()

        // Then
        coVerify(exactly = 8) { categoryRepository.insertCategory(any()) }
    }

    @Test
    fun `initializeDefaultCategories should not insert when categories already exist`() = runTest {
        // Given
        val existingCategory = CategoryDomain(1L, "Groceries", "#4CAF50", "grocery_cart", true)
        coEvery { categoryRepository.getAllCategories() } returns flowOf(listOf(existingCategory))

        // When
        categoryService.initializeDefaultCategories()

        // Then
        coVerify(exactly = 0) { categoryRepository.insertCategory(any()) }
    }

    @Test
    fun `getAllCategories should return flow from repository`() = runTest {
        // Given
        val categories = listOf(
            CategoryDomain(1L, "Groceries", "#4CAF50", "grocery_cart", true),
            CategoryDomain(2L, "Fuel", "#FF9800", "local_gas_station", true)
        )
        coEvery { categoryRepository.getAllCategories() } returns flowOf(categories)

        // When
        val result = categoryService.getAllCategories()

        // Then
        result.collect { categoryList ->
            assertEquals(2, categoryList.size)
            assertEquals("Groceries", categoryList[0].name)
            assertEquals("Fuel", categoryList[1].name)
        }
    }

    @Test
    fun `getCategoryById should return category from repository`() = runTest {
        // Given
        val category = CategoryDomain(1L, "Groceries", "#4CAF50", "grocery_cart", true)
        coEvery { categoryRepository.getCategoryById(1L) } returns category

        // When
        val result = categoryService.getCategoryById(1L)

        // Then
        assertNotNull(result)
        assertEquals("Groceries", result.name)
        assertEquals("#4CAF50", result.color)
    }

    @Test
    fun `getCategoryByName should return category from repository`() = runTest {
        // Given
        val category = CategoryDomain(1L, "Groceries", "#4CAF50", "grocery_cart", true)
        coEvery { categoryRepository.getCategoryByName("Groceries") } returns category

        // When
        val result = categoryService.getCategoryByName("Groceries")

        // Then
        assertNotNull(result)
        assertEquals("Groceries", result.name)
        assertEquals(1L, result.id)
    }

    @Test
    fun `addCustomCategory should create new category with correct id`() = runTest {
        // Given
        val existingCategories = listOf(
            CategoryDomain(1L, "Groceries", "#4CAF50", "grocery_cart", true),
            CategoryDomain(2L, "Fuel", "#FF9800", "local_gas_station", true)
        )
        coEvery { categoryRepository.getAllCategories() } returns flowOf(existingCategories)
        coEvery { categoryRepository.insertCategory(any()) } returns 3L

        // When
        val result = categoryService.addCustomCategory("Custom", "#000000", "custom_icon")

        // Then
        assertEquals(3L, result)
        coVerify {
            categoryRepository.insertCategory(match {
                it.id == 3L && it.name == "Custom" && !it.isDefault
            })
        }
    }

    @Test
    fun `updateCategory should call repository update`() = runTest {
        // Given
        val category = CategoryDomain(1L, "Updated", "#000000", "updated_icon", false)
        coEvery { categoryRepository.updateCategory(any()) } returns Unit

        // When
        categoryService.updateCategory(category)

        // Then
        coVerify { categoryRepository.updateCategory(category) }
    }

    @Test
    fun `deleteCustomCategory should only delete non-default categories`() = runTest {
        // Given
        val customCategory = CategoryDomain(10L, "Custom", "#000000", "custom", false)
        val defaultCategory = CategoryDomain(1L, "Groceries", "#4CAF50", "grocery_cart", true)
        coEvery { categoryRepository.deleteCategory(any()) } returns Unit

        // When - delete custom category
        categoryService.deleteCustomCategory(customCategory)

        // Then
        coVerify { categoryRepository.deleteCategory(customCategory) }

        // When - try to delete default category
        categoryService.deleteCustomCategory(defaultCategory)

        // Then - should not call delete for default category
        coVerify(exactly = 1) { categoryRepository.deleteCategory(any()) }
    }

    @Test
    fun `getDefaultCategories should return 8 predefined categories`() {
        // When
        val defaultCategories = categoryService.getDefaultCategories()

        // Then
        assertEquals(8, defaultCategories.size)
        
        val categoryNames = defaultCategories.map { it.name }
        assertTrue(categoryNames.contains("Groceries"))
        assertTrue(categoryNames.contains("Fuel"))
        assertTrue(categoryNames.contains("Restaurants"))
        assertTrue(categoryNames.contains("Transportation"))
        assertTrue(categoryNames.contains("Healthcare"))
        assertTrue(categoryNames.contains("Entertainment"))
        assertTrue(categoryNames.contains("Shopping"))
        assertTrue(categoryNames.contains("Utilities"))

        // All should be default categories
        assertTrue(defaultCategories.all { it.isDefault })
    }
}