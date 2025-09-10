package com.pharadev.receiptscanner.di

import android.content.Context
import com.pharadev.receiptscanner.data.ocr.DataExtractionService
import com.pharadev.receiptscanner.data.ocr.ImagePreprocessingService
import com.pharadev.receiptscanner.data.ocr.OCRProcessingService
import com.pharadev.receiptscanner.data.ocr.TextRecognitionService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dependency injection module for OCR-related services and components
 */
@Module
@InstallIn(SingletonComponent::class)
object OCRModule {
    
    /**
     * Provides image preprocessing service for enhancing images before OCR
     */
    @Provides
    @Singleton
    fun provideImagePreprocessingService(): ImagePreprocessingService {
        return ImagePreprocessingService()
    }
    
    /**
     * Provides ML Kit text recognition service
     */
    @Provides
    @Singleton
    fun provideTextRecognitionService(
        @ApplicationContext context: Context,
        imagePreprocessingService: ImagePreprocessingService
    ): TextRecognitionService {
        return TextRecognitionService(context, imagePreprocessingService)
    }
    
    /**
     * Provides data extraction service for parsing OCR results
     */
    @Provides
    @Singleton
    fun provideDataExtractionService(): DataExtractionService {
        return DataExtractionService()
    }
    
    /**
     * Provides high-level OCR processing service that coordinates
     * text recognition and data extraction
     */
    @Provides
    @Singleton
    fun provideOCRProcessingService(
        @ApplicationContext context: Context,
        textRecognitionService: TextRecognitionService,
        dataExtractionService: DataExtractionService
    ): OCRProcessingService {
        return OCRProcessingService(
            context = context,
            textRecognitionService = textRecognitionService,
            dataExtractionService = dataExtractionService
        )
    }
}