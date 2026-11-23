# ReceiptSnap - Smart Receipt Organizer
## Technical Design Document v1.0

---

## Executive Summary

**ReceiptSnap** is a native Android application designed to simplify receipt management through intelligent scanning, categorization, and reporting capabilities. This document outlines the comprehensive technical architecture, implementation strategy, and operational framework for delivering a solution.

---

## 1. System Architecture

### 1.1 High-Level Architecture

```
┌─────────────────────────────────────────────────┐
│                 Presentation Layer               │
├─────────────────────────────────────────────────┤
│  Activities/Fragments  │  ViewModels  │  UI      │
│  - CameraActivity      │  - Receipt   │  - XML   │
│  - DashboardActivity   │  - Category  │  - Adapt │
│  - ReportActivity      │  - Report    │  - Views │
├─────────────────────────────────────────────────┤
│                 Business Layer                   │
├─────────────────────────────────────────────────┤
│  Use Cases             │  Repositories │ Services │
│  - ScanReceipt         │  - Receipt    │  - OCR   │
│  - CategorizeExpense   │  - Category   │  - Export│
│  - GenerateReport      │  - Settings   │  - Cloud │
├─────────────────────────────────────────────────┤
│                 Data Layer                       │
├─────────────────────────────────────────────────┤
│  Local Storage         │  External APIs│  Cache   │
│  - Room Database       │  - Google     │  - Image │
│  - SharedPreferences   │    ML Kit     │  - OCR   │
│  - File System         │  - Firebase   │  Results │
└─────────────────────────────────────────────────┘
```

### 1.2 Technology Stack

**Core Framework:**
- **Language:** Kotlin (100%)
- **Architecture:** MVVM + Clean Architecture
- **Dependency Injection:** Hilt
- **Async Operations:** Coroutines + Flow
- **UI Framework:** Jetpack Compose + XML (hybrid approach)

**Data & Storage:**
- **Local Database:** Room (SQLite)
- **Preferences:** DataStore (SharedPreferences successor)
- **File Storage:** Internal/External Storage APIs
- **Cloud Backup:** Firebase Storage + Firestore (Premium)

**External Services:**
- **OCR Engine:** Google ML Kit Text Recognition
- **Image Processing:** CameraX + Glide
- **PDF Generation:** iText7-Android
- **Analytics:** Firebase Analytics
- **Billing:** Google Play Billing Library v5

---

## 2. Database Design

### 2.1 Entity Relationship Diagram

```sql
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│    Receipt      │    │    Category      │    │   ExpenseLimit  │
├─────────────────┤    ├──────────────────┤    ├─────────────────┤
│ id (PK)         │◄──┐│ id (PK)          │◄──┐│ id (PK)         │
│ amount          │   └┤ receipt_id (FK)  │   └┤ category_id(FK) │
│ vendor_name     │    │ name             │    │ monthly_limit   │
│ date_created    │    │ color            │    │ current_spent   │
│ image_path      │    │ icon             │    │ created_at      │
│ ocr_text        │    │ is_default       │    └─────────────────┘
│ category_id(FK) │    │ created_at       │
│ notes          │    └──────────────────┘
│ is_synced      │
│ created_at     │    ┌──────────────────┐
│ updated_at     │    │   User_Settings  │
└─────────────────┘    ├──────────────────┤
                       │ id (PK)          │
                       │ currency         │
                       │ theme_mode       │
                       │ auto_backup      │
                       │ ocr_language     │
                       │ export_format    │
                       └──────────────────┘
```

### 2.2 Database Schema

```kotlin
@Entity(tableName = "receipts")
data class Receipt(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val amount: Double,
    val vendorName: String? = null,
    val dateCreated: Long = System.currentTimeMillis(),
    val imagePath: String,
    val ocrText: String? = null,
    val categoryId: String,
    val notes: String? = null,
    val isSynced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val color: String,
    val icon: String,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "expense_limits")
data class ExpenseLimit(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val categoryId: String,
    val monthlyLimit: Double,
    val currentSpent: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)
```

---

## 3. Feature Implementation Details

### 3.1 Camera & OCR Integration

