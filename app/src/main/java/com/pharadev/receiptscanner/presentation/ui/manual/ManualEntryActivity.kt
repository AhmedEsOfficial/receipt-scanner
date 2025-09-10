package com.pharadev.receiptscanner.presentation.ui.manual

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pharadev.receiptscanner.R
import com.pharadev.receiptscanner.data.ocr.ReceiptData
import com.pharadev.receiptscanner.domain.model.CategoryDomain
import com.pharadev.receiptscanner.presentation.ui.receipts.ReceiptDetailActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class ManualEntryActivity : AppCompatActivity() {
    
    private val viewModel: ManualEntryViewModel by viewModels()
    
    // UI Components
    private lateinit var imageView: ImageView
    private lateinit var vendorEditText: EditText
    private lateinit var amountEditText: EditText
    private lateinit var dateTextView: TextView
    private lateinit var categorySpinner: Spinner
    private lateinit var notesEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var errorTextView: TextView
    private lateinit var ocrSuggestionCard: LinearLayout
    private lateinit var useSuggestionButton: Button
    private lateinit var ignoreSuggestionButton: Button
    private lateinit var suggestionTextView: TextView
    
    private var selectedDate: Date = Date()
    private var imageUri: Uri? = null
    private var ocrData: ReceiptData? = null
    private var categories: List<CategoryDomain> = emptyList()
    
    companion object {
        private const val TAG = "ManualEntryActivity"
        const val EXTRA_IMAGE_URI = "extra_image_uri"
        private const val EXTRA_OCR_DATA = "extra_ocr_data"
        
        fun createIntent(context: Context, imageUri: Uri, ocrData: ReceiptData? = null): Intent {
            return Intent(context, ManualEntryActivity::class.java).apply {
                putExtra(EXTRA_IMAGE_URI, imageUri.toString())
                // For now, we'll pass OCR data as separate fields since ReceiptData is not Parcelable
                ocrData?.let { data ->
                    data.vendor?.let { putExtra("ocr_vendor", it.name) }
                    data.amount?.let { putExtra("ocr_amount", it.amount.toString()) }
                    data.date?.let { putExtra("ocr_date", it.date.time) }
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual_entry)
        
        initializeViews()
        setupListeners()
        processIntentData()
        observeViewModel()
        
        // Load categories
        lifecycleScope.launch {
            viewModel.loadCategories()
        }
    }
    
    private fun initializeViews() {
        imageView = findViewById(R.id.imageView)
        vendorEditText = findViewById(R.id.editTextVendor)
        amountEditText = findViewById(R.id.editTextAmount)
        dateTextView = findViewById(R.id.textViewDate)
        categorySpinner = findViewById(R.id.spinnerCategory)
        notesEditText = findViewById(R.id.editTextNotes)
        saveButton = findViewById(R.id.buttonSave)
        progressBar = findViewById(R.id.progressBar)
        errorTextView = findViewById(R.id.textViewError)
        ocrSuggestionCard = findViewById(R.id.ocrSuggestionCard)
        useSuggestionButton = findViewById(R.id.buttonUseSuggestion)
        ignoreSuggestionButton = findViewById(R.id.buttonIgnoreSuggestion)
        suggestionTextView = findViewById(R.id.textViewSuggestion)
        
        // Setup date display
        updateDateDisplay()
        
        // Initially hide error and progress
        hideError()
        hideProgress()
    }
    
    private fun setupListeners() {
        // Date picker
        dateTextView.setOnClickListener {
            showDatePicker()
        }
        
        // Amount formatting
        amountEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validateAmount()
            }
        })
        
        // Vendor validation
        vendorEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validateForm()
            }
        })
        
        // Save button
        saveButton.setOnClickListener {
            if (validateForm()) {
                saveReceipt()
            }
        }
        
        // OCR suggestion buttons
        useSuggestionButton.setOnClickListener {
            applySuggestions()
        }
        
        ignoreSuggestionButton.setOnClickListener {
            hideSuggestionCard()
        }
    }
    
    private fun processIntentData() {
        // Get image URI
        val imageUriString = intent.getStringExtra(EXTRA_IMAGE_URI)
        imageUriString?.let { uriString ->
            imageUri = Uri.parse(uriString)
            imageView.setImageURI(imageUri)
        }
        
        // Get OCR data if available  
        val ocrVendor = intent.getStringExtra("ocr_vendor")
        val ocrAmountStr = intent.getStringExtra("ocr_amount")
        val ocrDateTime = intent.getLongExtra("ocr_date", 0L)
        
        if (!ocrVendor.isNullOrEmpty() || !ocrAmountStr.isNullOrEmpty() || ocrDateTime != 0L) {
            showSuggestionCard(ocrVendor, ocrAmountStr, ocrDateTime)
        }
    }
    
    private fun observeViewModel() {
        viewModel.categories.observe(this) { categoryList ->
            categories = categoryList
            setupCategorySpinner(categoryList)
        }
        
        viewModel.saveState.observe(this) { state ->
            when (state) {
                ManualEntryViewModel.SaveState.Loading -> showProgress()
                is ManualEntryViewModel.SaveState.Success -> {
                    hideProgress()
                    // Navigate to receipt detail view
                    val intent = Intent(this, ReceiptDetailActivity::class.java).apply {
                        putExtra("receiptId", state.receiptId)
                    }
                    startActivity(intent)
                    setResult(RESULT_OK)
                    finish()
                }
                is ManualEntryViewModel.SaveState.Error -> {
                    hideProgress()
                    showError(state.message)
                }
                ManualEntryViewModel.SaveState.Idle -> {
                    hideProgress()
                }
            }
        }
    }
    
    private fun setupCategorySpinner(categories: List<CategoryDomain>) {
        val categoryNames = categories.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categoryNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner.adapter = adapter
        
        // Select default category if available
        val defaultCategoryIndex = categories.indexOfFirst { it.isDefault }
        if (defaultCategoryIndex >= 0) {
            categorySpinner.setSelection(defaultCategoryIndex)
        }
    }
    
    private fun showSuggestionCard(vendor: String?, amount: String?, dateTime: Long) {
        val suggestions = buildList {
            vendor?.let { add("Vendor: $it") }
            amount?.let { add("Amount: $$it") }
            if (dateTime != 0L) {
                val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                add("Date: ${formatter.format(Date(dateTime))}")
            }
        }
        
        if (suggestions.isNotEmpty()) {
            suggestionTextView.text = "Found: ${suggestions.joinToString(", ")}"
            ocrSuggestionCard.visibility = View.VISIBLE
        }
    }
    
    private fun hideSuggestionCard() {
        ocrSuggestionCard.visibility = View.GONE
    }
    
    private fun applySuggestions() {
        intent.getStringExtra("ocr_vendor")?.let { vendor ->
            vendorEditText.setText(vendor)
        }
        intent.getStringExtra("ocr_amount")?.let { amount ->
            amountEditText.setText(amount)
        }
        val dateTime = intent.getLongExtra("ocr_date", 0L)
        if (dateTime != 0L) {
            selectedDate = Date(dateTime)
            updateDateDisplay()
        }
        hideSuggestionCard()
        validateForm()
    }
    
    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        calendar.time = selectedDate
        
        val datePicker = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val newCalendar = Calendar.getInstance()
                newCalendar.set(year, month, dayOfMonth)
                selectedDate = newCalendar.time
                updateDateDisplay()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        
        datePicker.show()
    }
    
    private fun updateDateDisplay() {
        val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        dateTextView.text = formatter.format(selectedDate)
    }
    
    private fun validateAmount(): Boolean {
        val amountText = amountEditText.text.toString().trim()
        
        return try {
            if (amountText.isEmpty()) {
                amountEditText.error = "Amount is required"
                false
            } else {
                val amount = BigDecimal(amountText)
                if (amount <= BigDecimal.ZERO) {
                    amountEditText.error = "Amount must be greater than 0"
                    false
                } else if (amount > BigDecimal("99999.99")) {
                    amountEditText.error = "Amount is too large"
                    false
                } else {
                    amountEditText.error = null
                    true
                }
            }
        } catch (e: NumberFormatException) {
            amountEditText.error = "Please enter a valid amount"
            false
        }
    }
    
    private fun validateForm(): Boolean {
        val vendor = vendorEditText.text.toString().trim()
        val isAmountValid = validateAmount()
        val isVendorValid = vendor.isNotEmpty()
        
        if (!isVendorValid) {
            vendorEditText.error = "Vendor name is required"
        } else {
            vendorEditText.error = null
        }
        
        val isFormValid = isAmountValid && isVendorValid
        saveButton.isEnabled = isFormValid
        
        return isFormValid
    }
    
    private fun saveReceipt() {
        if (!validateForm()) return
        
        val vendor = vendorEditText.text.toString().trim()
        val amountText = amountEditText.text.toString().trim()
        val amount = BigDecimal(amountText)
        val notes = notesEditText.text.toString().trim()
        val selectedCategoryIndex = categorySpinner.selectedItemPosition
        
        if (selectedCategoryIndex >= 0 && selectedCategoryIndex < categories.size) {
            val selectedCategory = categories[selectedCategoryIndex]
            
            hideKeyboard()
            
            lifecycleScope.launch {
                viewModel.saveReceipt(
                    imageUri = imageUri,
                    vendor = vendor,
                    amount = amount,
                    date = selectedDate,
                    categoryId = selectedCategory.id,
                    notes = notes.takeIf { it.isNotEmpty() }
                )
            }
        } else {
            showError("Please select a category")
        }
    }
    
    private fun showProgress() {
        progressBar.visibility = View.VISIBLE
        saveButton.isEnabled = false
    }
    
    private fun hideProgress() {
        progressBar.visibility = View.GONE
        validateForm() // Re-enable save button if form is valid
    }
    
    private fun showError(message: String) {
        errorTextView.text = message
        errorTextView.visibility = View.VISIBLE
        
        Log.e(TAG, "Error: $message")
    }
    
    private fun hideError() {
        errorTextView.visibility = View.GONE
    }
    
    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val view = currentFocus ?: View(this)
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
    
    override fun onBackPressed() {
        if (hasUnsavedChanges()) {
            // Show confirmation dialog
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Discard Changes?")
                .setMessage("You have unsaved changes. Are you sure you want to leave?")
                .setPositiveButton("Discard") { _, _ ->
                    super.onBackPressed()
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            super.onBackPressed()
        }
    }
    
    private fun hasUnsavedChanges(): Boolean {
        return vendorEditText.text.toString().trim().isNotEmpty() ||
                amountEditText.text.toString().trim().isNotEmpty() ||
                notesEditText.text.toString().trim().isNotEmpty()
    }
}