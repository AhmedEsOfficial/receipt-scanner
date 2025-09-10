package com.pharadev.receiptscanner.domain.service

import com.pharadev.receiptscanner.domain.model.CategoryDomain
import com.pharadev.receiptscanner.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoCategorizationService @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val categoryLearningService: CategoryLearningService
) {
    
    companion object {
        private const val CONFIDENCE_THRESHOLD = 0.7
        
        private val CATEGORY_KEYWORDS = mapOf(
            "Groceries" to listOf(
                // Major grocery chains
                "walmart", "target", "costco", "kroger", "safeway", "publix", "trader joe", "whole foods",
                "food lion", "harris teeter", "giant", "acme", "weis", "shoprite", "stop & shop",
                "king soopers", "fred meyer", "meijer", "hy-vee", "wegmans",
                // Generic grocery terms
                "grocery", "supermarket", "market", "food", "produce", "deli", "bakery",
                "organic", "fresh", "milk", "bread", "meat", "vegetables", "fruit"
            ),
            "Fuel" to listOf(
                // Gas station chains
                "shell", "exxon", "chevron", "bp", "mobil", "texaco", "citgo", "arco",
                "sunoco", "marathon", "speedway", "wawa", "sheetz", "quicktrip", "racetrac",
                "circle k", "casey's", "valero", "phillips 66", "conoco",
                // Fuel terms
                "gas", "fuel", "petrol", "gasoline", "diesel", "station", "pump"
            ),
            "Restaurants" to listOf(
                // Fast food chains
                "mcdonald", "burger king", "wendy", "taco bell", "kfc", "subway", "pizza hut",
                "domino", "papa john", "starbucks", "dunkin", "chipotle", "panera", "chick-fil-a",
                "in-n-out", "five guys", "shake shack", "sonic", "dairy queen", "arby",
                "popeyes", "jack in the box", "carl jr", "hardee", "white castle",
                // Restaurant types and terms
                "restaurant", "cafe", "diner", "bistro", "grill", "bar", "pub", "tavern",
                "pizza", "burger", "taco", "sushi", "chinese", "italian", "mexican",
                "dining", "food court", "drive thru", "takeout", "delivery"
            ),
            "Transportation" to listOf(
                // Ride sharing and taxi
                "uber", "lyft", "taxi", "cab", "yellow cab", "checker cab",
                // Public transport
                "bus", "metro", "subway", "train", "rail", "transit", "mta", "bart",
                "amtrak", "greyhound", "megabus",
                // Parking and tolls
                "parking", "garage", "meter", "toll", "bridge", "turnpike",
                // Car services
                "car wash", "auto", "mechanic", "tire", "oil change", "repair",
                // Airlines and travel
                "airline", "airport", "flight", "united", "american airlines", "delta",
                "southwest", "jetblue", "alaska", "frontier", "spirit"
            ),
            "Healthcare" to listOf(
                // Pharmacies
                "cvs", "walgreens", "rite aid", "pharmacy", "drug store", "medicine",
                // Healthcare facilities
                "hospital", "clinic", "medical", "doctor", "physician", "dentist",
                "urgent care", "emergency", "health", "wellness",
                // Medical terms
                "prescription", "medication", "pills", "treatment", "therapy",
                "check-up", "visit", "appointment", "surgery", "dental"
            ),
            "Entertainment" to listOf(
                // Movie theaters
                "amc", "regal", "cinemark", "movie", "theater", "cinema", "film",
                // Streaming and digital
                "netflix", "hulu", "disney", "amazon prime", "spotify", "apple music",
                "youtube", "twitch", "steam", "playstation", "xbox", "nintendo",
                // Events and venues
                "concert", "show", "event", "ticket", "venue", "stadium", "arena",
                "museum", "zoo", "park", "festival", "fair", "carnival",
                // Gaming and hobbies
                "game", "toy", "hobby", "sport", "recreation", "fun", "entertainment"
            ),
            "Shopping" to listOf(
                // Department stores
                "macy", "nordstrom", "jcpenney", "kohls", "sears", "dillard",
                // Retail chains
                "amazon", "best buy", "home depot", "lowe", "bed bath beyond",
                "tj maxx", "marshall", "ross", "old navy", "gap", "h&m", "zara",
                // Online shopping
                "ebay", "etsy", "shopify", "paypal",
                // Shopping terms
                "shopping", "retail", "store", "mall", "outlet", "purchase",
                "clothes", "clothing", "apparel", "shoes", "electronics", "furniture",
                "appliance", "tool", "equipment", "supplies"
            ),
            "Utilities" to listOf(
                // Utility companies
                "electric", "electricity", "power", "energy", "gas company", "water",
                "sewer", "trash", "garbage", "waste", "recycling",
                // Telecom
                "phone", "wireless", "cellular", "internet", "cable", "satellite",
                "verizon", "att", "t-mobile", "sprint", "comcast", "charter", "cox",
                // Utilities terms
                "utility", "bill", "service", "municipal", "city", "county"
            )
        )
    }

    suspend fun suggestCategory(
        ocrText: String,
        vendorName: String? = null,
        amount: Double? = null
    ): CategorySuggestion {
        
        // First check learned associations from user corrections
        val learnedSuggestion = categoryLearningService.getSuggestedCategory(vendorName, ocrText)
        if (learnedSuggestion != null && learnedSuggestion.confidence >= CONFIDENCE_THRESHOLD) {
            return learnedSuggestion
        }

        // Combine all text for analysis
        val searchText = buildString {
            append(ocrText)
            vendorName?.let { append(" $it") }
        }.lowercase().trim()

        // Calculate confidence scores for each category
        val categoryScores = mutableMapOf<String, Double>()
        
        CATEGORY_KEYWORDS.forEach { (categoryName, keywords) ->
            val score = calculateKeywordScore(searchText, keywords)
            if (score > 0) {
                categoryScores[categoryName] = score
            }
        }

        // Get the best matching category
        val bestMatch = categoryScores.maxByOrNull { it.value }
        
        return if (bestMatch != null && bestMatch.value >= CONFIDENCE_THRESHOLD) {
            val category = categoryRepository.getCategoryByName(bestMatch.key)
            CategorySuggestion(
                category = category,
                confidence = bestMatch.value,
                reason = "Keyword matching"
            )
        } else {
            // Default to first category (Groceries) with low confidence
            val defaultCategory = categoryRepository.getCategoryById(1L)
            CategorySuggestion(
                category = defaultCategory,
                confidence = 0.3,
                reason = "Default suggestion"
            )
        }
    }

    private fun calculateKeywordScore(text: String, keywords: List<String>): Double {
        var score = 0.0
        var matchedKeywords = 0
        val totalKeywords = keywords.size

        keywords.forEach { keyword ->
            if (text.contains(keyword.lowercase())) {
                // Give higher weight to exact matches and brand names
                val weight = when {
                    keyword.length > 6 -> 2.0  // Brand names get higher weight
                    text.contains(" $keyword ") -> 1.5  // Exact word matches
                    else -> 1.0
                }
                score += weight
                matchedKeywords++
            }
        }

        // Normalize score based on keyword density and matches
        return if (matchedKeywords > 0) {
            val density = matchedKeywords.toDouble() / totalKeywords
            val matchStrength = score / matchedKeywords
            (density * 0.3 + matchStrength * 0.7).coerceAtMost(1.0)
        } else {
            0.0
        }
    }

    suspend fun learnFromUserCorrection(
        ocrText: String,
        vendorName: String?,
        originalSuggestion: CategoryDomain?,
        userSelectedCategory: CategoryDomain
    ) {
        categoryLearningService.recordUserCorrection(
            ocrText = ocrText,
            vendorName = vendorName,
            originalSuggestion = originalSuggestion,
            userChoice = userSelectedCategory
        )
    }
}

data class CategorySuggestion(
    val category: CategoryDomain?,
    val confidence: Double,
    val reason: String
)