```kotlin
class CameraService @Inject constructor(
    private val mlKitTextRecognizer: TextRecognizer
) {
    suspend fun captureAndProcessReceipt(imageUri: Uri): ReceiptScanResult {
        return withContext(Dispatchers.IO) {
            val inputImage = InputImage.fromFilePath(context, imageUri)
            val ocrResult = mlKitTextRecognizer.process(inputImage).await()
            
            ReceiptScanResult(
                imagePath = saveImageToStorage(imageUri),
                extractedText = ocrResult.text,
                detectedAmount = parseAmount(ocrResult.text),
                suggestedVendor = parseVendor(ocrResult.text),
                suggestedCategory = categorizeFromText(ocrResult.text)
            )
        }
    }
    
    private fun parseAmount(text: String): Double? {
        val amountRegex = """[\$€£¥₹]?(\d+\.?\d*)""".toRegex()
        return amountRegex.findAll(text)
            .mapNotNull { it.groupValues[1].toDoubleOrNull() }
            .maxOrNull()
    }
}
```

### 3.2 Auto-Categorization Logic

```kotlin
class CategoryService @Inject constructor(
    private val categoryRepository: CategoryRepository
) {
    private val categoryKeywords = mapOf(
        "Groceries" to listOf("walmart", "target", "grocery", "supermarket", "food"),
        "Fuel" to listOf("shell", "exxon", "gas", "fuel", "petrol"),
        "Restaurants" to listOf("restaurant", "cafe", "pizza", "burger", "dining"),
        "Transportation" to listOf("uber", "taxi", "bus", "train", "parking"),
        "Healthcare" to listOf("pharmacy", "hospital", "clinic", "medical", "doctor")
    )
    
    fun suggestCategory(ocrText: String, vendorName: String?): String? {
        val searchText = "$ocrText $vendorName".lowercase()
        
        return categoryKeywords.entries.find { (_, keywords) ->
            keywords.any { keyword -> searchText.contains(keyword) }
        }?.key
    }
}
```

### 3.3 Export & Reporting System

```kotlin
class ReportGenerator @Inject constructor(
    private val receiptRepository: ReceiptRepository
) {
    suspend fun generatePDFReport(
        startDate: Long,
        endDate: Long,
        categories: List<String>
    ): File {
        val receipts = receiptRepository.getReceiptsByDateRange(startDate, endDate, categories)
        val document = Document(PageSize.A4)
        val file = File(context.getExternalFilesDir(null), "report_${System.currentTimeMillis()}.pdf")
        
        PdfWriter.getInstance(document, FileOutputStream(file))
        document.open()
        
        // Add header
        document.add(Paragraph("Expense Report", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18f)))
        document.add(Paragraph("Period: ${formatDate(startDate)} - ${formatDate(endDate)}\n\n"))
        
        // Add summary table
        val table = PdfPTable(3)
        table.addCell("Category")
        table.addCell("Count")
        table.addCell("Total")
        
        receipts.groupBy { it.categoryId }.forEach { (categoryId, categoryReceipts) ->
            table.addCell(getCategoryName(categoryId))
            table.addCell(categoryReceipts.size.toString())
            table.addCell("$${categoryReceipts.sumOf { it.amount }}")
        }
        
        document.add(table)
        document.close()
        
        return file
    }
    
    suspend fun generateCSVReport(startDate: Long, endDate: Long): File {
        val receipts = receiptRepository.getReceiptsByDateRange(startDate, endDate)
        val file = File(context.getExternalFilesDir(null), "receipts_${System.currentTimeMillis()}.csv")
        
        file.bufferedWriter().use { writer ->
            writer.write("Date,Vendor,Category,Amount,Notes\n")
            receipts.forEach { receipt ->
                writer.write("${formatDate(receipt.dateCreated)},${receipt.vendorName},${getCategoryName(receipt.categoryId)},${receipt.amount},\"${receipt.notes}\"\n")
            }
        }
        
        return file
    }
}
```

---

## 4. User Interface Design

### 4.1 Navigation Structure

