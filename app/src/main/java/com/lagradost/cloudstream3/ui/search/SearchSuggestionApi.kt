package com.lagradost.cloudstream3.ui.search

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError

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
                cacheTime = 60  // Cache for 1 minute
            )
            
            // Response format: ["query",["suggestion1","suggestion2",...]]
            val text = response.text
            parseSuggestions(text)
        } catch (e: Exception) {
            logError(e)
            emptyList()
        }
    }
    
    /**
     * Parses the Google suggestion JSON response.
     * Format: ["query",["suggestion1","suggestion2",...]]
     */
    private fun parseSuggestions(json: String): List<String> {
        return try {
            // Simple parsing without full JSON library
            // Find the array between the first [ after the query and the last ]
            val startIndex = json.indexOf("[", json.indexOf(","))
            val endIndex = json.lastIndexOf("]")
            
            if (startIndex == -1 || endIndex == -1 || startIndex >= endIndex) {
                return emptyList()
            }
            
            val arrayContent = json.substring(startIndex + 1, endIndex)
            
            // Extract strings from the array
            val suggestions = mutableListOf<String>()
            var inQuote = false
            var currentString = StringBuilder()
            var escaped = false
            
            for (char in arrayContent) {
                when {
                    escaped -> {
                        currentString.append(char)
                        escaped = false
                    }
                    char == '\\' -> {
                        escaped = true
                    }
                    char == '"' -> {
                        if (inQuote) {
                            suggestions.add(currentString.toString())
                            currentString = StringBuilder()
                        }
                        inQuote = !inQuote
                    }
                    inQuote -> {
                        currentString.append(char)
                    }
                }
            }
            
            suggestions.take(10) // Limit to 10 suggestions
        } catch (e: Exception) {
            logError(e)
            emptyList()
        }
    }
}
