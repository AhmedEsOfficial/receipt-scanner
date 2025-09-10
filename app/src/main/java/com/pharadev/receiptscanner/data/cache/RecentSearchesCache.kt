package com.pharadev.receiptscanner.data.cache

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.recentSearchesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "recent_searches"
)

@Singleton
class RecentSearchesCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val recentSearchesKey = stringSetPreferencesKey("recent_searches")
    
    companion object {
        private const val MAX_RECENT_SEARCHES = 10
    }
    
    val recentSearches: Flow<List<String>> = context.recentSearchesDataStore.data
        .map { preferences ->
            preferences[recentSearchesKey]?.toList()?.take(MAX_RECENT_SEARCHES) ?: emptyList()
        }
    
    suspend fun addRecentSearch(query: String) {
        if (query.trim().length >= 2) { // Only save searches with at least 2 characters
            context.recentSearchesDataStore.edit { preferences ->
                val currentSearches = preferences[recentSearchesKey]?.toMutableSet() ?: mutableSetOf()
                
                // Remove if already exists (to move to top)
                currentSearches.remove(query.trim())
                
                // Add to beginning
                val updatedSearches = mutableSetOf(query.trim()).apply {
                    addAll(currentSearches.take(MAX_RECENT_SEARCHES - 1))
                }
                
                preferences[recentSearchesKey] = updatedSearches
            }
        }
    }
    
    suspend fun clearRecentSearches() {
        context.recentSearchesDataStore.edit { preferences ->
            preferences.remove(recentSearchesKey)
        }
    }
}