package com.lagradost.cloudstream3.syncproviders.providers

import androidx.annotation.StringRes
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.ActorRole
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.NextAiring
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.syncproviders.AuthData
import com.lagradost.cloudstream3.syncproviders.AuthLoginPage
import com.lagradost.cloudstream3.syncproviders.AuthToken
import com.lagradost.cloudstream3.syncproviders.AuthUser
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.ui.SyncWatchType
import com.lagradost.cloudstream3.ui.library.ListSorting
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.DataStoreHelper.toYear
import com.lagradost.cloudstream3.utils.txt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URLEncoder
import java.util.Locale

class AniListApi : SyncAPI() {
    override var name = "AniList"
    override val idPrefix = "anilist"

    private val key = BuildConfig.ANILIST_KEY
    override val redirectUrlIdentifier = "anilistlogin"
    override var requireLibraryRefresh = true
    override val hasOAuth2 = true
    override var mainUrl = "https://anilist.co"
    override val icon = R.drawable.ic_anilist_icon
    override val createAccountUrl = "$mainUrl/signup"
    override val syncIdName = SyncIdName.Anilist

    override fun loginRequest(): AuthLoginPage? =
        AuthLoginPage("https://anilist.co/api/v2/oauth/authorize?client_id=$key&response_type=token")

    override suspend fun login(redirectUrl: String, payload: String?): AuthToken? {
        val sanitizer = splitRedirectUrl(redirectUrl)
        val token = AuthToken(
            accessToken = sanitizer["access_token"]
                ?: throw ErrorLoadingException("No access token"),
            // refreshToken = sanitizer["refresh_token"],
            accessTokenLifetime = APIHolder.unixTime + sanitizer["expires_in"]!!.toLong(),
        )
        return token
    }

    // https://docs.anilist.co/guide/auth/
    override suspend fun refreshToken(token: AuthToken): AuthToken? {
        // AniList access tokens are long-lived. They will remain valid for 1 year from the time they are issued.
        // Refresh tokens are not currently supported. Once a token expires, you will need to re-authenticate your users.
        return super.refreshToken(token)
    }

    override suspend fun user(token: AuthToken?): AuthUser? {
        val user = getUser(token ?: return null)
            ?: throw ErrorLoadingException("Unable to fetch user data")

        return AuthUser(
            id = user.id,
            name = user.name,
            profilePicture = user.picture,
        )
    }

    override fun urlToId(url: String): String? =
        url.removePrefix("$mainUrl/anime/").removeSuffix("/")

    private fun getUrlFromId(id: Int): String {
        return "$mainUrl/anime/$id"
    }

    override suspend fun search(auth: AuthData?, query: String): List<SyncAPI.SyncSearchResult>? {
        val data = searchShows(query) ?: return null
        return data.data?.page?.media?.map {
            SyncAPI.SyncSearchResult(
                it.title.romaji ?: return null,
                this.name,
                it.id.toString(),
                getUrlFromId(it.id),
                it.bannerImage
            )
        }
    }

