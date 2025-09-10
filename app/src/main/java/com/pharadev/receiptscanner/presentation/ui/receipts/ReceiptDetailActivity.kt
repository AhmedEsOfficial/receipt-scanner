package com.pharadev.receiptscanner.presentation.ui.receipts

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pharadev.receiptscanner.R
import com.pharadev.receiptscanner.domain.model.CategoryDomain
import com.pharadev.receiptscanner.domain.model.ReceiptDomain
import com.pharadev.receiptscanner.presentation.viewmodel.ReceiptDetailViewModel
import com.pharadev.receiptscanner.ui.theme.ReceiptScannerTheme
import dagger.hilt.android.AndroidEntryPoint
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@AndroidEntryPoint
class ReceiptDetailActivity : ComponentActivity() {

    private val viewModel: ReceiptDetailViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ReceiptScannerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ReceiptDetailScreen(
                        viewModel = viewModel,
                        onBackClick = { finish() },
                        onShareClick = { receipt -> shareReceipt(receipt) },
                        onDeleteSuccess = { finish() }
                    )
                }
            }
        }
    }

    private fun shareReceipt(receipt: ReceiptDomain) {
        val shareText = buildString {
            appendLine("Receipt Details:")
            appendLine("Vendor: ${receipt.vendor}")
            appendLine("Amount: ${DecimalFormat("$#,##0.00").format(receipt.amount)}")
            appendLine("Date: ${receipt.date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))}")
            receipt.category?.let { appendLine("Category: ${it.name}") }
            receipt.notes?.takeIf { it.isNotBlank() }?.let { appendLine("Notes: $it") }
        }

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "Receipt from ${receipt.vendor}")
        }

        startActivity(Intent.createChooser(shareIntent, "Share Receipt"))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptDetailScreen(
    viewModel: ReceiptDetailViewModel,
    onBackClick: () -> Unit,
    onShareClick: (ReceiptDomain) -> Unit,
    onDeleteSuccess: () -> Unit
) {
    val receipt by viewModel.receipt.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val isEditing by viewModel.isEditing.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    
    var showImageDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        // Listen for delete success
        // This would typically be handled through a result state in the ViewModel
    }

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    receipt?.let { receiptData ->
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top App Bar
            TopAppBar(
                title = { Text("Receipt Details") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Share button
                    IconButton(onClick = { onShareClick(receiptData) }) {
                        Icon(Icons.Default.Share, contentDescription = "Share receipt")
                    }
                    
                    // Edit/Save button
                    IconButton(
                        onClick = {
                            if (isEditing) {
                                viewModel.saveChanges()
                            } else {
                                viewModel.startEditing()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isEditing) Icons.Default.Save else Icons.Default.Edit,
                            contentDescription = if (isEditing) "Save changes" else "Edit receipt"
                        )
                    }
                    
                    // Delete button
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete receipt",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )

            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Receipt Image
                ReceiptImageCard(
                    imagePath = receiptData.imagePath,
                    onClick = { showImageDialog = true }
                )

                // Receipt Details Form
                if (isEditing) {
                    EditableReceiptDetails(
                        viewModel = viewModel,
                        categories = categories
                    )
                } else {
                    ReadOnlyReceiptDetails(receipt = receiptData)
                }
            }
        }

        // Full-screen image dialog
        if (showImageDialog) {
            FullScreenImageDialog(
                imagePath = receiptData.imagePath,
                onDismiss = { showImageDialog = false }
            )
        }

        // Delete confirmation dialog
        if (showDeleteDialog) {
            DeleteConfirmationDialog(
                receiptVendor = receiptData.vendor.takeIf { it.isNotBlank() } ?: "Unknown Vendor",
                onConfirm = {
                    viewModel.deleteReceipt()
                    showDeleteDialog = false
                    onDeleteSuccess()
                },
                onDismiss = { showDeleteDialog = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptImageCard(
    imagePath: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Box {
            AsyncImage(
                model = imagePath,
                contentDescription = "Receipt image",
                contentScale = ContentScale.Fit,
                placeholder = painterResource(R.drawable.ic_receipt_placeholder),
                error = painterResource(R.drawable.ic_receipt_placeholder),
                modifier = Modifier.fillMaxSize()
            )
            
            // Zoom hint overlay
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Tap to zoom",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun ReadOnlyReceiptDetails(
    receipt: ReceiptDomain,
    modifier: Modifier = Modifier
) {
    val currencyFormat = DecimalFormat("$#,##0.00")
    val dateFormatter = DateTimeFormatter.ofPattern("MMMM dd, yyyy")

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Receipt Information",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            // Vendor
            DetailRow(
                label = "Vendor",
                value = receipt.vendor.takeIf { it.isNotBlank() } ?: "Unknown Vendor",
                icon = Icons.Default.Store
            )

            // Amount
            DetailRow(
                label = "Amount",
                value = currencyFormat.format(receipt.amount),
                icon = Icons.Default.AttachMoney,
                valueColor = MaterialTheme.colorScheme.primary
            )

            // Date
            DetailRow(
                label = "Date",
                value = receipt.date.format(dateFormatter),
                icon = Icons.Default.CalendarToday
            )

            // Category
            receipt.category?.let { category ->
                DetailRow(
                    label = "Category",
                    value = category.name,
                    icon = Icons.Default.Category
                )
            }

            // Notes
            receipt.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                DetailRow(
                    label = "Notes",
                    value = notes,
                    icon = Icons.Default.Note,
                    isMultiline = true
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditableReceiptDetails(
    viewModel: ReceiptDetailViewModel,
    categories: List<CategoryDomain>,
    modifier: Modifier = Modifier
) {
    val editableVendor by viewModel.editableVendor.collectAsStateWithLifecycle()
    val editableAmount by viewModel.editableAmount.collectAsStateWithLifecycle()
    val editableDate by viewModel.editableDate.collectAsStateWithLifecycle()
    val editableNotes by viewModel.editableNotes.collectAsStateWithLifecycle()
    val editableCategory by viewModel.editableCategory.collectAsStateWithLifecycle()
    
    var showDatePicker by remember { mutableStateOf(false) }
    var showCategoryDropdown by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Edit Receipt",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                
                TextButton(onClick = viewModel::cancelEditing) {
                    Text("Cancel")
                }
            }

            // Vendor
            OutlinedTextField(
                value = editableVendor,
                onValueChange = viewModel::updateVendor,
                label = { Text("Vendor") },
                leadingIcon = {
                    Icon(Icons.Default.Store, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Amount
            OutlinedTextField(
                value = editableAmount,
                onValueChange = viewModel::updateAmount,
                label = { Text("Amount") },
                leadingIcon = {
                    Icon(Icons.Default.AttachMoney, contentDescription = null)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            // Date
            OutlinedTextField(
                value = editableDate?.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")) ?: "",
                onValueChange = { },
                label = { Text("Date") },
                leadingIcon = {
                    Icon(Icons.Default.CalendarToday, contentDescription = null)
                },
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Change date")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Category Dropdown
            ExposedDropdownMenuBox(
                expanded = showCategoryDropdown,
                onExpandedChange = { showCategoryDropdown = !showCategoryDropdown }
            ) {
                OutlinedTextField(
                    value = editableCategory?.name ?: "Select Category",
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Category") },
                    leadingIcon = {
                        Icon(Icons.Default.Category, contentDescription = null)
                    },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCategoryDropdown)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                
                ExposedDropdownMenu(
                    expanded = showCategoryDropdown,
                    onDismissRequest = { showCategoryDropdown = false }
                ) {
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            onClick = {
                                viewModel.updateCategory(category)
                                showCategoryDropdown = false
                            }
                        )
                    }
                }
            }

            // Notes
            OutlinedTextField(
                value = editableNotes,
                onValueChange = viewModel::updateNotes,
                label = { Text("Notes") },
                leadingIcon = {
                    Icon(Icons.Default.Note, contentDescription = null)
                },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Date Picker Dialog
    if (showDatePicker) {
        DatePickerDialog(
            initialDate = editableDate ?: LocalDate.now(),
            onDateSelected = { date ->
                viewModel.updateDate(date)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

@Composable
fun DetailRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    isMultiline: Boolean = false
) {
    Row(
        verticalAlignment = if (isMultiline) Alignment.Top else Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = valueColor,
                fontWeight = if (label == "Amount") FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun FullScreenImageDialog(
    imagePath: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                AsyncImage(
                    model = imagePath,
                    contentDescription = "Full size receipt image",
                    contentScale = ContentScale.Fit,
                    placeholder = painterResource(R.drawable.ic_receipt_placeholder),
                    error = painterResource(R.drawable.ic_receipt_placeholder),
                    modifier = Modifier.fillMaxSize()
                )
                
                // Close button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
                
                // Zoom hint
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Pinch to zoom • Tap outside to close",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialog(
    initialDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.toEpochDay() * 24 * 60 * 60 * 1000
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = LocalDate.ofEpochDay(millis / (24 * 60 * 60 * 1000))
                        onDateSelected(date)
                    }
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}