```
MainActivity (Bottom Navigation)
├── Dashboard Fragment
│   ├── Recent Receipts
│   ├── Monthly Summary
│   └── Quick Actions
├── Scan Fragment
│   ├── Camera Capture
│   ├── Gallery Import
│   └── Manual Entry
├── Categories Fragment
│   ├── Expense by Category
│   ├── Set Limits
│   └── Category Management
└── Reports Fragment
    ├── Date Range Selector
    ├── Export Options
    └── Expense Analytics
```

### 4.2 Key UI Components

```kotlin
@Composable
fun ReceiptCard(
    receipt: Receipt,
    category: Category,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = receipt.vendorName ?: "Unknown Vendor",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(parseColor(category.color))
                )
                Text(
                    text = formatDate(receipt.dateCreated),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Text(
                text = "$${receipt.amount}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
```

---

## 5. Premium Features

### 5.1 Feature Matrix

| Feature | Free Tier | Premium Tier |
|---------|-----------|--------------|
| Monthly Receipt Limit | 20 receipts | Unlimited |
| Auto-Categorization | ❌ | ✅ |
| Cloud Backup | ❌ | ✅ |
| Custom Categories | 5 categories | Unlimited |
| Export Formats | CSV only | PDF + CSV |
| Advanced Analytics | ❌ | ✅ |
| Receipt Search | ❌ | ✅ |
| Multiple Currency | ❌ | ✅ |
| Expense Limits | ❌ | ✅ |
| Data Export History | 3 months | Unlimited |

### 5.2 Billing Implementation

```kotlin
class BillingService @Inject constructor(
    private val context: Context
) {
    private lateinit var billingClient: BillingClient
    
    fun initializeBilling() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(purchaseUpdateListener)
            .enablePendingPurchases()
            .build()
            
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryAvailableProducts()
                }
            }
        })
    }
    
    private val purchaseUpdateListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        }
    }
    
    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // Unlock premium features
            PreferenceManager.setPremiumStatus(true)
            acknowledgePurchase(purchase)
        }
    }
}
```

---

## 6. Security & Privacy

### 6.1 Data Protection Strategy

**Local Data Security:**
- Encrypt sensitive receipt images using Android Keystore
- Hash all user preferences and settings
- Implement secure file deletion for removed receipts

**Cloud Sync Security:**
- End-to-end encryption for all synced data
- Use Firebase Auth for secure cloud access
- Implement data anonymization for analytics

```kotlin
class SecurityManager @Inject constructor() {
    
    fun encryptReceiptImage(imagePath: String): String {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val keyGenSpec = KeyGenParameterSpec.Builder(
            "receipt_encryption_key",
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
            
        keyGenerator.init(keyGenSpec)
        val secretKey = keyGenerator.generateKey()
        
        // Encrypt image file and return encrypted path
        return encryptFileWithKey(imagePath, secretKey)
    }
}
```

### 6.2 Privacy Compliance

**GDPR/CCPA Compliance:**
- Minimal data collection (no personal identification)
- Clear opt-in for cloud sync
- Easy data export and deletion
- Transparent privacy policy

**Data Retention Policy:**
- Local data: User controlled
- Cloud backup: 2 years maximum
- Analytics: Aggregated, anonymized, 13 months
- Crash reports: 90 days

---

## 7. Testing Strategy

### 7.1 Testing Pyramid

```
┌─────────────────────────────────────┐
│           E2E Tests (5%)            │  
│        - User Journey Tests         │
│        - Payment Flow Tests         │
├─────────────────────────────────────┤
│        Integration Tests (15%)       │
│     - Database Operations           │
│     - OCR Service Integration       │
│     - Export Functionality          │
├─────────────────────────────────────┤
│         Unit Tests (80%)            │
│    - ViewModels & Use Cases         │
│    - Repositories & Services        │
│    - Utility Functions              │
└─────────────────────────────────────┘
```

### 7.2 Test Implementation Examples

