package com.pharadev.receiptscanner.domain.service

import android.content.SharedPreferences
import com.pharadev.receiptscanner.domain.model.CategoryDomain
import com.pharadev.receiptscanner.domain.repository.CategoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryLearningService @Inject constructor(
    private val sharedPreferences: SharedPreferences,
    private val categoryRepository: CategoryRepository,
    private val json: Json
) {
    
    companion object {
        private const val LEARNING_DATA_KEY = "category_learning_data"
        private const val MIN_CONFIDENCE_THRESHOLD = 0.7
        private const val LEARNING_WEIGHT = 0.85
    }

    @Serializable
    data class VendorLearning(
        val vendorName: String,
        val categoryId: Long,
        val correctionCount: Int = 1,
        val confidence: Double = LEARNING_WEIGHT
    )

    @Serializable
    data class KeywordLearning(
        val keyword: String,
        val categoryId: Long,
        val correctionCount: Int = 1,
        val confidence: Double = LEARNING_WEIGHT
    )

    @Serializable
    data class LearningData(
        val vendorLearnings: Map<String, VendorLearning> = emptyMap(),
        val keywordLearnings: Map<String, KeywordLearning> = emptyMap()
    )

    private var cachedLearningData: LearningData? = null

    suspend fun getSuggestedCategory(
        vendorName: String?,
        ocrText: String
    ): CategorySuggestion? = withContext(Dispatchers.IO) {
        
        val learningData = getLearningData()
        
        // Check vendor-specific learning first (highest priority)
        vendorName?.let { vendor ->
            val normalizedVendor = vendor.lowercase().trim()
            learningData.vendorLearnings[normalizedVendor]?.let { learning ->
                if (learning.confidence >= MIN_CONFIDENCE_THRESHOLD) {
                    val category = categoryRepository.getCategoryById(learning.categoryId)
                    return@withContext CategorySuggestion(
                        category = category,
                        confidence = learning.confidence,
                        reason = "Learned from vendor: $vendor (${learning.correctionCount} corrections)"
                    )
                }
            }
        }

        // Check keyword-based learning
        val words = extractKeywords(ocrText)
        val keywordMatches = mutableMapOf<Long, KeywordLearningMatch>()

        words.forEach { word ->
            learningData.keywordLearnings[word]?.let { learning ->
                val existingMatch = keywordMatches[learning.categoryId]
                if (existingMatch == null) {
                    keywordMatches[learning.categoryId] = KeywordLearningMatch(
                        categoryId = learning.categoryId,
                        confidence = learning.confidence,
                        matchedKeywords = listOf(word),
                        totalCorrections = learning.correctionCount
                    )
                } else {
                    keywordMatches[learning.categoryId] = existingMatch.copy(
                        confidence = (existingMatch.confidence + learning.confidence) / 2,
                        matchedKeywords = existingMatch.matchedKeywords + word,
                        totalCorrections = existingMatch.totalCorrections + learning.correctionCount
                    )
                }
            }
        }

        // Find the best keyword-based match
        val bestKeywordMatch = keywordMatches.values.maxByOrNull { it.confidence }
        
        return@withContext if (bestKeywordMatch != null && bestKeywordMatch.confidence >= MIN_CONFIDENCE_THRESHOLD) {
            val category = categoryRepository.getCategoryById(bestKeywordMatch.categoryId)
            CategorySuggestion(
                category = category,
                confidence = bestKeywordMatch.confidence,
                reason = "Learned from keywords: ${bestKeywordMatch.matchedKeywords.joinToString(", ")} (${bestKeywordMatch.totalCorrections} corrections)"
            )
        } else null
    }

    suspend fun recordUserCorrection(
        ocrText: String,
        vendorName: String?,
        originalSuggestion: CategoryDomain?,
        userChoice: CategoryDomain
    ) = withContext(Dispatchers.IO) {
        
        val learningData = getLearningData().toMutable()
        
        // Record vendor-specific learning if vendor name is available
        vendorName?.let { vendor ->
            val normalizedVendor = vendor.lowercase().trim()
            val existingLearning = learningData.vendorLearnings[normalizedVendor]
            
            if (existingLearning != null && existingLearning.categoryId == userChoice.id) {
                // Reinforce existing learning
                learningData.vendorLearnings[normalizedVendor] = existingLearning.copy(
                    correctionCount = existingLearning.correctionCount + 1,
                    confidence = minOf(0.98, existingLearning.confidence + 0.05)
                )
            } else {
                // New learning or category change
                learningData.vendorLearnings[normalizedVendor] = VendorLearning(
                    vendorName = normalizedVendor,
                    categoryId = userChoice.id,
                    correctionCount = 1,
                    confidence = LEARNING_WEIGHT
                )
            }
        }

        // Record keyword-based learning
        val keywords = extractKeywords(ocrText)
        keywords.forEach { keyword ->
            val existingLearning = learningData.keywordLearnings[keyword]
            
            if (existingLearning != null && existingLearning.categoryId == userChoice.id) {
                // Reinforce existing learning
                learningData.keywordLearnings[keyword] = existingLearning.copy(
                    correctionCount = existingLearning.correctionCount + 1,
                    confidence = minOf(0.95, existingLearning.confidence + 0.03)
                )
            } else if (existingLearning == null) {
                // New keyword learning (only add if not conflicting with existing)
                learningData.keywordLearnings[keyword] = KeywordLearning(
                    keyword = keyword,
                    categoryId = userChoice.id,
                    correctionCount = 1,
                    confidence = LEARNING_WEIGHT * 0.8 // Slightly lower confidence for keywords
                )
            }
        }

        // Save the updated learning data
        saveLearningData(learningData.toImmutable())
    }

    private fun extractKeywords(text: String): List<String> {
        return text.lowercase()
            .split(Regex("[\\s\\p{Punct}]+"))
            .filter { it.length >= 3 && !it.matches(Regex("\\d+")) }
            .distinct()
    }

    private fun getLearningData(): LearningData {
        if (cachedLearningData == null) {
            val jsonString = sharedPreferences.getString(LEARNING_DATA_KEY, null)
            cachedLearningData = if (jsonString != null) {
                try {
                    json.decodeFromString<LearningData>(jsonString)
                } catch (e: Exception) {
                    LearningData()
                }
            } else {
                LearningData()
            }
        }
        return cachedLearningData!!
    }

    private fun saveLearningData(learningData: LearningData) {
        cachedLearningData = learningData
        val jsonString = json.encodeToString(LearningData.serializer(), learningData)
        sharedPreferences.edit()
            .putString(LEARNING_DATA_KEY, jsonString)
            .apply()
    }

    suspend fun clearLearningData() = withContext(Dispatchers.IO) {
        cachedLearningData = LearningData()
        sharedPreferences.edit()
            .remove(LEARNING_DATA_KEY)
            .apply()
    }

    suspend fun getLearningStats(): LearningStats = withContext(Dispatchers.IO) {
        val learningData = getLearningData()
        LearningStats(
            vendorLearningsCount = learningData.vendorLearnings.size,
            keywordLearningsCount = learningData.keywordLearnings.size,
            totalCorrections = learningData.vendorLearnings.values.sumOf { it.correctionCount } +
                    learningData.keywordLearnings.values.sumOf { it.correctionCount }
        )
    }

    private data class KeywordLearningMatch(
        val categoryId: Long,
        val confidence: Double,
        val matchedKeywords: List<String>,
        val totalCorrections: Int
    )

    private fun LearningData.toMutable(): MutableLearningData {
        return MutableLearningData(
            vendorLearnings = this.vendorLearnings.toMutableMap(),
            keywordLearnings = this.keywordLearnings.toMutableMap()
        )
    }

    private fun MutableLearningData.toImmutable(): LearningData {
        return LearningData(
            vendorLearnings = this.vendorLearnings.toMap(),
            keywordLearnings = this.keywordLearnings.toMap()
        )
    }

    private data class MutableLearningData(
        val vendorLearnings: MutableMap<String, VendorLearning>,
        val keywordLearnings: MutableMap<String, KeywordLearning>
    )
}

data class LearningStats(
    val vendorLearningsCount: Int,
    val keywordLearningsCount: Int,
    val totalCorrections: Int
)