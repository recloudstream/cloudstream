package com.lagradost.cloudstream3.syncproviders.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.getKeys
import com.lagradost.cloudstream3.AcraApplication.Companion.openBrowser
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.syncproviders.AuthAPI
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.splitQuery
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.DataStore.toKotlinObject
import java.net.URL
import java.util.*

class AniListApi(index: Int) : AccountManager(index), SyncAPI {
    override var name = "AniList"
    override val key = "6871"
    override val redirectUrl = "anilistlogin"
    override val idPrefix = "anilist"
    override var mainUrl = "https://anilist.co"
    override val icon = R.drawable.ic_anilist_icon
    override val requiresLogin = false
    override val createAccountUrl = "$mainUrl/signup"

    override fun loginInfo(): AuthAPI.LoginInfo? {
        // context.getUser(true)?.
        getKey<AniListUser>(accountId, ANILIST_USER_KEY)?.let { user ->
            return AuthAPI.LoginInfo(
                profilePicture = user.picture,
                name = user.name,
                accountIndex = accountIndex
            )
        }
        return null
    }

    override fun logOut() {
        removeAccountKeys()
    }

    override fun authenticate() {
        val request = "https://anilist.co/api/v2/oauth/authorize?client_id=$key&response_type=token"
        openBrowser(request)
    }

    override suspend fun handleRedirect(url: String): Boolean {
        val sanitizer =
            splitQuery(URL(url.replace(appString, "https").replace("/#", "?"))) // FIX ERROR
        val token = sanitizer["access_token"]!!
        val expiresIn = sanitizer["expires_in"]!!

        val endTime = unixTime + expiresIn.toLong()

        switchToNewAccount()
        setKey(accountId, ANILIST_UNIXTIME_KEY, endTime)
        setKey(accountId, ANILIST_TOKEN_KEY, token)
        setKey(ANILIST_SHOULD_UPDATE_LIST, true)
        val user = getUser()
        return user != null
    }

    override fun getIdFromUrl(url: String): String {
        return url.removePrefix("$mainUrl/anime/").removeSuffix("/")
    }

    private fun getUrlFromId(id: Int): String {
        return "$mainUrl/anime/$id"
    }

    override suspend fun search(name: String): List<SyncAPI.SyncSearchResult>? {
        val data = searchShows(name) ?: return null
        return data.data?.Page?.media?.map {
            SyncAPI.SyncSearchResult(
                it.title.romaji ?: return null,
                this.name,
                it.id.toString(),
                getUrlFromId(it.id),
                it.bannerImage
            )
        }
    }

    override suspend fun getResult(id: String): SyncAPI.SyncResult {
        val internalId = (Regex("anilist\\.co/anime/(\\d*)").find(id)?.groupValues?.getOrNull(1)
            ?: id).toIntOrNull() ?: throw ErrorLoadingException("Invalid internalId")
        val season = getSeason(internalId).data.Media

        return SyncAPI.SyncResult(
            season.id.toString(),
            nextAiring = season.nextAiringEpisode?.let {
                NextAiring(
                    it.episode ?: return@let null,
                    (it.timeUntilAiring ?: return@let null) + unixTime
                )
            },
            title = season.title?.userPreferred,
            synonyms = season.synonyms,
            isAdult = season.isAdult,
            totalEpisodes = season.episodes,
            synopsis = season.description,
            actors = season.characters?.edges?.mapNotNull { edge ->
                val node = edge.node ?: return@mapNotNull null
                ActorData(
                    actor = Actor(
                        name = node.name?.userPreferred ?: node.name?.full ?: node.name?.native
                        ?: return@mapNotNull null,
                        image = node.image?.large ?: node.image?.medium
                    ),
                    role = when (edge.role) {
                        "MAIN" -> ActorRole.Main
                        "SUPPORTING" -> ActorRole.Supporting
                        "BACKGROUND" -> ActorRole.Background
                        else -> null
                    },
                    voiceActor = edge.voiceActors?.firstNotNullOfOrNull { staff ->
                        Actor(
                            name = staff.name?.userPreferred ?: staff.name?.full
                            ?: staff.name?.native
                            ?: return@mapNotNull null,
                            image = staff.image?.large ?: staff.image?.medium
                        )
                    }
                )
            },
            publicScore = season.averageScore?.times(100),
            recommendations = season.recommendations?.edges?.mapNotNull { rec ->
                val recMedia = rec.node.mediaRecommendation
                SyncAPI.SyncSearchResult(
                    name = recMedia.title?.userPreferred ?: return@mapNotNull null,
                    this.name,
                    recMedia.id?.toString() ?: return@mapNotNull null,
                    getUrlFromId(recMedia.id),
                    recMedia.coverImage?.large ?: recMedia.coverImage?.medium
                )
            },
            trailers = when (season.trailer?.site?.lowercase()?.trim()) {
                "youtube" -> listOf("https://www.youtube.com/watch?v=${season.trailer.id}")
                else -> null
            }
            //TODO REST
        )
    }

