package com.pharadev.receiptscanner.di

import android.content.Context
import com.pharadev.receiptscanner.data.camera.CameraService
import com.pharadev.receiptscanner.data.image.DuplicateDetector
import com.pharadev.receiptscanner.data.image.ImageProcessor
import com.pharadev.receiptscanner.domain.repository.ReceiptRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CameraModule {
    
    @Provides
    @Singleton
    fun provideCameraService(
        @ApplicationContext context: Context
    ): CameraService {
        return CameraService(context)
    }
    
    @Provides
    @Singleton
    fun provideImageProcessor(
        @ApplicationContext context: Context
    ): ImageProcessor {
        return ImageProcessor(context)
    }
    
    @Provides
    @Singleton
    fun provideDuplicateDetector(
        @ApplicationContext context: Context,
        receiptRepository: ReceiptRepository
    ): DuplicateDetector {
        return DuplicateDetector(context, receiptRepository)
    }
}