```kotlin
@RunWith(AndroidJUnit4::class)
class ReceiptRepositoryTest {
    
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()
    
    private lateinit var database: AppDatabase
    private lateinit var receiptDao: ReceiptDao
    private lateinit var repository: ReceiptRepository
    
    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        
        receiptDao = database.receiptDao()
        repository = ReceiptRepositoryImpl(receiptDao)
    }
    
    @Test
    fun insertReceipt_returnsCorrectReceipt() = runTest {
        val receipt = Receipt(
            id = "test-id",
            amount = 25.99,
            vendorName = "Test Store",
            imagePath = "/test/path",
            categoryId = "category-1"
        )
        
        repository.insertReceipt(receipt)
        val retrieved = repository.getReceiptById("test-id")
        
        assertThat(retrieved).isEqualTo(receipt)
    }
}
```

---

## 8. Performance Optimization

### 8.1 Image Processing Optimization

```kotlin
class ImageOptimizer {
    
    companion object {
        private const val MAX_IMAGE_SIZE = 1024 * 1024 // 1MB
        private const val JPEG_QUALITY = 85
    }
    
    fun optimizeReceiptImage(originalPath: String): String {
        val originalFile = File(originalPath)
        val bitmap = BitmapFactory.decodeFile(originalPath)
        
        // Resize if too large
        val optimizedBitmap = if (originalFile.length() > MAX_IMAGE_SIZE) {
            resizeBitmap(bitmap, 0.7f)
        } else bitmap
        
        // Compress and save
        val optimizedPath = generateOptimizedPath(originalPath)
        FileOutputStream(optimizedPath).use { out ->
            optimizedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        
        return optimizedPath
    }
    
    private fun resizeBitmap(bitmap: Bitmap, scaleFactor: Float): Bitmap {
        val width = (bitmap.width * scaleFactor).toInt()
        val height = (bitmap.height * scaleFactor).toInt()
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
}
```

### 8.2 Database Optimization

```kotlin
@Dao
interface ReceiptDao {
    
    @Query("SELECT * FROM receipts WHERE date_created >= :startDate AND date_created <= :endDate ORDER BY date_created DESC")
    fun getReceiptsByDateRange(startDate: Long, endDate: Long): Flow<List<Receipt>>
    
    @Query("SELECT * FROM receipts WHERE category_id IN (:categoryIds) ORDER BY date_created DESC LIMIT :limit")
    fun getReceiptsByCategories(categoryIds: List<String>, limit: Int = 50): Flow<List<Receipt>>
    
    @Query("SELECT SUM(amount) FROM receipts WHERE category_id = :categoryId AND date_created >= :monthStart")
    suspend fun getCategorySpendingThisMonth(categoryId: String, monthStart: Long): Double?
    
    // Indexes for performance
    @Query("CREATE INDEX IF NOT EXISTS idx_receipts_date_created ON receipts(date_created)")
    fun createDateIndex()
    
    @Query("CREATE INDEX IF NOT EXISTS idx_receipts_category ON receipts(category_id)")
    fun createCategoryIndex()
}
```

---

## 9. Development Timeline

### Phase 1: Core MVP (Weeks 1-4)
**Week 1-2: Foundation**
- Project setup, architecture implementation
- Database schema and basic models
- Camera integration and basic OCR

**Week 3-4: Core Features**
- Receipt scanning and storage
- Basic categorization
- Simple dashboard with receipt list

### Phase 2: Essential Features (Weeks 5-6)
**Week 5:**
- Category management
- Manual receipt entry
- Basic export (CSV)

**Week 6:**
- UI polish and Material Design implementation
- Dark mode support
- Basic analytics

### Phase 3: Premium Features (Weeks 7-8)
**Week 7:**
- Premium billing integration
- Advanced auto-categorization
- PDF export functionality

**Week 8:**
- Cloud backup implementation
- Final testing and optimization
- Play Store preparation

---

## 10. Infrastructure & DevOps

### 10.1 Build Configuration

```gradle
// app/build.gradle
android {
    compileSdk 34
    
    defaultConfig {
        applicationId "com.receiptsnap.app"
        minSdk 24
        targetSdk 34
        versionCode 1
        versionName "1.0.0"
        
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
        
        buildConfigField "String", "API_BASE_URL", "\"https://api.receiptsnap.com/\""
        buildConfigField "boolean", "DEBUG_MODE", "true"
    }
    
    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            
            signingConfig = signingConfigs.release
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
```

### 10.2 CI/CD Pipeline (GitHub Actions)