    override suspend fun getStatus(id: String): SyncAPI.SyncStatus? {
        val internalId = id.toIntOrNull() ?: return null
        val data = getDataAboutId(internalId) ?: return null

        return SyncAPI.SyncStatus(
            score = data.score,
            watchedEpisodes = data.progress,
            status = data.type?.value ?: return null,
            isFavorite = data.isFavourite,
            maxEpisodes = data.episodes,
        )
    }

    override suspend fun score(id: String, status: SyncAPI.SyncStatus): Boolean {
        return postDataAboutId(
            id.toIntOrNull() ?: return false,
            fromIntToAnimeStatus(status.status),
            status.score,
            status.watchedEpisodes
        )
    }

    companion object {
        private val aniListStatusString =
            arrayOf("CURRENT", "COMPLETED", "PAUSED", "DROPPED", "PLANNING", "REPEATING")

        const val ANILIST_UNIXTIME_KEY: String = "anilist_unixtime" // When token expires
        const val ANILIST_TOKEN_KEY: String = "anilist_token" // anilist token for api
        const val ANILIST_USER_KEY: String = "anilist_user" // user data like profile
        const val ANILIST_CACHED_LIST: String = "anilist_cached_list"
        const val ANILIST_SHOULD_UPDATE_LIST: String = "anilist_should_update_list"

        private fun fixName(name: String): String {
            return name.lowercase(Locale.ROOT).replace(" ", "")
                .replace("[^a-zA-Z0-9]".toRegex(), "")
        }

        private suspend fun searchShows(name: String): GetSearchRoot? {
            try {
                val query = """
                query (${"$"}id: Int, ${"$"}page: Int, ${"$"}search: String, ${"$"}type: MediaType) {
                    Page (page: ${"$"}page, perPage: 10) {
                        media (id: ${"$"}id, search: ${"$"}search, type: ${"$"}type) {
                            id
                            idMal
                            seasonYear
                            startDate { year month day }
                            title {
                                romaji
                            }
                            averageScore
                            meanScore
                            nextAiringEpisode {
                                timeUntilAiring
                                episode
                            }
                            trailer { id site thumbnail }
                            bannerImage
                            recommendations {
                                nodes {
                                    id
                                    mediaRecommendation {
                                        id
                                        title {
                                            english
                                            romaji
                                        }
                                        idMal
                                        coverImage { medium large }
                                        averageScore
                                    }
                                }
                            }
                            relations {
                                edges {
                                    id
                                    relationType(version: 2)
                                    node {
                                        format
                                        id
                                        idMal
                                        coverImage { medium large }
                                        averageScore
                                        title {
                                            english
                                            romaji
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                """
                val data =
                    mapOf(
                        "query" to query,
                        "variables" to
                                mapOf(
                                    "search" to name,
                                    "page" to 1,
                                    "type" to "ANIME"
                                ).toJson()
                    )

                val res = app.post(
                    "https://graphql.anilist.co/",
                    //headers = mapOf(),
                    data = data,//(if (vars == null) mapOf("query" to q) else mapOf("query" to q, "variables" to vars))
                    timeout = 5000 // REASONABLE TIMEOUT
                ).text.replace("\\", "")
                return res.toKotlinObject()
            } catch (e: Exception) {
                logError(e)
            }
            return null
        }

        // Should use https://gist.github.com/purplepinapples/5dc60f15f2837bf1cea71b089cfeaa0a
        suspend fun getShowId(malId: String?, name: String, year: Int?): GetSearchMedia? {
            // Strips these from the name
            val blackList = listOf(
                "TV Dubbed",
                "(Dub)",
                "Subbed",
                "(TV)",
                "(Uncensored)",
                "(Censored)",
                "(\\d+)" // year
            )
            val blackListRegex =
                Regex(
                    """ (${
                        blackList.joinToString(separator = "|").replace("(", "\\(")
                            .replace(")", "\\)")
                    })"""
                )
            //println("NAME $name NEW NAME ${name.replace(blackListRegex, "")}")
            val shows = searchShows(name.replace(blackListRegex, ""))

            shows?.data?.Page?.media?.find {
                malId ?: "NONE" == it.idMal.toString()
            }?.let { return it }

            val filtered =
                shows?.data?.Page?.media?.filter {
                    (
                            it.startDate.year ?: year.toString() == year.toString()
                                    || year == null
                            )
                }
            filtered?.forEach {
                it.title.romaji?.let { romaji ->
                    if (fixName(romaji) == fixName(name)) return it
                }
            }

            return filtered?.firstOrNull()
        }

        // Changing names of these will show up in UI
        enum class AniListStatusType(var value: Int) {
            Watching(0),
            Completed(1),
            Paused(2),
            Dropped(3),
            Planning(4),
            ReWatching(5),
            None(-1)
        }

        fun fromIntToAnimeStatus(inp: Int): AniListStatusType {//= AniListStatusType.values().first { it.value == inp }
            return when (inp) {
                -1 -> AniListStatusType.None
                0 -> AniListStatusType.Watching
                1 -> AniListStatusType.Completed
                2 -> AniListStatusType.Paused
                3 -> AniListStatusType.Dropped
                4 -> AniListStatusType.Planning
                5 -> AniListStatusType.ReWatching
                else -> AniListStatusType.None
            }
        }

        fun convertAnilistStringToStatus(string: String): AniListStatusType {
            return fromIntToAnimeStatus(aniListStatusString.indexOf(string))
        }

        private suspend fun getSeason(id: Int): SeasonResponse {
            val q = """
               query (${'$'}id: Int = $id) {
                   Media (id: ${'$'}id, type: ANIME) {
                       id
                       idMal
                       coverImage {
                           extraLarge
                           large
                           medium
                           color
                       }
                       title {
                           romaji
                           english
                           native
                           userPreferred
                       }
                       duration
                       episodes
                       genres
                       synonyms
                       averageScore
                       isAdult
                       description(asHtml: false)
                       characters(sort: ROLE page: 1 perPage: 20) {
                           edges {
                               role
                               voiceActors {
                                   name {
                                       userPreferred
                                       full
                                       native
                                   }
                                   age
                                   image {
                                       large
                                       medium
                                   }
                               }
                               node {
                                   name {
                                       userPreferred
                                       full
                                       native
                                   }
                                   age
                                   image {
                                       large
                                       medium
                                   }
                               }
                           }
                       }
                       trailer {
                           id
                           site
                           thumbnail
                       }
                       relations {
                           edges {
                                id
                                relationType(version: 2)
                                node {
                                     id
                                     coverImage {
                                         extraLarge
                                         large
                                         medium
                                         color
                                     }
                                }
                           }
                       }
                       recommendations {
                           edges {
                               node {
                                   mediaRecommendation {
                                       id
                                       coverImage {
                                           extraLarge
                                           large
                                           medium
                                           color
                                       }
                                       title {
                                           romaji
                                           english
                                           native
                                           userPreferred
                                       }
                                   }
                               }
                           }
                       }
                       nextAiringEpisode {
                           timeUntilAiring
                           episode
                       }
                       format
                   }
               }
        """
            val data = app.post(
                "https://graphql.anilist.co",
                data = mapOf("query" to q),
                cacheTime = 0,
            ).text

            return tryParseJson(data) ?: throw ErrorLoadingException("Error parsing $data")
        }
    }

