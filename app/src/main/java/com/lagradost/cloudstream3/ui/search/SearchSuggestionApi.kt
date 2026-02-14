package com.lagradost.cloudstream3.ui.search

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.nicehttp.NiceResponse

/**
 * API for fetching search suggestions from external sources.
 * Uses TheMovieDB API to provide movie/show/anime related suggestions.
 */
object SearchSuggestionApi {
    private const val TMDB_API_URL = "https://api.themoviedb.org/3/search/multi"
    private const val TMDB_API_KEY = "e6333b32409e02a4a6eba6fb7ff866bb"
    
    data class TmdbSearchResult(
        @JsonProperty("results") val results: List<TmdbSearchItem>?
    )
    
    data class TmdbSearchItem(
        @JsonProperty("media_type") val mediaType: String?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("name") val name: String?,
        @JsonProperty("original_title") val originalTitle: String?,
        @JsonProperty("original_name") val originalName: String?
    )
    
    /**
     * Fetches search suggestions from TheMovieDB multi search API.
     * Returns suggestions for movies, TV series, and anime.
     * 
     * @param query The search query to get suggestions for
     * @return List of suggestion strings, empty list on failure
     */
    suspend fun getSuggestions(query: String): List<String> {
        if (query.isBlank() || query.length < 2) return emptyList()
        
        return try {
            val response = app.get(
                TMDB_API_URL,
                params = mapOf(
                    "api_key" to TMDB_API_KEY,
                    "query" to query,
                    "language" to "en-US"
                ),
                cacheTime = 60 * 24  // Cache for 1 day (cacheUnit default is Minutes)
            )
            
            parseSuggestions(response)
        } catch (e: Exception) {
            logError(e)
            emptyList()
        }
    }
    
    /**
     * Parses the TMDB search response and extracts movie/TV show titles.
     * Filters to only include movies, TV shows, and anime.
     */
    private fun parseSuggestions(response: NiceResponse): List<String> {
        return try {
            val parsed = response.parsed<TmdbSearchResult>()
            parsed.results
                ?.filter { it.mediaType == "movie" || it.mediaType == "tv" }
                ?.mapNotNull { it.title ?: it.name }
                ?.distinct()
                ?.take(10)
                ?: emptyList()
        } catch (e: Exception) {
            logError(e)
            emptyList()
        }
    }
}