    override suspend fun load(auth: AuthData?, id: String): SyncAPI.SyncResult? {
        val internalId = (Regex("anilist\\.co/anime/(\\d*)").find(id)?.groupValues?.getOrNull(1)
            ?: id).toIntOrNull() ?: throw ErrorLoadingException("Invalid internalId")
        val season = getSeason(internalId).data.media
        return SyncAPI.SyncResult(
            season.id.toString(),
            nextAiring = season.nextAiringEpisode?.let {
                NextAiring(
                    it.episode ?: return@let null,
                    (it.timeUntilAiring ?: return@let null) + APIHolder.unixTime
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
            publicScore = Score.from100(season.averageScore),
            recommendations = season.recommendations?.edges?.mapNotNull { rec ->
                val recMedia = rec.node.mediaRecommendation
                SyncAPI.SyncSearchResult(
                    name = recMedia?.title?.userPreferred ?: return@mapNotNull null,
                    this.name,
                    recMedia.id?.toString() ?: return@mapNotNull null,
                    getUrlFromId(recMedia.id),
                    recMedia.coverImage?.extraLarge ?: recMedia.coverImage?.large
                    ?: recMedia.coverImage?.medium
                )
            },
            trailers = when (season.trailer?.site?.lowercase()?.trim()) {
                "youtube" -> listOf("https://www.youtube.com/watch?v=${season.trailer.id}")
                else -> null
            }
            // TODO REST
        )
    }

    override suspend fun status(auth: AuthData?, id: String): SyncAPI.AbstractSyncStatus? {
        val internalId = id.toIntOrNull() ?: return null
        val data = getDataAboutId(auth ?: return null, internalId) ?: return null
        return SyncAPI.SyncStatus(
            score = Score.from100(data.score),
            watchedEpisodes = data.progress,
            status = SyncWatchType.fromInternalId(data.type?.value ?: return null),
            isFavorite = data.isFavourite,
            maxEpisodes = data.episodes,
        )
    }

    override suspend fun updateStatus(
        auth: AuthData?,
        id: String,
        newStatus: AbstractSyncStatus
    ): Boolean {
        return postDataAboutId(
            auth ?: return false,
            id.toIntOrNull() ?: return false,
            fromIntToAnimeStatus(newStatus.status.internalId),
            newStatus.score,
            newStatus.watchedEpisodes
        )
    }

    companion object {
        const val MAX_STALE = 60 * 10
        private val aniListStatusString =
            arrayOf("CURRENT", "COMPLETED", "PAUSED", "DROPPED", "PLANNING", "REPEATING")

        const val ANILIST_CACHED_LIST: String = "anilist_cached_list"

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
                                        coverImage { medium large extraLarge }
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
                                        coverImage { medium large extraLarge }
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
                        "variables" to Variables(
                            search = name,
                            page = 1,
                            type = "ANIME",
                        ).toJson()
                    )

                val res = app.post(
                    "https://graphql.anilist.co/",
                    // headers = mapOf(),
                    data = data, // (if (vars == null) mapOf("query" to q) else mapOf("query" to q, "variables" to vars))
                    timeout = 5000 // REASONABLE TIMEOUT
                ).text.replace("\\", "")
                return parseJson<GetSearchRoot>(res)
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
            // println("NAME $name NEW NAME ${name.replace(blackListRegex, "")}")
            val shows = searchShows(name.replace(blackListRegex, ""))

            shows?.data?.page?.media?.find {
                (malId ?: "NONE") == it.idMal.toString()
            }?.let { return it }

            val filtered =
                shows?.data?.page?.media?.filter {
                    (((it.startDate.year ?: year.toString()) == year.toString()
                            || year == null))
                }
            filtered?.forEach {
                it.title.romaji?.let { romaji ->
                    if (fixName(romaji) == fixName(name)) return it
                }
            }

            return filtered?.firstOrNull()
        }

        // Changing names of these will show up in UI
        enum class AniListStatusType(var value: Int, @StringRes val stringRes: Int) {
            Watching(0, R.string.type_watching),
            Completed(1, R.string.type_completed),
            Paused(2, R.string.type_on_hold),
            Dropped(3, R.string.type_dropped),
            Planning(4, R.string.type_plan_to_watch),
            ReWatching(5, R.string.type_re_watching),
            None(-1, R.string.none)
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

        fun convertAniListStringToStatus(string: String): AniListStatusType {
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

            return tryParseJson<SeasonResponse>(data) ?: throw ErrorLoadingException("Error parsing $data")
        }
    }

    private suspend fun getDataAboutId(auth: AuthData, id: Int): AniListTitleHolder? {
        val q =
            """query (${'$'}id: Int = $id) { # Define which variables will be used in the query (id)
                Media (id: ${'$'}id, type: ANIME) { # Insert our variables into the query arguments (id) (type: ANIME is hard-coded in the query)
                    id
                    episodes
                    isFavourite
                    mediaListEntry {
                        progress
                        status
                        score (format: POINT_100)
                    }
                    title {
                        english
                        romaji
                    }
                }
            }"""

        val data = postApi(auth.token, q, true)
        val d = parseJson<GetDataRoot>(data ?: return null)

        val main = d.data?.media
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

    private suspend fun postApi(token: AuthToken, q: String, cache: Boolean = false): String? {
        return app.post(
            "https://graphql.anilist.co/",
            headers = mapOf(
                "Authorization" to "Bearer ${token.accessToken ?: return null}",
                if (cache) "Cache-Control" to "max-stale=$MAX_STALE" else "Cache-Control" to "no-cache"
            ),
            cacheTime = 0,
            data = mapOf(
                "query" to URLEncoder.encode(
                    q,
                    "UTF-8"
                )
            ), // (if (vars == null) mapOf("query" to q) else mapOf("query" to q, "variables" to vars))
            timeout = 5 // REASONABLE TIMEOUT
        ).text.replace("\\/", "/")
    }

    @Serializable
    data class Variables(
        @JsonProperty("search") @SerialName("search") val search: String,
        @JsonProperty("page") @SerialName("page") val page: Int,
        @JsonProperty("type") @SerialName("type") val type: String,
    )

    @Serializable
    data class MediaRecommendation(
        @JsonProperty("id") @SerialName("id") val id: Int,
        @JsonProperty("title") @SerialName("title") val title: Title?,
        @JsonProperty("idMal") @SerialName("idMal") val idMal: Int?,
        @JsonProperty("coverImage") @SerialName("coverImage") val coverImage: CoverImage?,
        @JsonProperty("averageScore") @SerialName("averageScore") val averageScore: Int?,
    )

    @Serializable
    data class FullAnilistList(
        @JsonProperty("data") @SerialName("data") val data: Data?,
    )

    @Serializable
    data class CompletedAt(
        @JsonProperty("year") @SerialName("year") val year: Int,
        @JsonProperty("month") @SerialName("month") val month: Int,
        @JsonProperty("day") @SerialName("day") val day: Int,
    )

    @Serializable
    data class StartedAt(
        @JsonProperty("year") @SerialName("year") val year: String?,
        @JsonProperty("month") @SerialName("month") val month: String?,
        @JsonProperty("day") @SerialName("day") val day: String?,
    )

    @Serializable
    data class Title(
        @JsonProperty("english") @SerialName("english") val english: String?,
        @JsonProperty("romaji") @SerialName("romaji") val romaji: String?,
    )

    @Serializable
    data class CoverImage(
        @JsonProperty("medium") @SerialName("medium") val medium: String?,
        @JsonProperty("large") @SerialName("large") val large: String?,
        @JsonProperty("extraLarge") @SerialName("extraLarge") val extraLarge: String?,
    )

    @Serializable
    data class Media(
        @JsonProperty("id") @SerialName("id") val id: Int,
        @JsonProperty("idMal") @SerialName("idMal") val idMal: Int?,
        @JsonProperty("season") @SerialName("season") val season: String?,
        @JsonProperty("seasonYear") @SerialName("seasonYear") val seasonYear: Int,
        @JsonProperty("format") @SerialName("format") val format: String?,
        @JsonProperty("episodes") @SerialName("episodes") val episodes: Int,
        @JsonProperty("title") @SerialName("title") val title: Title,
        @JsonProperty("description") @SerialName("description") val description: String?,
        @JsonProperty("coverImage") @SerialName("coverImage") val coverImage: CoverImage,
        @JsonProperty("synonyms") @SerialName("synonyms") val synonyms: List<String>,
        @JsonProperty("nextAiringEpisode") @SerialName("nextAiringEpisode") val nextAiringEpisode: SeasonNextAiringEpisode?,
    )

    @Serializable
    data class Entries(
        @JsonProperty("status") @SerialName("status") val status: String?,
        @JsonProperty("completedAt") @SerialName("completedAt") val completedAt: CompletedAt,
        @JsonProperty("startedAt") @SerialName("startedAt") val startedAt: StartedAt,
        @JsonProperty("updatedAt") @SerialName("updatedAt") val updatedAt: Int,
        @JsonProperty("progress") @SerialName("progress") val progress: Int,
        @JsonProperty("score") @SerialName("score") val score: Int,
        @JsonProperty("private") @SerialName("private") val private: Boolean,
        @JsonProperty("media") @SerialName("media") val media: Media,
    ) {
        fun toLibraryItem(): SyncAPI.LibraryItem {
            return SyncAPI.LibraryItem(
                // English title first
                this.media.title.english ?: this.media.title.romaji
                ?: this.media.synonyms.firstOrNull()
                ?: "",
                "https://anilist.co/anime/${this.media.id}/",
                this.media.id.toString(),
                this.progress,
                this.media.episodes,
                Score.from100(this.score),
                this.updatedAt.toLong(),
                "AniList",
                TvType.Anime,
                this.media.coverImage.extraLarge ?: this.media.coverImage.large
                ?: this.media.coverImage.medium,
                null,
                null,
                this.media.seasonYear.toYear(),
                null,
                plot = this.media.description,
            )
        }
    }

    @Serializable
    data class Lists(
        @JsonProperty("status") @SerialName("status") val status: String?,
        @JsonProperty("entries") @SerialName("entries") val entries: List<Entries>,
    )

    @Serializable
    data class MediaListCollection(
        @JsonProperty("lists") @SerialName("lists") val lists: List<Lists>,
    )

    @Serializable
    data class Data(
        @JsonProperty("MediaListCollection") @SerialName("MediaListCollection") val mediaListCollection: MediaListCollection,
    )

    private suspend fun getAniListAnimeListSmart(auth: AuthData): Array<Lists>? {
        return if (requireLibraryRefresh) {
            val list = getFullAniListList(auth)?.data?.mediaListCollection?.lists?.toTypedArray()
            if (list != null) {
                setKey(ANILIST_CACHED_LIST, auth.user.id.toString(), list)
            }
            list
        } else {
            getKey<Array<Lists>>(
                ANILIST_CACHED_LIST,
                auth.user.id.toString()
            ) as? Array<Lists>
        }
    }

    override suspend fun library(auth: AuthData?): SyncAPI.LibraryMetadata? {
        val list = getAniListAnimeListSmart(auth ?: return null)?.groupBy {
            convertAniListStringToStatus(it.status ?: "").stringRes
        }?.mapValues { group ->
            group.value.map { it.entries.map { entry -> entry.toLibraryItem() } }.flatten()
        } ?: emptyMap()

        // To fill empty lists when AniList does not return them
        val baseMap =
            AniListStatusType.entries.filter { it.value >= 0 }.associate {
                it.stringRes to emptyList<SyncAPI.LibraryItem>()
            }

        return SyncAPI.LibraryMetadata(
            (baseMap + list).map { SyncAPI.LibraryList(txt(it.key), it.value) },
            setOf(
                ListSorting.AlphabeticalA,
                ListSorting.AlphabeticalZ,
                ListSorting.UpdatedNew,
                ListSorting.UpdatedOld,
                ListSorting.ReleaseDateNew,
                ListSorting.ReleaseDateOld,
                ListSorting.RatingHigh,
                ListSorting.RatingLow,
            )
        )
    }

    private suspend fun getFullAniListList(auth: AuthData): FullAnilistList? {
        val userID = auth.user.id
        val mediaType = "ANIME"
        val query = """
                query (${'$'}userID: Int = $userID, ${'$'}MEDIA: MediaType = $mediaType) {
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
                                score (format: POINT_100)
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
                                    coverImage { extraLarge large medium }
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
        val text = postApi(auth.token, query)
        return tryParseJson<FullAnilistList>(text)
    }

    suspend fun toggleLike(auth: AuthData, id: Int): Boolean {
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
        val data = postApi(auth.token, q)
        return data != ""
    }

    /** Used to query a saved MediaItem on the list to get the id for removal */
    @Serializable
    data class MediaListItemRoot(
        @JsonProperty("data") @SerialName("data") val data: MediaListItem? = null,
    )

    @Serializable
    data class MediaListItem(
        @JsonProperty("MediaList") @SerialName("MediaList") val mediaList: MediaListId? = null,
    )

    @Serializable
    data class MediaListId(
        @JsonProperty("id") @SerialName("id") val id: Long? = null,
    )

    private suspend fun postDataAboutId(
        auth: AuthData,
        id: Int,
        type: AniListStatusType,
        score: Score?,
        progress: Int?
    ): Boolean {
        val userID = auth.user.id
        val q =
            // Delete item if status type is None
            if (type == AniListStatusType.None) {
                // Get list ID for deletion
                val idQuery = """
                  query MediaList(${'$'}userId: Int = $userID, ${'$'}mediaId: Int = $id) {
                    MediaList(userId: ${'$'}userId, mediaId: ${'$'}mediaId) {
                      id
                    }
                  }
                """
                val response = postApi(auth.token, idQuery)
                val listId =
                    tryParseJson<MediaListItemRoot>(response)?.data?.mediaList?.id ?: return false
                """
                    mutation(${'$'}id: Int = $listId) {
                        DeleteMediaListEntry(id: ${'$'}id) {
                            deleted
                        }
                    }
                """
            } else {
                """mutation (${'$'}id: Int = $id, ${'$'}status: MediaListStatus = ${
                    aniListStatusString[maxOf(
                        0,
                        type.value
                    )]
                }, ${if (score != null) "${'$'}scoreRaw: Int = ${score.toInt(100)}" else ""} , ${if (progress != null) "${'$'}progress: Int = $progress" else ""}) {
                    SaveMediaListEntry (mediaId: ${'$'}id, status: ${'$'}status, scoreRaw: ${'$'}scoreRaw, progress: ${'$'}progress) {
                        id
                        status
                        progress
                        score
                    }
                }"""
            }

        val data = postApi(auth.token, q)
        return data != ""
    }

    private suspend fun getUser(token: AuthToken): AniListUser? {
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
        val data = postApi(token, q)
        if (data.isNullOrBlank()) return null
        val userData = parseJson<AniListRoot>(data)
        val u = userData.data?.viewer ?: return null
        val user = AniListUser(
            u.id,
            u.name,
            u.avatar?.large,
        )
        return user
    }

    suspend fun getAllSeasons(id: Int): List<SeasonResponse?> {
        val seasons = mutableListOf<SeasonResponse?>()
        suspend fun getSeasonRecursive(id: Int) {
            val season = getSeason(id)
            seasons.add(season)
            if (season.data.media.format?.startsWith("TV") == true) {
                season.data.media.relations?.edges?.forEach {
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

    @Serializable
    data class SeasonResponse(
        @JsonProperty("data") @SerialName("data") val data: SeasonData,
    )

    @Serializable
    data class SeasonData(
        @JsonProperty("Media") @SerialName("Media") val media: SeasonMedia,
    )

    @Serializable
    data class RecommendedMedia(
        @JsonProperty("id") @SerialName("id") val id: Int?,
        @JsonProperty("title") @SerialName("title") val title: MediaTitle?,
        @JsonProperty("coverImage") @SerialName("coverImage") val coverImage: MediaCoverImage?,
    )

    @Serializable
    data class CharacterMedia(
        @JsonProperty("id") @SerialName("id") val id: Int?,
        @JsonProperty("title") @SerialName("title") val title: MediaTitle?,
        @JsonProperty("coverImage") @SerialName("coverImage") val coverImage: MediaCoverImage?,
    )

    @Serializable
    data class SeasonMedia(
        @JsonProperty("id") @SerialName("id") val id: Int?,
        @JsonProperty("title") @SerialName("title") val title: MediaTitle?,
        @JsonProperty("idMal") @SerialName("idMal") val idMal: Int?,
        @JsonProperty("format") @SerialName("format") val format: String?,
        @JsonProperty("nextAiringEpisode") @SerialName("nextAiringEpisode") val nextAiringEpisode: SeasonNextAiringEpisode?,
        @JsonProperty("relations") @SerialName("relations") val relations: SeasonEdges?,
        @JsonProperty("coverImage") @SerialName("coverImage") val coverImage: MediaCoverImage?,
        @JsonProperty("duration") @SerialName("duration") val duration: Int?,
        @JsonProperty("episodes") @SerialName("episodes") val episodes: Int?,
        @JsonProperty("genres") @SerialName("genres") val genres: List<String>?,
        @JsonProperty("synonyms") @SerialName("synonyms") val synonyms: List<String>?,
        @JsonProperty("averageScore") @SerialName("averageScore") val averageScore: Int?,
        @JsonProperty("isAdult") @SerialName("isAdult") val isAdult: Boolean?,
        @JsonProperty("trailer") @SerialName("trailer") val trailer: MediaTrailer?,
        @JsonProperty("description") @SerialName("description") val description: String?,
        @JsonProperty("characters") @SerialName("characters") val characters: CharacterConnection?,
        @JsonProperty("recommendations") @SerialName("recommendations") val recommendations: RecommendationConnection?,
    )

    @Serializable
    data class RecommendationConnection(
        @JsonProperty("edges") @SerialName("edges") val edges: List<RecommendationEdge> = emptyList(),
        @JsonProperty("nodes") @SerialName("nodes") val nodes: List<Recommendation> = emptyList(),
    )

    @Serializable
    data class RecommendationEdge(
        @JsonProperty("node") @SerialName("node") val node: Recommendation,
    )

    @Serializable
    data class Recommendation(
        @JsonProperty("id") @SerialName("id") val id: Long,
        @JsonProperty("mediaRecommendation") @SerialName("mediaRecommendation") val mediaRecommendation: RecommendedMedia?,
    )

    @Serializable
    data class CharacterName(
        @JsonProperty("name") @SerialName("name") val first: String?,
        @JsonProperty("middle") @SerialName("middle") val middle: String?,
        @JsonProperty("last") @SerialName("last") val last: String?,
        @JsonProperty("full") @SerialName("full") val full: String?,
        @JsonProperty("native") @SerialName("native") val native: String?,
        @JsonProperty("alternative") @SerialName("alternative") val alternative: List<String>?,
        @JsonProperty("alternativeSpoiler") @SerialName("alternativeSpoiler") val alternativeSpoiler: List<String>?,
        @JsonProperty("userPreferred") @SerialName("userPreferred") val userPreferred: String?,
    )

    @Serializable
    data class CharacterImage(
        @JsonProperty("large") @SerialName("large") val large: String?,
        @JsonProperty("medium") @SerialName("medium") val medium: String?,
    )

    @Serializable
    data class Character(
        @JsonProperty("name") @SerialName("name") val name: CharacterName?,
        @JsonProperty("age") @SerialName("age") val age: String?,
        @JsonProperty("image") @SerialName("image") val image: CharacterImage?,
    )

    @Serializable
    data class CharacterEdge(
        @JsonProperty("id") @SerialName("id") val id: Int?,
        /**
         * MAIN - A primary character role in the media
         * SUPPORTING - A supporting character role in the media
         * BACKGROUND - A background character in the media
         */
        @JsonProperty("role") @SerialName("role") val role: String?,
        @JsonProperty("name") @SerialName("name") val name: String?,
        @JsonProperty("voiceActors") @SerialName("voiceActors") val voiceActors: List<Staff>?,
        @JsonProperty("favouriteOrder") @SerialName("favouriteOrder") val favouriteOrder: Int?,
        @JsonProperty("media") @SerialName("media") val media: List<CharacterMedia>?,
        @JsonProperty("node") @SerialName("node") val node: Character?,
    )

    @Serializable
    data class StaffImage(
        @JsonProperty("large") @SerialName("large") val large: String?,
        @JsonProperty("medium") @SerialName("medium") val medium: String?,
    )

    @Serializable
    data class StaffName(
        @JsonProperty("name") @SerialName("name") val first: String?,
        @JsonProperty("middle") @SerialName("middle") val middle: String?,
        @JsonProperty("last") @SerialName("last") val last: String?,
        @JsonProperty("full") @SerialName("full") val full: String?,
        @JsonProperty("native") @SerialName("native") val native: String?,
        @JsonProperty("alternative") @SerialName("alternative") val alternative: List<String>?,
        @JsonProperty("userPreferred") @SerialName("userPreferred") val userPreferred: String?,
    )

    @Serializable
    data class Staff(
        @JsonProperty("image") @SerialName("image") val image: StaffImage?,
        @JsonProperty("name") @SerialName("name") val name: StaffName?,
        @JsonProperty("age") @SerialName("age") val age: Int?,
    )

    @Serializable
    data class CharacterConnection(
        @JsonProperty("edges") @SerialName("edges") val edges: List<CharacterEdge>?,
        @JsonProperty("nodes") @SerialName("nodes") val nodes: List<Character>?,
    )

    @Serializable
    data class MediaTrailer(
        @JsonProperty("id") @SerialName("id") val id: String?,
        @JsonProperty("site") @SerialName("site") val site: String?,
        @JsonProperty("thumbnail") @SerialName("thumbnail") val thumbnail: String?,
    )

    @Serializable
    data class MediaCoverImage(
        @JsonProperty("extraLarge") @SerialName("extraLarge") val extraLarge: String?,
        @JsonProperty("large") @SerialName("large") val large: String?,
        @JsonProperty("medium") @SerialName("medium") val medium: String?,
        @JsonProperty("color") @SerialName("color") val color: String?,
    )

    @Serializable
    data class SeasonNextAiringEpisode(
        @JsonProperty("episode") @SerialName("episode") val episode: Int?,
        @JsonProperty("timeUntilAiring") @SerialName("timeUntilAiring") val timeUntilAiring: Int?,
    )

    @Serializable
    data class SeasonEdges(
        @JsonProperty("edges") @SerialName("edges") val edges: List<SeasonEdge>?,
    )

    @Serializable
    data class SeasonEdge(
        @JsonProperty("id") @SerialName("id") val id: Int?,
        @JsonProperty("relationType") @SerialName("relationType") val relationType: String?,
        @JsonProperty("node") @SerialName("node") val node: SeasonNode?,
    )

    @Serializable
    data class AniListFavoritesMediaConnection(
        @JsonProperty("nodes") @SerialName("nodes") val nodes: List<LikeNode>,
    )

    @Serializable
    data class AniListFavourites(
        @JsonProperty("anime") @SerialName("anime") val anime: AniListFavoritesMediaConnection,
    )

    @Serializable
    data class MediaTitle(
        @JsonProperty("romaji") @SerialName("romaji") val romaji: String?,
        @JsonProperty("english") @SerialName("english") val english: String?,
        @JsonProperty("native") @SerialName("native") val native: String?,
        @JsonProperty("userPreferred") @SerialName("userPreferred") val userPreferred: String?,
    )

    @Serializable
    data class SeasonNode(
        @JsonProperty("id") @SerialName("id") val id: Int,
        @JsonProperty("format") @SerialName("format") val format: String?,
        @JsonProperty("title") @SerialName("title") val title: Title?,
        @JsonProperty("idMal") @SerialName("idMal") val idMal: Int?,
        @JsonProperty("coverImage") @SerialName("coverImage") val coverImage: CoverImage?,
        @JsonProperty("averageScore") @SerialName("averageScore") val averageScore: Int?,
    )

    @Serializable
    data class AniListAvatar(
        @JsonProperty("large") @SerialName("large") val large: String?,
    )

    @Serializable
    data class AniListViewer(
        @JsonProperty("id") @SerialName("id") val id: Int,
        @JsonProperty("name") @SerialName("name") val name: String,
        @JsonProperty("avatar") @SerialName("avatar") val avatar: AniListAvatar?,
        @JsonProperty("favourites") @SerialName("favourites") val favourites: AniListFavourites?,
    )

    @Serializable
    data class AniListData(
        @JsonProperty("Viewer") @SerialName("Viewer") val viewer: AniListViewer?,
    )

    @Serializable
    data class AniListRoot(
        @JsonProperty("data") @SerialName("data") val data: AniListData?,
    )

    @Serializable
    data class AniListUser(
        @JsonProperty("id") @SerialName("id") val id: Int,
        @JsonProperty("name") @SerialName("name") val name: String,
        @JsonProperty("picture") @SerialName("picture") val picture: String?,
    )

    @Serializable
    data class LikeNode(
        @JsonProperty("id") @SerialName("id") val id: Int?,
    )

    @Serializable
    data class LikePageInfo(
        @JsonProperty("total") @SerialName("total") val total: Int?,
        @JsonProperty("currentPage") @SerialName("currentPage") val currentPage: Int?,
        @JsonProperty("lastPage") @SerialName("lastPage") val lastPage: Int?,
        @JsonProperty("perPage") @SerialName("perPage") val perPage: Int?,
        @JsonProperty("hasNextPage") @SerialName("hasNextPage") val hasNextPage: Boolean?,
    )

    @Serializable
    data class LikeAnime(
        @JsonProperty("nodes") @SerialName("nodes") val nodes: List<LikeNode>?,
        @JsonProperty("pageInfo") @SerialName("pageInfo") val pageInfo: LikePageInfo?,
    )

    @Serializable
    data class LikeFavourites(
        @JsonProperty("anime") @SerialName("anime") val anime: LikeAnime?,
    )

    @Serializable
    data class LikeViewer(
        @JsonProperty("favourites") @SerialName("favourites") val favourites: LikeFavourites?,
    )

    @Serializable
    data class LikeData(
        @JsonProperty("Viewer") @SerialName("Viewer") val viewer: LikeViewer?,
    )

    @Serializable
    data class LikeRoot(
        @JsonProperty("data") @SerialName("data") val data: LikeData?,
    )

    @Serializable
    data class AniListTitleHolder(
        @JsonProperty("title") @SerialName("title") val title: Title?,
        @JsonProperty("isFavourite") @SerialName("isFavourite") val isFavourite: Boolean?,
        @JsonProperty("id") @SerialName("id") val id: Int?,
        @JsonProperty("progress") @SerialName("progress") val progress: Int?,
        @JsonProperty("episodes") @SerialName("episodes") val episodes: Int?,
        @JsonProperty("score") @SerialName("score") val score: Int?,
        @JsonProperty("type") @SerialName("type") val type: AniListStatusType?,
    )

    @Serializable
    data class GetDataMediaListEntry(
        @JsonProperty("progress") @SerialName("progress") val progress: Int?,
        @JsonProperty("status") @SerialName("status") val status: String?,
        @JsonProperty("score") @SerialName("score") val score: Int?,
    )

    @Serializable
    data class Nodes(
        @JsonProperty("id") @SerialName("id") val id: Int?,
        @JsonProperty("mediaRecommendation") @SerialName("mediaRecommendation") val mediaRecommendation: MediaRecommendation?,
    )

    @Serializable
    data class GetDataMedia(
        @JsonProperty("isFavourite") @SerialName("isFavourite") val isFavourite: Boolean?,
        @JsonProperty("episodes") @SerialName("episodes") val episodes: Int?,
        @JsonProperty("title") @SerialName("title") val title: Title?,
        @JsonProperty("mediaListEntry") @SerialName("mediaListEntry") val mediaListEntry: GetDataMediaListEntry?,
    )

    @Serializable
    data class Recommendations(
        @JsonProperty("nodes") @SerialName("nodes") val nodes: List<Nodes>?,
    )

    @Serializable
    data class GetDataData(
        @JsonProperty("Media") @SerialName("Media") val media: GetDataMedia?,
    )

    @Serializable
    data class GetDataRoot(
        @JsonProperty("data") @SerialName("data") val data: GetDataData?,
    )

    @Serializable
    data class GetSearchTitle(
        @JsonProperty("romaji") @SerialName("romaji") val romaji: String?,
    )

    @Serializable
    data class TrailerObject(
        @JsonProperty("id") @SerialName("id") val id: String?,
        @JsonProperty("thumbnail") @SerialName("thumbnail") val thumbnail: String?,
        @JsonProperty("site") @SerialName("site") val site: String?,
    )

    @Serializable
    data class GetSearchMedia(
        @JsonProperty("id") @SerialName("id") val id: Int,
        @JsonProperty("idMal") @SerialName("idMal") val idMal: Int?,
        @JsonProperty("seasonYear") @SerialName("seasonYear") val seasonYear: Int,
        @JsonProperty("title") @SerialName("title") val title: GetSearchTitle,
        @JsonProperty("startDate") @SerialName("startDate") val startDate: StartedAt,
        @JsonProperty("averageScore") @SerialName("averageScore") val averageScore: Int?,
        @JsonProperty("meanScore") @SerialName("meanScore") val meanScore: Int?,
        @JsonProperty("bannerImage") @SerialName("bannerImage") val bannerImage: String?,
        @JsonProperty("trailer") @SerialName("trailer") val trailer: TrailerObject?,
        @JsonProperty("nextAiringEpisode") @SerialName("nextAiringEpisode") val nextAiringEpisode: SeasonNextAiringEpisode?,
        @JsonProperty("recommendations") @SerialName("recommendations") val recommendations: Recommendations?,
        @JsonProperty("relations") @SerialName("relations") val relations: SeasonEdges?,
    )

    @Serializable
    data class GetSearchPage(
        @JsonProperty("Page") @SerialName("Page") val page: GetSearchData?,
    )

    @Serializable
    data class GetSearchData(
        @JsonProperty("media") @SerialName("media") val media: List<GetSearchMedia>?,
    )

    @Serializable
    data class GetSearchRoot(
        @JsonProperty("data") @SerialName("data") val data: GetSearchPage?,
    )
}
