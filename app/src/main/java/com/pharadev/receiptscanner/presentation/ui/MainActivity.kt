package com.pharadev.receiptscanner.presentation.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import com.pharadev.receiptscanner.presentation.ui.camera.CameraActivity
import com.pharadev.receiptscanner.presentation.ui.gallery.GalleryActivity
import com.pharadev.receiptscanner.presentation.ui.receipts.ReceiptListActivity
import com.pharadev.receiptscanner.presentation.ui.receipts.ReceiptDetailActivity
import com.pharadev.receiptscanner.presentation.viewmodel.DashboardViewModel
import com.pharadev.receiptscanner.domain.model.ReceiptDomain
import com.pharadev.receiptscanner.domain.model.DashboardStatistics
import com.pharadev.receiptscanner.ui.theme.ReceiptScannerTheme
import java.text.DecimalFormat
import java.time.format.DateTimeFormatter

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private val dashboardViewModel: DashboardViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            ReceiptScannerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        viewModel = dashboardViewModel,
                        onScanReceiptClick = { startCameraActivity() },
                        onGalleryImportClick = { startGalleryActivity() },
                        onViewAllReceiptsClick = { startReceiptListActivity() },
                        onReceiptClick = { receiptId -> startReceiptDetailActivity(receiptId) }
                    )
                }
            }
        }
    }
    
    private fun startCameraActivity() {
        val intent = Intent(this, CameraActivity::class.java)
        startActivity(intent)
    }
    
    private fun startGalleryActivity() {
        val intent = Intent(this, GalleryActivity::class.java)
        startActivity(intent)
    }
    
    private fun startReceiptListActivity() {
        val intent = Intent(this, ReceiptListActivity::class.java)
        startActivity(intent)
    }
    
    private fun startReceiptDetailActivity(receiptId: Long) {
        val intent = Intent(this, ReceiptDetailActivity::class.java).apply {
            putExtra("receiptId", receiptId)
        }
        startActivity(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: DashboardViewModel,
    onScanReceiptClick: () -> Unit,
    onGalleryImportClick: () -> Unit,
    onViewAllReceiptsClick: () -> Unit,
    onReceiptClick: (Long) -> Unit
) {
    val dashboardStats by viewModel.statistics.collectAsStateWithLifecycle()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // App Title
        Text(
            text = "ReceiptSnap",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        // Quick Actions Section
        QuickActionsSection(
            onScanReceiptClick = onScanReceiptClick,
            onGalleryImportClick = onGalleryImportClick
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Dashboard Statistics
        dashboardStats?.let { stats ->
            DashboardStatsSection(stats = stats)
            Spacer(modifier = Modifier.height(24.dp))
        }
        
        // Recent Receipts Section  
        dashboardStats?.let { stats ->
            RecentReceiptsSection(
                receipts = stats.recentReceipts,
                onViewAllClick = onViewAllReceiptsClick,
                onReceiptClick = onReceiptClick
            )
        }
    }
}

@Composable
fun QuickActionsSection(
    onScanReceiptClick: () -> Unit,
    onGalleryImportClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Scan Receipt Button
                Card(
                    onClick = onScanReceiptClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(120.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Camera,
                            contentDescription = "Scan Receipt",
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Scan Receipt",
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // Import from Gallery Button
                Card(
                    onClick = onGalleryImportClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(120.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Import from Gallery",
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Gallery Import",
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardStatsSection(stats: DashboardStatistics) {
    val currencyFormat = DecimalFormat("$#,##0.00")
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "This Month",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Total Amount
                StatCard(
                    title = "Total Spent",
                    value = currencyFormat.format(stats.totalAmount),
                    icon = Icons.Default.AttachMoney,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // Receipt Count
                StatCard(
                    title = "Receipts",
                    value = stats.receiptCount.toString(),
                    icon = Icons.Default.Receipt,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Top Category (if available)
            stats.topCategory?.let { category ->
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Top Category: ${category.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(100.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = color
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = color.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun RecentReceiptsSection(
    receipts: List<ReceiptDomain>,
    onViewAllClick: () -> Unit,
    onReceiptClick: (Long) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Receipts",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                
                if (receipts.isNotEmpty()) {
                    TextButton(
                        onClick = onViewAllClick
                    ) {
                        Text("View All")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (receipts.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Receipt,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No receipts yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "Start by scanning your first receipt!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(receipts) { receipt ->
                        ReceiptListItem(
                            receipt = receipt,
                            onClick = { onReceiptClick(receipt.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReceiptListItem(
    receipt: ReceiptDomain,
    onClick: () -> Unit
) {
    val currencyFormat = DecimalFormat("$#,##0.00")
    val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
    
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = receipt.vendor,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = receipt.date.format(dateFormatter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Text(
                text = currencyFormat.format(receipt.amount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}