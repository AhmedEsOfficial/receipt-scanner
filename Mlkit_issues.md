 Based on my analysis, I've identified the key issues with receipt recognition and database storage. Here's my comprehensive analysis and plan:

  Issues Identified

  1. Poor OCR Text Extraction

  - MLKit is only extracting 16-27 characters from receipt images
  - This suggests image quality issues or preprocessing problems
  - Very limited text means data extraction has minimal information to work with

  2. Data Extraction Failures

  - Amount extraction: Failing because regex patterns in DataExtractionService.kt:25-34 are too restrictive
  - Date extraction: No dates found, possibly due to short text or format mismatches
  - Vendor extraction: Working partially but with low confidence (0.5-0.6)

  3. Database Storage Integration

  - The CameraViewModel.saveReceipt() method on line 215 looks correct
  - However, receipts likely aren't being saved because saveReceipt() validation fails on line 243:
  if (vendor.isEmpty() || amount <= BigDecimal.ZERO) {
      return // Invalid data - prevents saving
  }

  4. Image Processing Issues

  - The low character count suggests images may need better preprocessing
  - No image enhancement or contrast adjustment before OCR

  Comprehensive Fix Plan

● Resolution Plan

  1. Image Preprocessing Enhancement (Critical)

  - Add image contrast/brightness adjustment before OCR
  - Implement grayscale conversion and sharpening
  - Add image rotation correction

  2. Fix Amount Extraction Patterns (High Priority)

  - Current regex patterns in DataExtractionService.kt:25-34 are too restrictive
  - Need to handle amounts without currency symbols
  - Add support for integers (e.g., "15", "125")
  - Improve decimal detection

  3. Improve Date Extraction (High Priority)

  - Expand date format patterns in DataExtractionService.kt:37-46
  - Add more flexible parsing
  - Handle timestamps and partial dates

  4. Database Storage Fallbacks (Critical)

  - Modify CameraViewModel.saveReceipt() line 243 validation logic
  - Allow saving receipts with partial data
  - Add flag for "needs review" receipts

  5. Enhanced OCR Confidence (Medium Priority)

  - Improve confidence calculation in TextRecognitionService.kt:183-194
  - Use actual MLKit confidence when available
  - Add context-based scoring

  Would you like me to implement these fixes? I recommend starting with the image preprocessing and amount extraction regex improvements as those will have the biggest impact on your OCR accuracy and        
  database storage issues.