    fun initGetUser() {
        if (getAuth() == null) return
        ioSafe {
            getUser()
        }
    }

    private fun checkToken(): Boolean {
        return unixTime > getKey(
            accountId,
            ANILIST_UNIXTIME_KEY, 0L
        )!!
    }

    private suspend fun getDataAboutId(id: Int): AniListTitleHolder? {
        val q =
            """query (${'$'}id: Int = $id) { # Define which variables will be used in the query (id)
                Media (id: ${'$'}id, type: ANIME) { # Insert our variables into the query arguments (id) (type: ANIME is hard-coded in the query)
                    id
                    episodes
                    isFavourite
                    mediaListEntry {
                        progress
                        status
                        score (format: POINT_10)
                    }
                    title {
                        english
                        romaji
                    }
                }
            }"""

        val data = postApi(q, true)
        val d = parseJson<GetDataRoot>(data ?: return null)

        val main = d.data?.Media
        if (main?.mediaListEntry != null) {
            return AniListTitleHolder(
                title = main.title,
                id = id,
                isFavourite = main.isFavourite,
                progress = main.mediaListEntry.progress,
                episodes = main.episodes,
                score = main.mediaListEntry.score,
                type = fromIntToAnimeStatus(aniListStatusString.indexOf(main.mediaListEntry.status)),
            )
        } else {
            return AniListTitleHolder(
                title = main?.title,
                id = id,
                isFavourite = main?.isFavourite,
                progress = 0,
                episodes = main?.episodes,
                score = 0,
                type = AniListStatusType.None,
            )
        }

    }

