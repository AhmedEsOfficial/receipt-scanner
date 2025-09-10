package com.pharadev.receiptscanner.di

import android.content.Context
import androidx.room.Room
import com.pharadev.receiptscanner.data.database.ReceiptDatabase
import com.pharadev.receiptscanner.data.database.dao.CategoryDao
import com.pharadev.receiptscanner.data.database.dao.ReceiptDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideReceiptDatabase(
        @ApplicationContext context: Context
    ): ReceiptDatabase {
        return ReceiptDatabase.buildDatabase(context)
    }
    
    @Provides
    fun provideReceiptDao(database: ReceiptDatabase): ReceiptDao {
        return database.receiptDao()
    }
    
    @Provides
    fun provideCategoryDao(database: ReceiptDatabase): CategoryDao {
        return database.categoryDao()
    }
}