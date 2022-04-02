package com.lagradost.cloudstream3.ui.search

import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.syncproviders.OAuth2API
import com.lagradost.cloudstream3.syncproviders.SyncAPI

class SyncSearchViewModel {
    private val repos = OAuth2API.SyncApis

    data class SyncSearchResultSearchResponse(
        override val name: String,
        override val url: String,
        override val apiName: String,
        override var type: TvType?,
        override var posterUrl: String?,
        override var id: Int?,
        override var quality: SearchQuality? = null
    ) : SearchResponse

    private fun SyncAPI.SyncSearchResult.toSearchResponse(): SyncSearchResultSearchResponse {
        return SyncSearchResultSearchResponse(
            this.name,
            this.url,
            this.syncApiName,
            null,
            this.posterUrl,
            null, //this.id.hashCode()
        )
    }

}