    private fun getAuth(): String? {
        return getKey(
            accountId,
            ANILIST_TOKEN_KEY
        )
    }

    private suspend fun postApi(q: String, cache: Boolean = false): String? {
        return if (!checkToken()) {
            app.post(
                "https://graphql.anilist.co/",
                headers = mapOf(
                    "Authorization" to "Bearer " + (getAuth() ?: return null),
                    if (cache) "Cache-Control" to "max-stale=$maxStale" else "Cache-Control" to "no-cache"
                ),
                cacheTime = 0,
                data = mapOf("query" to q),//(if (vars == null) mapOf("query" to q) else mapOf("query" to q, "variables" to vars))
                timeout = 5 // REASONABLE TIMEOUT
            ).text.replace("\\/", "/")
        } else {
            null
        }
    }

    data class MediaRecommendation(
        @JsonProperty("id") val id: Int,
        @JsonProperty("title") val title: Title?,
        @JsonProperty("idMal") val idMal: Int?,
        @JsonProperty("coverImage") val coverImage: CoverImage?,
        @JsonProperty("averageScore") val averageScore: Int?
    )

    data class FullAnilistList(
        @JsonProperty("data") val data: Data?
    )

    data class CompletedAt(
        @JsonProperty("year") val year: Int,
        @JsonProperty("month") val month: Int,
        @JsonProperty("day") val day: Int
    )

    data class StartedAt(
        @JsonProperty("year") val year: String?,
        @JsonProperty("month") val month: String?,
        @JsonProperty("day") val day: String?
    )

    data class Title(
        @JsonProperty("english") val english: String?,
        @JsonProperty("romaji") val romaji: String?
    )

    data class CoverImage(
        @JsonProperty("medium") val medium: String?,
        @JsonProperty("large") val large: String?
    )

    data class Media(
        @JsonProperty("id") val id: Int,
        @JsonProperty("idMal") val idMal: Int?,
        @JsonProperty("season") val season: String?,
        @JsonProperty("seasonYear") val seasonYear: Int,
        @JsonProperty("format") val format: String?,
        //@JsonProperty("source") val source: String,
        @JsonProperty("episodes") val episodes: Int,
        @JsonProperty("title") val title: Title,
        //@JsonProperty("description") val description: String,
        @JsonProperty("coverImage") val coverImage: CoverImage,
        @JsonProperty("synonyms") val synonyms: List<String>,
        @JsonProperty("nextAiringEpisode") val nextAiringEpisode: SeasonNextAiringEpisode?,
    )

