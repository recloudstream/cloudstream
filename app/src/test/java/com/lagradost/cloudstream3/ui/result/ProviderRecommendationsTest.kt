package com.lagradost.cloudstream3.ui.result

import com.lagradost.cloudstream3.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ProviderRecommendationsTest {
    private val source = object : MainAPI() { override var name = "Selected" }
    private val other = object : MainAPI() { override var name = "Other" }

    @Test fun keepsTwelveSelectedRecommendationsAndExcludesCurrentAndForeignTitles() = runBlocking {
        val item = source.newMovieLoadResponse("Current", "https://example.com/current", TvType.Movie, "video") {
            recommendations = listOf(source.newMovieSearchResponse("Current", "https://example.com/current", fix=false),
                other.newMovieSearchResponse("Foreign", "https://example.com/foreign", fix=false)) +
                (1..14).map { source.newMovieSearchResponse("Film $it", "https://example.com/$it", fix=false) }
        }
        var result = emptyList<SearchResponse>()
        ProviderRecommendations.collect(item,listOf(source),12) { result = it }
        assertEquals(12,result.size)
        assertTrue(result.all { it.apiName == source.name })
        assertFalse(result.any { it.name == "Current" || it.name == "Foreign" })
    }

    @Test fun retainsSixOptionAndDeduplicatesEquivalentTitles() = runBlocking {
        val item = source.newMovieLoadResponse("Current", "https://example.com/current", TvType.Movie, "video") {
            recommendations = listOf("Película!", "Pelicula") .mapIndexed { index, name ->
                source.newMovieSearchResponse(name,"https://example.com/duplicate$index", fix=false)
            } + (1..8).map { source.newMovieSearchResponse("Film $it","https://example.com/$it",fix=false) }
        }
        var result = emptyList<SearchResponse>()
        ProviderRecommendations.collect(item,listOf(source),6) { result = it }
        assertEquals(6,result.size)
        assertEquals(1,result.count { it.name == "Película!" || it.name == "Pelicula" })
    }

    @Test fun doesNotFabricateMissingRecommendations() = runBlocking {
        val item = source.newMovieLoadResponse("Current", "https://example.com/current", TvType.Movie, "video")
        var result = emptyList<SearchResponse>()
        ProviderRecommendations.collect(item,emptyList(),12) { result = it }
        assertTrue(result.isEmpty())
    }
}
