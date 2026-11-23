# ML Kit OCR notes

Development notes on getting on-device text recognition working reliably. They
record problems seen during MVP development and what was changed in response.

## Problem

Early test scans returned only 16-27 characters of text per receipt, so
amount, date and vendor extraction mostly failed and receipts were rejected by
the save validation. Device logs also showed NNAPI delegate errors, which
pointed at the ML Kit setup rather than the extraction logic alone.

## Changes made

| Area | Change |
|------|--------|
| Dependency | Use the Google Play services variant `com.google.android.gms:play-services-mlkit-text-recognition` (plus `play-services-base` / `play-services-tasks`) so the model is delivered and updated by Play services. |
| Manifest | Added the `com.google.mlkit.vision.DEPENDENCIES` meta-data (`ocr`) so the model is downloaded at install time, and the `INTERNET` permission for model downloads. |
| `TextRecognitionService` | Checks Google Play services availability, detects NNAPI/hardware-acceleration errors and retries once with a second recognizer (CPU fallback), and logs diagnostics. |
| `ImagePreprocessingService` | Resize, grayscale, contrast/brightness adjustment, sharpening and light denoising before OCR. |
| `DataExtractionService` | Broader amount patterns (no currency symbol, integers, varied decimals) and more date formats. |
| Save flow | Receipts with partial data are saved with a quality flag for review instead of being dropped. |

## Troubleshooting

- The device needs Google Play services, up to date. The first scan needs a
  network connection if the model has not been downloaded yet.
- Clearing app data forces a model re-download.
- If Play services cannot be relied on, switch to the bundled model
  (`com.google.mlkit:text-recognition`), which makes the APK larger but removes
  the Play services dependency.

## Status

OCR accuracy has not been benchmarked, and the unit tests cover the
categorization logic only; the OCR pipeline has no automated tests yet.