    data class Entries(
        @JsonProperty("status") val status: String?,
        @JsonProperty("completedAt") val completedAt: CompletedAt,
        @JsonProperty("startedAt") val startedAt: StartedAt,
        @JsonProperty("updatedAt") val updatedAt: Int,
        @JsonProperty("progress") val progress: Int,
        @JsonProperty("score") val score: Int,
        @JsonProperty("private") val private: Boolean,
        @JsonProperty("media") val media: Media
    )

    data class Lists(
        @JsonProperty("status") val status: String?,
        @JsonProperty("entries") val entries: List<Entries>
    )

    data class MediaListCollection(
        @JsonProperty("lists") val lists: List<Lists>
    )

    data class Data(
        @JsonProperty("MediaListCollection") val MediaListCollection: MediaListCollection
    )

    fun getAnilistListCached(): Array<Lists>? {
        return getKey(ANILIST_CACHED_LIST) as? Array<Lists>
    }

    suspend fun getAnilistAnimeListSmart(): Array<Lists>? {
        if (getAuth() == null) return null

        if (checkToken()) return null
        return if (getKey(ANILIST_SHOULD_UPDATE_LIST, true) == true) {
            val list = getFullAnilistList()?.data?.MediaListCollection?.lists?.toTypedArray()
            if (list != null) {
                setKey(ANILIST_CACHED_LIST, list)
                setKey(ANILIST_SHOULD_UPDATE_LIST, false)
            }
            list
        } else {
            getAnilistListCached()
        }
    }

    private suspend fun getFullAnilistList(): FullAnilistList? {
        var userID: Int? = null
        /** WARNING ASSUMES ONE USER! **/
        getKeys(ANILIST_USER_KEY)?.forEach { key ->
            getKey<AniListUser>(key, null)?.let {
                userID = it.id
            }
        }

        val fixedUserID = userID ?: return null
        val mediaType = "ANIME"

        val query = """
                query (${'$'}userID: Int = $fixedUserID, ${'$'}MEDIA: MediaType = $mediaType) {
                    MediaListCollection (userId: ${'$'}userID, type: ${'$'}MEDIA) { 
                        lists {
                            status
                            entries
                            {
                                status
                                completedAt { year month day }
                                startedAt { year month day }
                                updatedAt
                                progress
                                score
                                private
                                media
                                {
                                    id
                                    idMal
                                    season
                                    seasonYear
                                    format
                                    episodes
                                    chapters
                                    title
                                    {
                                        english
                                        romaji
                                    }
                                    coverImage { medium }
                                    synonyms
                                    nextAiringEpisode {
                                        timeUntilAiring
                                        episode
                                    }
                                }
                            }
                        }
                    }
                    }
            """
        val text = postApi(query)
        return text?.toKotlinObject()
    }

    suspend fun toggleLike(id: Int): Boolean {
        val q = """mutation (${'$'}animeId: Int = $id) {
				ToggleFavourite (animeId: ${'$'}animeId) {
					anime {
						nodes {
							id
							title {
								romaji
							}
						}
					}
				}
			}"""
        val data = postApi(q)
        return data != ""
    }

    private suspend fun postDataAboutId(
        id: Int,
        type: AniListStatusType,
        score: Int?,
        progress: Int?
    ): Boolean {
        val q =
            """mutation (${'$'}id: Int = $id, ${'$'}status: MediaListStatus = ${
                aniListStatusString[maxOf(
                    0,
                    type.value
                )]
            }, ${if (score != null) "${'$'}scoreRaw: Int = ${score * 10}" else ""} , ${if (progress != null) "${'$'}progress: Int = $progress" else ""}) {
                SaveMediaListEntry (mediaId: ${'$'}id, status: ${'$'}status, scoreRaw: ${'$'}scoreRaw, progress: ${'$'}progress) {
                    id
                    status
                    progress
                    score
                }
                }"""
        val data = postApi(q)
        return data != ""
    }

