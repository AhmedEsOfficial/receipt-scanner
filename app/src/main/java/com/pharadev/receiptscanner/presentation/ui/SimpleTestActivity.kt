package com.pharadev.receiptscanner.presentation.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.pharadev.receiptscanner.presentation.ui.camera.CameraActivity
import com.pharadev.receiptscanner.presentation.ui.gallery.GalleryActivity
import com.pharadev.receiptscanner.presentation.viewmodel.DashboardViewModel
import com.pharadev.receiptscanner.ui.theme.ReceiptScannerTheme
import com.pharadev.receiptscanner.domain.repository.ReceiptRepository
import com.pharadev.receiptscanner.domain.repository.CategoryRepository
import com.pharadev.receiptscanner.domain.model.ReceiptDomain
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

@AndroidEntryPoint
class SimpleTestActivity : ComponentActivity() {
    
    @Inject
    lateinit var receiptRepository: ReceiptRepository
    
    @Inject
    lateinit var categoryRepository: CategoryRepository
    
    private val dashboardViewModel: DashboardViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            ReceiptScannerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SimpleTestScreen(
                        viewModel = dashboardViewModel,
                        onTestDatabaseClick = { testDatabase() },
                        onTestCameraClick = { testCameraFeature() },
                        onTestGalleryClick = { testGalleryFeature() },
                        onAddSampleDataClick = { addSampleData() },
                        onMainAppClick = { startMainActivity() }
                    )
                }
            }
        }
    }
    
    private fun testDatabase() {
        lifecycleScope.launch {
            try {
                val categories = categoryRepository.getAllCategories().first()
                val receipts = receiptRepository.getAllReceipts().first()
                
                val message = "Database Test:\nCategories: ${categories.size}\nReceipts: ${receipts.size}"
                showToast(message)
            } catch (e: Exception) {
                showToast("Database Error: ${e.message}")
            }
        }
    }
    
    private fun testCameraFeature() {
        try {
            val intent = Intent(this, CameraActivity::class.java)
            startActivity(intent)
            showToast("Camera Feature Launched")
        } catch (e: Exception) {
            showToast("Camera Error: ${e.message}")
        }
    }
    
    private fun testGalleryFeature() {
        try {
            val intent = Intent(this, GalleryActivity::class.java)
            startActivity(intent)
            showToast("Gallery Feature Launched")
        } catch (e: Exception) {
            showToast("Gallery Error: ${e.message}")
        }
    }
    
    private fun addSampleData() {
        lifecycleScope.launch {
            try {
                // Get first category for sample data
                val categories = categoryRepository.getAllCategories().first()
                if (categories.isEmpty()) {
                    showToast("No categories found. Database might not be initialized.")
                    return@launch
                }
                
                val sampleReceipt = ReceiptDomain(
                    id = 0,
                    imagePath = "/sample/path1.jpg",
                    vendor = "Sample Restaurant",
                    amount = BigDecimal("25.99"),
                    date = LocalDate.now().minusDays(1),
                    category = categories[0],
                    notes = "Test receipt",
                    createdAt = LocalDateTime.now().minusDays(1),
                    modifiedAt = LocalDateTime.now().minusDays(1)
                )
                
                receiptRepository.insertReceipt(sampleReceipt)
                showToast("Sample data added successfully!")
                
            } catch (e: Exception) {
                showToast("Error adding sample data: ${e.message}")
            }
        }
    }
    
    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
    }
    
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleTestScreen(
    viewModel: DashboardViewModel,
    onTestDatabaseClick: () -> Unit,
    onTestCameraClick: () -> Unit,
    onTestGalleryClick: () -> Unit,
    onAddSampleDataClick: () -> Unit,
    onMainAppClick: () -> Unit
) {
    val dashboardStats by viewModel.statistics.collectAsStateWithLifecycle()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "ReceiptScanner Test",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Test MVP Features",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        
        // Current Statistics
        dashboardStats?.let { stats ->
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Database Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Receipts: ${stats.receiptCount}")
                    Text("Total: $${stats.totalAmount}")
                    stats.topCategory?.let { category ->
                        Text("Top Category: ${category.name}")
                    }
                }
            }
        }
        
        // Test Buttons
        Button(
            onClick = onTestDatabaseClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Default.Storage, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Test Database")
        }
        
        Button(
            onClick = onTestCameraClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Test Camera")
        }
        
        Button(
            onClick = onTestGalleryClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Test Gallery")
        }
        
        OutlinedButton(
            onClick = onAddSampleDataClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Sample Data")
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Main App Button
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Ready for Production Testing!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onMainAppClick) {
                    Icon(imageVector = Icons.Default.Home, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Go to Main App")
                }
            }
        }
    }
}