```yaml
name: Android CI/CD

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    - name: Set up JDK 11
      uses: actions/setup-java@v3
      with:
        java-version: '11'
        distribution: 'temurin'
        
    - name: Cache Gradle packages
      uses: actions/cache@v3
      with:
        path: |
          ~/.gradle/caches
          ~/.gradle/wrapper
        key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*') }}
        
    - name: Run tests
      run: ./gradlew test
      
    - name: Run instrumented tests
      uses: reactivecircus/android-emulator-runner@v2
      with:
        api-level: 29
        script: ./gradlew connectedAndroidTest
        
  build:
    needs: test
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    
    steps:
    - name: Build APK
      run: ./gradlew assembleRelease
      
    - name: Sign APK
      uses: r0adkll/sign-android-release@v1
      with:
        releaseDirectory: app/build/outputs/apk/release
        signingKeyBase64: ${{ secrets.SIGNING_KEY }}
        alias: ${{ secrets.ALIAS }}
        keyStorePassword: ${{ secrets.KEY_STORE_PASSWORD }}
        
    - name: Upload to Play Console
      uses: r0adkll/upload-google-play@v1
      with:
        serviceAccountJsonPlainText: ${{ secrets.SERVICE_ACCOUNT_JSON }}
        packageName: com.receiptsnap.app
        releaseFiles: app/build/outputs/apk/release/*.apk
        track: internal
```

---

## 11. Analytics & Monitoring

### 11.1 Key Metrics to Track

**User Engagement:**
- Daily/Monthly Active Users
- Session duration and frequency
- Feature adoption rates (scan, categorize, export)
- Retention rates (Day 1, 7, 30)

**Technical Metrics:**
- App crashes and ANRs
- OCR accuracy rates
- Export success rates
- Cloud sync reliability

### 11.2 Implementation

```kotlin
class AnalyticsManager @Inject constructor(
    private val firebaseAnalytics: FirebaseAnalytics,
    private val crashlytics: FirebaseCrashlytics
) {
    
    fun logReceiptScanned(ocrSuccess: Boolean, processingTime: Long) {
        val bundle = Bundle().apply {
            putBoolean("ocr_success", ocrSuccess)
            putLong("processing_time_ms", processingTime)
        }
        firebaseAnalytics.logEvent("receipt_scanned", bundle)
    }
    
    fun logFeatureUsed(featureName: String, isPremiumFeature: Boolean) {
        val bundle = Bundle().apply {
            putString("feature_name", featureName)
            putBoolean("is_premium", isPremiumFeature)
        }
        firebaseAnalytics.logEvent("feature_used", bundle)
    }
    
    fun logPremiumConversion(conversionSource: String) {
        val bundle = Bundle().apply {
            putString("conversion_source", conversionSource)
        }
        firebaseAnalytics.logEvent("premium_conversion", bundle)
    }
}
```

---

## 12. Risk Assessment & Mitigation

### 12.1 Technical Risks

| Risk | Impact | Probability | Mitigation Strategy |
|------|--------|-------------|-------------------|
| OCR Accuracy Issues | High | Medium | Implement manual correction UI, multiple OCR providers fallback |
| Performance on Low-end Devices | Medium | High | Optimize image processing, implement quality settings |
| Data Loss/Corruption | High | Low | Implement automatic backups, data validation |
| Third-party Service Outages | Medium | Medium | Graceful degradation, offline mode prioritization |

### 12.2 Business Risks

| Risk | Impact | Probability | Mitigation Strategy |
|------|--------|-------------|-------------------|
| Low User Adoption | High | Medium | Strong MVP, user testing |
| Competition from Major Players | High | Low | Focus on simplicity niche, rapid iteration |
| Google Play Policy Changes | Medium | Low | Monitor policy updates, maintain compliance |

---

## 13. Conclusion

**ReceiptSnap** aims to simplify expense tracking with a local-first, photo-based workflow. This document describes the intended technical architecture; see the README for what is implemented today.

**Guiding principles:**
1. **Simplicity first** - avoid feature creep that complicates the core scan-and-categorize flow
2. **Privacy by default** - on-device OCR and local storage
3. **Incremental delivery** - ship the MVP, then iterate on feedback
