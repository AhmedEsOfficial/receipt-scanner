package com.pharadev.receiptscanner.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.pharadev.receiptscanner.domain.model.ReceiptDomain
import com.pharadev.receiptscanner.domain.repository.ReceiptRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class DuplicateDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val receiptRepository: ReceiptRepository
) {
    
    companion object {
        private const val SIMILARITY_THRESHOLD = 0.95 // 95% similarity threshold
        private const val THUMBNAIL_SIZE = 64 // Size for perceptual hash calculation
    }
    
    /**
     * Check if an image is a duplicate of an existing receipt
     */
    suspend fun checkForDuplicates(imageUri: Uri): DuplicateCheckResult = withContext(Dispatchers.IO) {
        try {
            // Calculate hash for the new image
            val newImageHash = calculatePerceptualHash(imageUri)
            if (newImageHash.isEmpty()) {
                return@withContext DuplicateCheckResult.Error("Failed to process image")
            }
            
            // Get all existing receipts
            val existingReceipts = receiptRepository.getRecentReceipts(1000) // Get recent 1000 receipts for comparison
            
            // Check for duplicates
            val duplicates = mutableListOf<DuplicateMatch>()
            
            for (receipt in existingReceipts) {
                if (receipt.imagePath.isNotEmpty() && File(receipt.imagePath).exists()) {
                    val existingHash = calculatePerceptualHashFromPath(receipt.imagePath)
                    if (existingHash.isNotEmpty()) {
                        val similarity = calculateSimilarity(newImageHash, existingHash)
                        
                        if (similarity >= SIMILARITY_THRESHOLD) {
                            duplicates.add(
                                DuplicateMatch(
                                    receipt = receipt,
                                    similarity = similarity,
                                    matchType = when {
                                        similarity >= 0.99 -> MatchType.EXACT
                                        similarity >= 0.95 -> MatchType.VERY_SIMILAR
                                        else -> MatchType.SIMILAR
                                    }
                                )
                            )
                        }
                    }
                }
            }
            
            if (duplicates.isNotEmpty()) {
                DuplicateCheckResult.DuplicatesFound(duplicates.sortedByDescending { it.similarity })
            } else {
                DuplicateCheckResult.NoDuplicates
            }
            
        } catch (e: Exception) {
            DuplicateCheckResult.Error("Duplicate check failed: ${e.message}")
        }
    }
    
    /**
     * Calculate perceptual hash (pHash) for image duplicate detection
     */
    private suspend fun calculatePerceptualHash(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    calculatePerceptualHashFromBitmap(bitmap)
                } else {
                    ""
                }
            } ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    
    private fun calculatePerceptualHashFromPath(imagePath: String): String {
        return try {
            val bitmap = BitmapFactory.decodeFile(imagePath)
            if (bitmap != null) {
                calculatePerceptualHashFromBitmap(bitmap)
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }
    
    private fun calculatePerceptualHashFromBitmap(bitmap: Bitmap): String {
        try {
            // Resize to standard size
            val resized = Bitmap.createScaledBitmap(bitmap, THUMBNAIL_SIZE, THUMBNAIL_SIZE, true)
            
            // Convert to grayscale and get pixel values
            val pixels = IntArray(THUMBNAIL_SIZE * THUMBNAIL_SIZE)
            val grayscaleValues = DoubleArray(THUMBNAIL_SIZE * THUMBNAIL_SIZE)
            
            for (y in 0 until THUMBNAIL_SIZE) {
                for (x in 0 until THUMBNAIL_SIZE) {
                    val pixel = resized.getPixel(x, y)
                    val gray = (0.299 * ((pixel shr 16) and 0xFF) +
                              0.587 * ((pixel shr 8) and 0xFF) +
                              0.114 * (pixel and 0xFF))
                    grayscaleValues[y * THUMBNAIL_SIZE + x] = gray
                }
            }
            
            // Apply DCT (simplified version)
            val dctValues = appleDCT(grayscaleValues)
            
            // Get average of DCT values (excluding DC component)
            val dctAverage = dctValues.drop(1).take(63).average()
            
            // Generate hash based on DCT values compared to average
            val hashBits = StringBuilder()
            for (i in 1 until 64) {
                hashBits.append(if (dctValues[i] > dctAverage) "1" else "0")
            }
            
            // Convert binary string to hex
            return binaryToHex(hashBits.toString())
            
        } catch (e: Exception) {
            return ""
        }
    }
    
    /**
     * Simplified DCT implementation for perceptual hashing
     */
    private fun appleDCT(values: DoubleArray): DoubleArray {
        val size = THUMBNAIL_SIZE
        val result = DoubleArray(size * size)
        
        for (u in 0 until 8) {
            for (v in 0 until 8) {
                var sum = 0.0
                for (x in 0 until size) {
                    for (y in 0 until size) {
                        sum += values[y * size + x] *
                               kotlin.math.cos((2 * x + 1) * u * kotlin.math.PI / 16) *
                               kotlin.math.cos((2 * y + 1) * v * kotlin.math.PI / 16)
                    }
                }
                val cu = if (u == 0) 1.0 / kotlin.math.sqrt(2.0) else 1.0
                val cv = if (v == 0) 1.0 / kotlin.math.sqrt(2.0) else 1.0
                result[v * 8 + u] = 0.25 * cu * cv * sum
            }
        }
        
        return result
    }
    
    private fun binaryToHex(binary: String): String {
        val hex = StringBuilder()
        for (i in binary.indices step 4) {
            val chunk = binary.substring(i, minOf(i + 4, binary.length)).padEnd(4, '0')
            val value = chunk.toInt(2)
            hex.append(value.toString(16))
        }
        return hex.toString()
    }
    
    /**
     * Calculate Hamming distance between two hashes to determine similarity
     */
    private fun calculateSimilarity(hash1: String, hash2: String): Double {
        if (hash1.length != hash2.length) return 0.0
        
        var differences = 0
        for (i in hash1.indices) {
            if (hash1[i] != hash2[i]) differences++
        }
        
        return 1.0 - (differences.toDouble() / hash1.length.toDouble())
    }
    
    /**
     * Calculate file-based hash for additional duplicate detection
     */
    suspend fun calculateFileHash(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val digest = MessageDigest.getInstance("MD5")
                val buffer = ByteArray(8192)
                var bytesRead: Int
                
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
                
                digest.digest().joinToString("") { "%02x".format(it) }
            } ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}

sealed class DuplicateCheckResult {
    object NoDuplicates : DuplicateCheckResult()
    data class DuplicatesFound(val matches: List<DuplicateMatch>) : DuplicateCheckResult()
    data class Error(val message: String) : DuplicateCheckResult()
}

data class DuplicateMatch(
    val receipt: ReceiptDomain,
    val similarity: Double,
    val matchType: MatchType
)

enum class MatchType {
    EXACT,      // 99%+ similarity - likely exact duplicate
    VERY_SIMILAR, // 95-99% similarity - very likely duplicate
    SIMILAR     // 85-95% similarity - possibly duplicate
}