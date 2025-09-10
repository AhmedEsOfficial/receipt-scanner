package com.pharadev.receiptscanner.presentation.ui.receipts

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pharadev.receiptscanner.R
import com.pharadev.receiptscanner.domain.model.CategoryDomain
import com.pharadev.receiptscanner.domain.model.ReceiptDomain
import com.pharadev.receiptscanner.presentation.viewmodel.ReceiptListViewModel
import com.pharadev.receiptscanner.ui.theme.ReceiptScannerTheme
import dagger.hilt.android.AndroidEntryPoint
import java.text.DecimalFormat
import java.time.format.DateTimeFormatter

@AndroidEntryPoint
class ReceiptListActivity : ComponentActivity() {

    private val viewModel: ReceiptListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ReceiptScannerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ReceiptListScreen(
                        viewModel = viewModel,
                        onReceiptClick = { receipt ->
                            openReceiptDetail(receipt.id)
                        },
                        onBackClick = {
                            finish()
                        }
                    )
                }
            }
        }
    }

    private fun openReceiptDetail(receiptId: Long) {
        val intent = Intent(this, ReceiptDetailActivity::class.java).apply {
            putExtra("receiptId", receiptId)
        }
        startActivity(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptListScreen(
    viewModel: ReceiptListViewModel,
    onReceiptClick: (ReceiptDomain) -> Unit,
    onBackClick: () -> Unit
) {
    val receipts by viewModel.receipts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top App Bar
        TopAppBar(
            title = { Text("All Receipts") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )

        // Search Bar
        SearchBar(
            query = searchQuery,
            onQueryChange = viewModel::searchReceipts,
            onClearClick = {
                viewModel.clearFilters()
                keyboardController?.hide()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )

        // Category Filter Chips
        if (categories.isNotEmpty()) {
            CategoryFilterChips(
                categories = categories,
                selectedCategory = selectedCategory,
                onCategorySelected = viewModel::filterByCategory,
                onClearFilter = { viewModel.filterByCategory(null) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // Receipt List
        Box(modifier = Modifier.fillMaxSize()) {
            if (receipts.isEmpty()) {
                EmptyState(
                    searchQuery = searchQuery,
                    selectedCategory = selectedCategory,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Pull-to-refresh indicator
                    if (isRefreshing) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    
                    items(
                        items = receipts.take(50), // Limit to 50 items as per requirements
                        key = { it.id }
                    ) { receipt ->
                        ReceiptListItem(
                            receipt = receipt,
                            onClick = { onReceiptClick(receipt) },
                            onDeleteClick = { viewModel.deleteReceipt(receipt) }
                        )
                    }
                    
                    // Show pagination info if there are more than 50 items
                    if (receipts.size > 50) {
                        item {
                            Text(
                                text = "Showing first 50 receipts of ${receipts.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            )
                        }
                    }
                }
            }
            
            // Floating refresh button
            if (!isRefreshing) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::refreshReceipts,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    },
                    text = { Text("Refresh") },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search receipts...") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search"
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClearClick) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear search"
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                // Keyboard controller will be handled in parent
            }
        ),
        singleLine = true,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryFilterChips(
    categories: List<CategoryDomain>,
    selectedCategory: CategoryDomain?,
    onCategorySelected: (CategoryDomain) -> Unit,
    onClearFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
        modifier = modifier
    ) {
        // Clear filter chip
        item {
            FilterChip(
                selected = selectedCategory == null,
                onClick = onClearFilter,
                label = { Text("All") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.GridView,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
        
        // Category chips
        items(categories) { category ->
            FilterChip(
                selected = selectedCategory?.id == category.id,
                onClick = { onCategorySelected(category) },
                label = { Text(category.name) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = getCategoryIconResource(category.icon)),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptListItem(
    receipt: ReceiptDomain,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currencyFormat = DecimalFormat("$#,##0.00")
    val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
    
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Receipt Image
            Card(
                modifier = Modifier.size(60.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                AsyncImage(
                    model = receipt.imagePath,
                    contentDescription = "Receipt image",
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.drawable.ic_receipt_placeholder),
                    error = painterResource(R.drawable.ic_receipt_placeholder),
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Receipt Information
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = receipt.vendor.takeIf { it.isNotBlank() } ?: "Unknown Vendor",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = receipt.category?.name ?: "Uncategorized",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = receipt.date.format(dateFormatter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Notes preview if available
                receipt.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Amount and Actions
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = currencyFormat.format(receipt.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                // Delete button
                IconButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete receipt",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
    
    // Delete confirmation dialog
    if (showDeleteDialog) {
        DeleteConfirmationDialog(
            receiptVendor = receipt.vendor.takeIf { it.isNotBlank() } ?: "Unknown Vendor",
            onConfirm = {
                onDeleteClick()
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

@Composable
fun EmptyState(
    searchQuery: String,
    selectedCategory: CategoryDomain?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = when {
                searchQuery.isNotEmpty() -> Icons.Default.SearchOff
                selectedCategory != null -> Icons.Default.FilterList
                else -> Icons.Default.Receipt
            },
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = when {
                searchQuery.isNotEmpty() -> "No receipts found"
                selectedCategory != null -> "No receipts in ${selectedCategory.name}"
                else -> "No receipts yet"
            },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        
        Text(
            text = when {
                searchQuery.isNotEmpty() -> "Try adjusting your search terms"
                selectedCategory != null -> "Try selecting a different category"
                else -> "Start by scanning your first receipt!"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun DeleteConfirmationDialog(
    receiptVendor: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Receipt") },
        text = {
            Text("Are you sure you want to delete the receipt from $receiptVendor? This action cannot be undone.")
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Helper function to get category icon resource
private fun getCategoryIconResource(iconName: String): Int {
    return when (iconName.lowercase()) {
        "groceries", "food" -> R.drawable.ic_groceries
        "fuel", "gas" -> R.drawable.ic_fuel
        "restaurants", "dining" -> R.drawable.ic_restaurants
        "transportation", "transport" -> R.drawable.ic_transportation
        "healthcare", "medical" -> R.drawable.ic_healthcare
        "shopping" -> R.drawable.ic_shopping
        "entertainment" -> R.drawable.ic_entertainment
        "utilities" -> R.drawable.ic_utilities
        else -> R.drawable.ic_category_default
    }
}