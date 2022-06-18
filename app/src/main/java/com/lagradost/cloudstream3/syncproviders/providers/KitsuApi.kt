package com.lagradost.cloudstream3.syncproviders.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError

// modified code from from https://github.com/saikou-app/saikou/blob/main/app/src/main/java/ani/saikou/others/Kitsu.kt
// GNU General Public License v3.0 https://github.com/saikou-app/saikou/blob/main/LICENSE.md
object Kitsu {
    private suspend fun getKitsuData(query: String): KitsuResponse {
        val headers = mapOf(
            "Content-Type" to "application/json",
            "Accept" to "application/json",
            "Connection" to "keep-alive",
            "DNT" to "1",
            "Origin" to "https://kitsu.io"
        )

        return app.post(
            "https://kitsu.io/api/graphql",
            headers = headers,
            data = mapOf("query" to query)
        ).parsed()
    }

    private val cache: MutableMap<Pair<String, String>, Map<Int, KitsuResponse.Node>> =
        mutableMapOf()

    var isEnabled = true

    suspend fun getEpisodesDetails(
        malId: String?,
        anilistId: String?,
        isResponseRequired: Boolean = true, // overrides isEnabled
    ): Map<Int, KitsuResponse.Node>? {
        if (!isResponseRequired && !isEnabled) return null
        if (anilistId != null) {
            try {
                val map = getKitsuEpisodesDetails(anilistId, "ANILIST_ANIME")
                if (!map.isNullOrEmpty()) return map
            } catch (e: Exception) {
                logError(e)
            }
        }
        if (malId != null) {
            try {
                val map = getKitsuEpisodesDetails(malId, "MYANIMELIST_ANIME")
                if (!map.isNullOrEmpty()) return map
            } catch (e: Exception) {
                logError(e)
            }
        }
        return null
    }

    @Throws
    suspend fun getKitsuEpisodesDetails(id: String, site: String): Map<Int, KitsuResponse.Node>? {
        require(id.isNotBlank()) {
            "Black id"
        }

        require(site.isNotBlank()) {
            "invalid site"
        }

        if (cache.containsKey(id to site)) {
            return cache[id to site]
        }

        val query =
            """
query {
  lookupMapping(externalId: $id, externalSite: $site) {
    __typename
    ... on Anime {
      id
      episodes(first: 2000) {
        nodes {
          number
          titles {
            canonical
          }
          description
          thumbnail {
            original {
              url
            }
          }
        }
      }
    }
  }
}"""
        val result = getKitsuData(query)
        val map = (result.data?.lookupMapping?.episodes?.nodes ?: return null).mapNotNull { ep ->
            val num = ep?.num ?: return@mapNotNull null
            num to ep
        }.toMap()
        if (map.isNotEmpty()) {
            cache[id to site] = map
        }
        return map
    }

    data class KitsuResponse(
        val data: Data? = null
    ) {
        data class Data(
            val lookupMapping: LookupMapping? = null
        )

        data class LookupMapping(
            val id: String? = null,
            val episodes: Episodes? = null
        )

        data class Episodes(
            val nodes: List<Node?>? = null
        )

        data class Node(
            @JsonProperty("number")
            val num: Int? = null,
            val titles: Titles? = null,
            val description: Description? = null,
            val thumbnail: Thumbnail? = null
        )

        data class Description(
            val en: String? = null
        )

        data class Thumbnail(
            val original: Original? = null
        )

        data class Original(
            val url: String? = null
        )

        data class Titles(
            val canonical: String? = null
        )
    }
}