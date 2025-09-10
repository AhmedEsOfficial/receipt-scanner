package com.pharadev.receiptscanner.di

import com.pharadev.receiptscanner.data.repository.CategoryRepositoryImpl
import com.pharadev.receiptscanner.data.repository.ReceiptRepositoryImpl
import com.pharadev.receiptscanner.domain.repository.CategoryRepository
import com.pharadev.receiptscanner.domain.repository.ReceiptRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    @Binds
    abstract fun bindReceiptRepository(
        receiptRepositoryImpl: ReceiptRepositoryImpl
    ): ReceiptRepository
    
    @Binds
    abstract fun bindCategoryRepository(
        categoryRepositoryImpl: CategoryRepositoryImpl
    ): CategoryRepository
}