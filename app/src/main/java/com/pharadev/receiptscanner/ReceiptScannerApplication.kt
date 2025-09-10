package com.pharadev.receiptscanner

import android.app.Application
import com.pharadev.receiptscanner.domain.usecase.InitializeCategoriesUseCase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ReceiptScannerApplication : Application() {

    @Inject
    lateinit var initializeCategoriesUseCase: InitializeCategoriesUseCase

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        
        // Initialize default categories on app startup
        applicationScope.launch {
            try {
                initializeCategoriesUseCase()
            } catch (e: Exception) {
                // Log error but don't crash the app
                android.util.Log.e("ReceiptScannerApp", "Failed to initialize categories", e)
            }
        }
    }
}