    private suspend fun getUser(setSettings: Boolean = true): AniListUser? {
        val q = """
				{
  					Viewer {
    					id
    					name
						avatar {
							large
						}
                        favourites {
                            anime {
                                nodes {
                                    id
                                }
                            }
                        }
  					}
				}"""
        val data = postApi(q)
        if (data.isNullOrBlank()) return null
        val userData = parseJson<AniListRoot>(data)
        val u = userData.data?.Viewer
        val user = AniListUser(
            u?.id,
            u?.name,
            u?.avatar?.large,
        )
        if (setSettings) {
            setKey(accountId, ANILIST_USER_KEY, user)
            registerAccount()
        }
        /* // TODO FIX FAVS
        for(i in u.favourites.anime.nodes) {
            println("FFAV:" + i.id)
        }*/
        return user
    }

    suspend fun getAllSeasons(id: Int): List<SeasonResponse?> {
        val seasons = mutableListOf<SeasonResponse?>()
        suspend fun getSeasonRecursive(id: Int) {
            val season = getSeason(id)
            seasons.add(season)
            if (season.data.Media.format?.startsWith("TV") == true) {
                season.data.Media.relations?.edges?.forEach {
                    if (it.node?.format != null) {
                        if (it.relationType == "SEQUEL" && it.node.format.startsWith("TV")) {
                            getSeasonRecursive(it.node.id)
                            return@forEach
                        }
                    }
                }
            }
        }
        getSeasonRecursive(id)
        return seasons.toList()
    }

    data class SeasonResponse(
        @JsonProperty("data") val data: SeasonData,
    )

    data class SeasonData(
        @JsonProperty("Media") val Media: SeasonMedia,
    )

    data class SeasonMedia(
        @JsonProperty("id") val id: Int?,
        @JsonProperty("title") val title: MediaTitle?,
        @JsonProperty("idMal") val idMal: Int?,
        @JsonProperty("format") val format: String?,
        @JsonProperty("nextAiringEpisode") val nextAiringEpisode: SeasonNextAiringEpisode?,
        @JsonProperty("relations") val relations: SeasonEdges?,
        @JsonProperty("coverImage") val coverImage: MediaCoverImage?,
        @JsonProperty("duration") val duration: Int?,
        @JsonProperty("episodes") val episodes: Int?,
        @JsonProperty("genres") val genres: List<String>?,
        @JsonProperty("synonyms") val synonyms: List<String>?,
        @JsonProperty("averageScore") val averageScore: Int?,
        @JsonProperty("isAdult") val isAdult: Boolean?,
        @JsonProperty("trailer") val trailer: MediaTrailer?,
        @JsonProperty("description") val description: String?,
        @JsonProperty("characters") val characters: CharacterConnection?,
        @JsonProperty("recommendations") val recommendations: RecommendationConnection?,
    )

    data class RecommendationConnection(
        @JsonProperty("edges") val edges: List<RecommendationEdge> = emptyList(),
        @JsonProperty("nodes") val nodes: List<Recommendation> = emptyList(),
        //@JsonProperty("pageInfo") val pageInfo: PageInfo,
    )

    data class RecommendationEdge(
        //@JsonProperty("rating") val rating: Int,
        @JsonProperty("node") val node: Recommendation,
    )

    data class Recommendation(
        @JsonProperty("mediaRecommendation") val mediaRecommendation: SeasonMedia,
    )

    data class CharacterName(
        @JsonProperty("name") val first: String?,
        @JsonProperty("middle") val middle: String?,
        @JsonProperty("last") val last: String?,
        @JsonProperty("full") val full: String?,
        @JsonProperty("native") val native: String?,
        @JsonProperty("alternative") val alternative: List<String>?,
        @JsonProperty("alternativeSpoiler") val alternativeSpoiler: List<String>?,
        @JsonProperty("userPreferred") val userPreferred: String?,
    )

    data class CharacterImage(
        @JsonProperty("large") val large: String?,
        @JsonProperty("medium") val medium: String?,
    )

    data class Character(
        @JsonProperty("name") val name: CharacterName?,
        @JsonProperty("age") val age: String?,
        @JsonProperty("image") val image: CharacterImage?,
    )

