package com.pharadev.receiptscanner.di

import android.content.Context
import android.content.SharedPreferences
import com.pharadev.receiptscanner.domain.service.AutoCategorizationService
import com.pharadev.receiptscanner.domain.service.CategoryLearningService
import com.pharadev.receiptscanner.domain.service.CategoryService
import com.pharadev.receiptscanner.domain.repository.CategoryRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CategoryModule {

    @Provides
    @Singleton
    fun provideCategoryService(
        categoryRepository: CategoryRepository
    ): CategoryService {
        return CategoryService(categoryRepository)
    }

    @Provides
    @Singleton
    fun provideCategoryLearningService(
        sharedPreferences: SharedPreferences,
        categoryRepository: CategoryRepository,
        json: Json
    ): CategoryLearningService {
        return CategoryLearningService(sharedPreferences, categoryRepository, json)
    }

    @Provides
    @Singleton
    fun provideAutoCategorizationService(
        categoryRepository: CategoryRepository,
        categoryLearningService: CategoryLearningService
    ): AutoCategorizationService {
        return AutoCategorizationService(categoryRepository, categoryLearningService)
    }

    @Provides
    @Singleton
    fun provideSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences {
        return context.getSharedPreferences("category_prefs", Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}