package com.pharadev.receiptscanner.di

import com.pharadev.receiptscanner.security.KeystoreManager
import com.pharadev.receiptscanner.security.NetworkSecurityHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {
    
    @Provides
    @Singleton
    fun provideKeystoreManager(): KeystoreManager {
        return KeystoreManager()
    }
    
    @Provides
    @Singleton
    fun provideNetworkSecurityHelper(): NetworkSecurityHelper {
        return NetworkSecurityHelper()
    }
}