    data class CharacterEdge(
        @JsonProperty("id") val id: Int?,
        /**
        MAIN
        A primary character role in the media

        SUPPORTING
        A supporting character role in the media

        BACKGROUND
        A background character in the media
         */
        @JsonProperty("role") val role: String?,
        @JsonProperty("name") val name: String?,
        @JsonProperty("voiceActors") val voiceActors: List<Staff>?,
        @JsonProperty("favouriteOrder") val favouriteOrder: Int?,
        @JsonProperty("media") val media: List<SeasonMedia>?,
        @JsonProperty("node") val node: Character?,
    )

    data class StaffImage(
        @JsonProperty("large") val large: String?,
        @JsonProperty("medium") val medium: String?,
    )

    data class StaffName(
        @JsonProperty("name") val first: String?,
        @JsonProperty("middle") val middle: String?,
        @JsonProperty("last") val last: String?,
        @JsonProperty("full") val full: String?,
        @JsonProperty("native") val native: String?,
        @JsonProperty("alternative") val alternative: List<String>?,
        @JsonProperty("userPreferred") val userPreferred: String?,
    )

    data class Staff(
        @JsonProperty("image") val image: StaffImage?,
        @JsonProperty("name") val name: StaffName?,
        @JsonProperty("age") val age: Int?,
    )

    data class CharacterConnection(
        @JsonProperty("edges") val edges: List<CharacterEdge>?,
        @JsonProperty("nodes") val nodes: List<Character>?,
        //@JsonProperty("pageInfo")  pageInfo: PageInfo
    )

    data class MediaTrailer(
        @JsonProperty("id") val id: String?,
        @JsonProperty("site") val site: String?,
        @JsonProperty("thumbnail") val thumbnail: String?,
    )

    data class MediaCoverImage(
        @JsonProperty("extraLarge") val extraLarge: String?,
        @JsonProperty("large") val large: String?,
        @JsonProperty("medium") val medium: String?,
        @JsonProperty("color") val color: String?,
    )

    data class SeasonNextAiringEpisode(
        @JsonProperty("episode") val episode: Int?,
        @JsonProperty("timeUntilAiring") val timeUntilAiring: Int?,
    )

    data class SeasonEdges(
        @JsonProperty("edges") val edges: List<SeasonEdge>?,
    )

    data class SeasonEdge(
        @JsonProperty("id") val id: Int?,
        @JsonProperty("relationType") val relationType: String?,
        @JsonProperty("node") val node: SeasonNode?,
    )

    data class AniListFavoritesMediaConnection(
        @JsonProperty("nodes") val nodes: List<LikeNode>,
    )

    data class AniListFavourites(
        @JsonProperty("anime") val anime: AniListFavoritesMediaConnection,
    )

    data class MediaTitle(
        @JsonProperty("romaji") val romaji: String?,
        @JsonProperty("english") val english: String?,
        @JsonProperty("native") val native: String?,
        @JsonProperty("userPreferred") val userPreferred: String?,
    )

    data class SeasonNode(
        @JsonProperty("id") val id: Int,
        @JsonProperty("format") val format: String?,
        @JsonProperty("title") val title: Title?,
        @JsonProperty("idMal") val idMal: Int?,
        @JsonProperty("coverImage") val coverImage: CoverImage?,
        @JsonProperty("averageScore") val averageScore: Int?
//        @JsonProperty("nextAiringEpisode") val nextAiringEpisode: SeasonNextAiringEpisode?,
    )

    data class AniListAvatar(
        @JsonProperty("large") val large: String?,
    )

    data class AniListViewer(
        @JsonProperty("id") val id: Int?,
        @JsonProperty("name") val name: String?,
        @JsonProperty("avatar") val avatar: AniListAvatar?,
        @JsonProperty("favourites") val favourites: AniListFavourites?,
    )

    data class AniListData(
        @JsonProperty("Viewer") val Viewer: AniListViewer?,
    )

    data class AniListRoot(
        @JsonProperty("data") val data: AniListData?,
    )

