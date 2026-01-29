package com.lagradost.cloudstream3.ui.search

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.nicehttp.NiceResponse

/**
 * API for fetching search suggestions from external sources.
 * Uses Google's suggestion API which provides movie/show related suggestions.
 */
object SearchSuggestionApi {
    private const val GOOGLE_SUGGESTION_URL = "https://suggestqueries.google.com/complete/search"
    
    /**
     * Fetches search suggestions from Google's autocomplete API.
     * 
     * @param query The search query to get suggestions for
     * @return List of suggestion strings, empty list on failure
     */
    suspend fun getSuggestions(query: String): List<String> {
        if (query.isBlank() || query.length < 2) return emptyList()
        
        return try {
            val response = app.get(
                GOOGLE_SUGGESTION_URL,
                params = mapOf(
                    "client" to "firefox",  // Returns JSON format
                    "q" to query,
                    "hl" to "en"  // Language hint
                ),
                cacheTime = 60 * 24  // Cache for 1 day (cacheUnit default is Minutes)
            )
            
            // Response format: ["query",["suggestion1","suggestion2",...]]
            parseSuggestions(response)
        } catch (e: Exception) {
            logError(e)
            emptyList()
        }
    }
    
    /**
     * Parses the Google suggestion JSON response.
     * Format: ["query",["suggestion1","suggestion2",...]]
     */
    private fun parseSuggestions(response: NiceResponse): List<String> {
        return try {
            val parsed = response.parsed<Array<Any>>()
            val suggestions = parsed.getOrNull(1)
            when (suggestions) {
                is List<*> -> suggestions.filterIsInstance<String>().take(10)
                is Array<*> -> suggestions.filterIsInstance<String>().take(10)
                else -> emptyList()
            }
        } catch (e: Exception) {
            logError(e)
            emptyList()
        }
    }
}
