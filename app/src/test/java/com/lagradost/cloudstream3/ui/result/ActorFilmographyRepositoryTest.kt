package com.lagradost.cloudstream3.ui.result

import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ActorFilmographyRepositoryTest {
    @Test
    fun `profile image selects the matching person despite image size and query parameters`() = runBlocking {
        val calls = mutableListOf<String>()
        val repository = ActorFilmographyRepository { path, params ->
            calls += path
            if (path == "/search/person") {
                assertEquals("Alex Smith", params["query"])
                assertEquals("false", params["include_adult"])
                """{"results":[
                    {"id":1,"name":"Alex Smith","profile_path":"/other.jpg"},
                    {"id":2,"name":"Alexander Smith","profile_path":"/match.jpg"}
                ]}"""
            } else {
                """{"cast":[]}"""
            }
        }

        repository.load(Actor(" Alex Smith ", "https://image.tmdb.org/t/p/w185/match.jpg?v=1"))
        assertEquals(listOf("/search/person", "/person/2/combined_credits"), calls)
    }

    @Test
    fun `exact name is preferred to a higher ranked partial name`() = runBlocking {
        val repository = ActorFilmographyRepository { path, _ ->
            when (path) {
                "/search/person" -> """{"results":[
                    {"id":3,"name":"Alex Smith Jr"},
                    {"id":4,"name":"ALEX SMITH"}
                ]}"""
                "/person/4/combined_credits" -> """{"cast":[]}"""
                else -> error("Selected the wrong person: $path")
            }
        }
        assertTrue(repository.load(Actor("Alex Smith")).isEmpty())
    }

    @Test
    fun `credits include movies and series while excluding duplicates and unusable entries`() = runBlocking {
        val repository = ActorFilmographyRepository { path, _ ->
            if (path == "/search/person") {
                """{"results":[{"id":2,"name":"Actor"}]}"""
            } else {
                """{"cast":[
                    {"id":10,"media_type":"movie","title":"Movie","release_date":"2020-03-01",
                     "poster_path":"/poster.jpg","vote_average":8.0,"popularity":4},
                    {"id":10,"media_type":"movie","title":"Movie","character":"Second role"},
                    {"id":10,"media_type":"tv","name":"Series","first_air_date":"2024-04-01","popularity":4},
                    {"id":11,"media_type":"movie","title":" ","original_title":"Original","popularity":1},
                    {"id":12,"media_type":"movie","title":"Adult","adult":true,"popularity":100},
                    {"id":13,"media_type":"person","name":"Person"},
                    {"id":14,"media_type":"movie","title":""},
                    {"media_type":"movie","title":"Missing ID"},
                    {"id":0,"media_type":"tv","name":"Invalid ID"}
                ],"crew":[{"id":15,"media_type":"movie","title":"Crew only"}]}"""
            }
        }

        val credits = repository.load(Actor("Actor"))
        assertEquals(listOf("Series", "Movie", "Original"), credits.map { it.name })
        assertEquals(2024, (credits[0] as TvSeriesSearchResponse).year)
        assertEquals(2020, (credits[1] as MovieSearchResponse).year)
        assertNotEquals(credits[0].id, credits[1].id)
        assertEquals("https://www.themoviedb.org/tv/10", credits[0].url)
        assertEquals("https://www.themoviedb.org/movie/10", credits[1].url)
        assertEquals("https://image.tmdb.org/t/p/w500/poster.jpg", credits[1].posterUrl)
        assertNull(credits[2].posterUrl)
    }

    @Test
    fun `empty or invalid people do not request credits`() = runBlocking {
        for (response in listOf("{}", "{\"results\":null}", "{\"results\":[]}", "{\"results\":[{\"name\":\"Actor\"}]}")) {
            val repository = ActorFilmographyRepository { path, _ ->
                assertEquals("/search/person", path)
                response
            }
            assertTrue(repository.load(Actor("Actor")).isEmpty())
        }
    }

    @Test
    fun `empty actor names do not request TMDB`() = runBlocking {
        val repository = ActorFilmographyRepository { _, _ -> error("Unexpected request") }
        assertTrue(repository.load(Actor("  ")).isEmpty())
    }

    @Test(expected = IOException::class)
    fun `network failures reach the retry state instead of looking like empty credits`() = runBlocking {
        ActorFilmographyRepository { _, _ -> throw IOException("Offline") }.load(Actor("Actor"))
        Unit
    }

    @Test(expected = CancellationException::class)
    fun `request cancellation propagates when the sheet is dismissed`() = runBlocking {
        ActorFilmographyRepository { _, _ -> throw CancellationException("Dismissed") }.load(Actor("Actor"))
        Unit
    }
}
