package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.syncproviders.providers.AniListApi
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ensure AniList responses containing nulls can be parsed. */
class AniListParsingTest {
    private val listResponse = """
        {"data":{"MediaListCollection":{"lists":[
          {"status":"PLANNING","entries":[{
            "status":"PLANNING",
            "completedAt":{"year":null,"month":null,"day":null},
            "startedAt":{"year":null,"month":null,"day":null},
            "updatedAt":1761990000,"progress":0,"score":0,"private":false,
            "media":{"id":5114,"idMal":5114,"season":null,"seasonYear":null,"format":"TV",
              "episodes":null,"chapters":null,
              "title":{"english":null,"romaji":"Hagane no Renkinjutsushi: FULLMETAL ALCHEMIST"},
              "coverImage":{"extraLarge":null,"large":"https://example.com/l.jpg","medium":null},
              "synonyms":[],"nextAiringEpisode":null}
          }]},
          {"status":"COMPLETED","entries":[{
            "status":"COMPLETED",
            "completedAt":{"year":2024,"month":3,"day":22},
            "startedAt":{"year":2023,"month":9,"day":29},
            "updatedAt":1711100000,"progress":28,"score":95,"private":false,
            "media":{"id":154587,"idMal":52991,"season":"FALL","seasonYear":2023,"format":"TV",
              "episodes":28,"chapters":null,
              "title":{"english":"Frieren: Beyond Journey's End","romaji":"Sousou no Frieren"},
              "coverImage":{"extraLarge":"https://example.com/xl.jpg","large":null,"medium":null},
              "synonyms":["Frieren"],"nextAiringEpisode":null}
          }]}
        ]}}}
    """.trimIndent()

    @Test
    fun `parses library list with null dates and episodes`() {
        val lists = parseJson<AniListApi.FullAnilistList>(listResponse)
            .data!!.mediaListCollection.lists
        assertEquals(2, lists.size)

        val planning = lists[0].entries[0]
        assertNull(planning.completedAt?.year)
        assertNull(planning.media.episodes)
        val planningItem = planning.toLibraryItem()
        assertEquals("Hagane no Renkinjutsushi: FULLMETAL ALCHEMIST", planningItem.name)
        assertNull(planningItem.episodesTotal)

        val completed = lists[1].entries[0]
        assertEquals(2024, completed.completedAt?.year)
        assertEquals(2023, completed.startedAt?.year)
        assertEquals("Frieren: Beyond Journey's End", completed.toLibraryItem().name)
    }

    @Test
    fun `detects GraphQL errors in mutation response`() {
        val error = """{"errors":[{"message":"Variable \"${'$'}scoreRaw\" is not defined.","status":400}],"data":null}"""
        val errors = parseJson<AniListApi.GraphQLErrorRoot>(error).errors
        assertEquals("Variable \"\$scoreRaw\" is not defined.", errors?.single()?.message)

        val success = """{"data":{"SaveMediaListEntry":{"id":1,"status":"PLANNING","progress":0,"score":0}}}"""
        assertTrue(parseJson<AniListApi.GraphQLErrorRoot>(success).errors.isNullOrEmpty())
    }
}