    data class AniListUser(
        @JsonProperty("id") val id: Int?,
        @JsonProperty("name") val name: String?,
        @JsonProperty("picture") val picture: String?,
    )

    data class LikeNode(
        @JsonProperty("id") val id: Int?,
        //@JsonProperty("idMal") public int idMal;
    )

    data class LikePageInfo(
        @JsonProperty("total") val total: Int?,
        @JsonProperty("currentPage") val currentPage: Int?,
        @JsonProperty("lastPage") val lastPage: Int?,
        @JsonProperty("perPage") val perPage: Int?,
        @JsonProperty("hasNextPage") val hasNextPage: Boolean?,
    )

    data class LikeAnime(
        @JsonProperty("nodes") val nodes: List<LikeNode>?,
        @JsonProperty("pageInfo") val pageInfo: LikePageInfo?,
    )

    data class LikeFavourites(
        @JsonProperty("anime") val anime: LikeAnime?,
    )

    data class LikeViewer(
        @JsonProperty("favourites") val favourites: LikeFavourites?,
    )

    data class LikeData(
        @JsonProperty("Viewer") val Viewer: LikeViewer?,
    )

    data class LikeRoot(
        @JsonProperty("data") val data: LikeData?,
    )

    data class AniListTitleHolder(
        @JsonProperty("title") val title: Title?,
        @JsonProperty("isFavourite") val isFavourite: Boolean?,
        @JsonProperty("id") val id: Int?,
        @JsonProperty("progress") val progress: Int?,
        @JsonProperty("episodes") val episodes: Int?,
        @JsonProperty("score") val score: Int?,
        @JsonProperty("type") val type: AniListStatusType?,
    )

    data class GetDataMediaListEntry(
        @JsonProperty("progress") val progress: Int?,
        @JsonProperty("status") val status: String?,
        @JsonProperty("score") val score: Int?,
    )

    data class Nodes(
        @JsonProperty("id") val id: Int?,
        @JsonProperty("mediaRecommendation") val mediaRecommendation: MediaRecommendation?
    )

    data class GetDataMedia(
        @JsonProperty("isFavourite") val isFavourite: Boolean?,
        @JsonProperty("episodes") val episodes: Int?,
        @JsonProperty("title") val title: Title?,
        @JsonProperty("mediaListEntry") val mediaListEntry: GetDataMediaListEntry?
    )

    data class Recommendations(
        @JsonProperty("nodes") val nodes: List<Nodes>?
    )

    data class GetDataData(
        @JsonProperty("Media") val Media: GetDataMedia?,
    )

    data class GetDataRoot(
        @JsonProperty("data") val data: GetDataData?,
    )

    data class GetSearchTitle(
        @JsonProperty("romaji") val romaji: String?,
    )

    data class TrailerObject(
        @JsonProperty("id") val id: String?,
        @JsonProperty("thumbnail") val thumbnail: String?,
        @JsonProperty("site") val site: String?,
    )

    data class GetSearchMedia(
        @JsonProperty("id") val id: Int,
        @JsonProperty("idMal") val idMal: Int?,
        @JsonProperty("seasonYear") val seasonYear: Int,
        @JsonProperty("title") val title: GetSearchTitle,
        @JsonProperty("startDate") val startDate: StartedAt,
        @JsonProperty("averageScore") val averageScore: Int?,
        @JsonProperty("meanScore") val meanScore: Int?,
        @JsonProperty("bannerImage") val bannerImage: String?,
        @JsonProperty("trailer") val trailer: TrailerObject?,
        @JsonProperty("nextAiringEpisode") val nextAiringEpisode: SeasonNextAiringEpisode?,
        @JsonProperty("recommendations") val recommendations: Recommendations?,
        @JsonProperty("relations") val relations: SeasonEdges?
    )

    data class GetSearchPage(
        @JsonProperty("Page") val Page: GetSearchData?,
    )

    data class GetSearchData(
        @JsonProperty("media") val media: List<GetSearchMedia>?,
    )

    data class GetSearchRoot(
        @JsonProperty("data") val data: GetSearchPage?,
    )
}