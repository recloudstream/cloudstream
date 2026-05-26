package com.lagradost.cloudstream3.metadataproviders

import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.app
import java.util.concurrent.TimeUnit
import android.util.Log

object MetadataRepository {
    private const val MDBLIST_API_URL = "https://mdblist.com/api/"
    private const val TAG = "MetadataRepository"

    /**
     * Fetches ratings from MDBList.
     * Uses Cloudstream's native `app.get` with built-in caching.
     * Cached for 7 days so we don't spam the MDBList API.
     */
    suspend fun getRatings(title: String, year: Int? = null, imdbId: String? = null): MdbListResponse? {
        val apiKey = BuildConfig.MDBLIST_API_KEY
        if (apiKey.isBlank()) {
            Log.w(TAG, "MDBList API Key is missing in BuildConfig")
            return null
        }
        
        return try {
            val url = if (!imdbId.isNullOrBlank()) {
                "$MDBLIST_API_URL?apikey=$apiKey&i=$imdbId"
            } else {
                "$MDBLIST_API_URL?apikey=$apiKey&s=$title" + (if (year != null) "&y=$year" else "")
            }
            
            // `app.get` automatically handles OkHttp caching
            val response = app.get(
                url = url,
                cacheTime = 7,
                cacheUnit = TimeUnit.DAYS
            )
            
            response.parsedSafe<MdbListResponse>()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch MDBList ratings: ${e.message}")
            null
        }
    }
}
