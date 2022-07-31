package com.lagradost.cloudstream3.animeproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class GogoanimeProvider : MainAPI() {
    companion object {
        fun getType(t: String): TvType {
            return if (t.contains("OVA") || t.contains("Special")) TvType.OVA
            else if (t.contains("Movie")) TvType.AnimeMovie
            else TvType.Anime
        }

        fun getStatus(t: String): ShowStatus {
            return when (t) {
                "Completed" -> ShowStatus.Completed
                "Ongoing" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }

        /**
         * @param id base64Decode(show_id) + IV
         * @return the encryption key
         * */
        private fun getKey(id: String): String? {
            return normalSafeApiCall {
                id.map {
                    it.code.toString(16)
                }.joinToString("").substring(0, 32)
            }
        }

        val qualityRegex = Regex("(\\d+)P")

        // https://github.com/saikou-app/saikou/blob/3e756bd8e876ad7a9318b17110526880525a5cd3/app/src/main/java/ani/saikou/anime/source/extractors/GogoCDN.kt#L60
        // No Licence on the function
        private fun cryptoHandler(
            string: String,
            iv: String,
            secretKeyString: String,
            encrypt: Boolean = true
        ): String {
            println("IV: $iv, Key: $secretKeyString, encrypt: $encrypt, Message: $string")
            val ivParameterSpec = IvParameterSpec(iv.toByteArray())
            val secretKey = SecretKeySpec(secretKeyString.toByteArray(), "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            return if (!encrypt) {
                cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec)
                String(cipher.doFinal(base64DecodeArray(string)))
            } else {
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec)
                base64Encode(cipher.doFinal(string.toByteArray()))
            }
        }

        private fun String.decodeHex(): ByteArray {
            check(length % 2 == 0) { "Must have an even length" }
            return chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
        }

        /**
         * @param iframeUrl something like https://gogoplay4.com/streaming.php?id=XXXXXX
         * @param mainApiName used for ExtractorLink names and source
         * @param iv secret iv from site, required non-null if isUsingAdaptiveKeys is off
         * @param secretKey secret key for decryption from site, required non-null if isUsingAdaptiveKeys is off
         * @param secretDecryptKey secret key to decrypt the response json, required non-null if isUsingAdaptiveKeys is off
         * @param isUsingAdaptiveKeys generates keys from IV and ID, see getKey()
         * @param isUsingAdaptiveData generate encrypt-ajax data based on $("script[data-name='episode']")[0].dataset.value
         * */
        suspend fun extractVidstream(
            iframeUrl: String,
            mainApiName: String,
            callback: (ExtractorLink) -> Unit,
            iv: String?,
            secretKey: String?,
            secretDecryptKey: String?,
            // This could be removed, but i prefer it verbose
            isUsingAdaptiveKeys: Boolean,
            isUsingAdaptiveData: Boolean,
            // If you don't want to re-fetch the document
            iframeDocument: Document? = null
        ) = safeApiCall {
            // https://github.com/saikou-app/saikou/blob/3e756bd8e876ad7a9318b17110526880525a5cd3/app/src/main/java/ani/saikou/anime/source/extractors/GogoCDN.kt
            // No Licence on the following code
            // Also modified of https://github.com/jmir1/aniyomi-extensions/blob/master/src/en/gogoanime/src/eu/kanade/tachiyomi/animeextension/en/gogoanime/extractors/GogoCdnExtractor.kt
            // License on the code above  https://github.com/jmir1/aniyomi-extensions/blob/master/LICENSE

            if ((iv == null || secretKey == null || secretDecryptKey == null) && !isUsingAdaptiveKeys)
                return@safeApiCall

            val id = Regex("id=([^&]+)").find(iframeUrl)!!.value.removePrefix("id=")

            var document: Document? = iframeDocument
            val foundIv =
                iv ?: (document ?: app.get(iframeUrl).document.also { document = it })
                    .select("""div.wrapper[class*=container]""")
                    .attr("class").split("-").lastOrNull() ?: return@safeApiCall
            val foundKey = secretKey ?: getKey(base64Decode(id) + foundIv) ?: return@safeApiCall
            val foundDecryptKey = secretDecryptKey ?: foundKey

            val uri = URI(iframeUrl)
            val mainUrl = "https://" + uri.host

            val encryptedId = cryptoHandler(id, foundIv, foundKey)
            val encryptRequestData = if (isUsingAdaptiveData) {
                // Only fetch the document if necessary
                val realDocument = document ?: app.get(iframeUrl).document
                val dataEncrypted =
                    realDocument.select("script[data-name='episode']").attr("data-value")
                val headers = cryptoHandler(dataEncrypted, foundIv, foundKey, false)
                "id=$encryptedId&alias=$id&" + headers.substringAfter("&")
            } else {
                "id=$encryptedId&alias=$id"
            }

            val jsonResponse =
                app.get(
                    "$mainUrl/encrypt-ajax.php?$encryptRequestData",
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest")
                )
            val dataencrypted =
                jsonResponse.text.substringAfter("{\"data\":\"").substringBefore("\"}")
            val datadecrypted = cryptoHandler(dataencrypted, foundIv, foundDecryptKey, false)
            val sources = AppUtils.parseJson<GogoSources>(datadecrypted)

            fun invokeGogoSource(
                source: GogoSource,
                sourceCallback: (ExtractorLink) -> Unit
            ) {
                sourceCallback.invoke(
                    ExtractorLink(
                        mainApiName,
                        mainApiName,
                        source.file,
                        mainUrl,
                        getQualityFromName(source.label),
                        isM3u8 = source.type == "hls" || source.label?.contains(
                            "auto",
                            ignoreCase = true
                        ) == true
                    )
                )
            }

            sources.source?.forEach {
                invokeGogoSource(it, callback)
            }
            sources.sourceBk?.forEach {
                invokeGogoSource(it, callback)
            }
        }
    }

    override var mainUrl = "https://gogoanime.lu"
    override var name = "GogoAnime"
    override val hasQuickSearch = false
    override val hasMainPage = true

    override val supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.Anime,
        TvType.OVA
    )

    val headers = mapOf(
        "authority" to "ajax.gogo-load.com",
        "sec-ch-ua" to "\"Google Chrome\";v=\"89\", \"Chromium\";v=\"89\", \";Not A Brand\";v=\"99\"",
        "accept" to "text/html, */*; q=0.01",
        "dnt" to "1",
        "sec-ch-ua-mobile" to "?0",
        "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.90 Safari/537.36",
        "origin" to mainUrl,
        "sec-fetch-site" to "cross-site",
        "sec-fetch-mode" to "cors",
        "sec-fetch-dest" to "empty",
        "referer" to "$mainUrl/"
    )
    val parseRegex =
        Regex("""<li>\s*\n.*\n.*<a\s*href=["'](.*?-episode-(\d+))["']\s*title=["'](.*?)["']>\n.*?img src="(.*?)"""")

    override val mainPage = mainPageOf(
        Pair("1", "Recent Release - Sub"),
        Pair("2", "Recent Release - Dub"),
        Pair("3", "Recent Release - Chinese"),
    )

    override suspend fun getMainPage(
        page: Int,
        request : MainPageRequest
    ): HomePageResponse {
        val params = mapOf("page" to page.toString(), "type" to request.data)
        val html = app.get(
            "https://ajax.gogo-load.com/ajax/page-recent-release.html",
            headers = headers,
            params = params
        )
        val isSub = listOf(1, 3).contains(request.data.toInt())

        val home = parseRegex.findAll(html.text).map {
            val (link, epNum, title, poster) = it.destructured
            newAnimeSearchResponse(title, link) {
                this.posterUrl = poster
                addDubStatus(!isSub, epNum.toIntOrNull())
            }
        }.toList()

        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): ArrayList<SearchResponse> {
        val link = "$mainUrl/search.html?keyword=$query"
        val html = app.get(link).text
        val doc = Jsoup.parse(html)

        val episodes = doc.select(""".last_episodes li""").mapNotNull {
            AnimeSearchResponse(
                it.selectFirst(".name")?.text()?.replace(" (Dub)", "") ?: return@mapNotNull null,
                fixUrl(it.selectFirst(".name > a")?.attr("href") ?: return@mapNotNull null),
                this.name,
                TvType.Anime,
                it.selectFirst("img")?.attr("src"),
                it.selectFirst(".released")?.text()?.split(":")?.getOrNull(1)?.trim()
                    ?.toIntOrNull(),
                if (it.selectFirst(".name")?.text()
                        ?.contains("Dub") == true
                ) EnumSet.of(DubStatus.Dubbed) else EnumSet.of(
                    DubStatus.Subbed
                ),
            )
        }

        return ArrayList(episodes)
    }

    private fun getProperAnimeLink(uri: String): String {
        if (uri.contains("-episode")) {
            val split = uri.split("/")
            val slug = split[split.size - 1].split("-episode")[0]
            return "$mainUrl/category/$slug"
        }
        return uri
    }

    override suspend fun load(url: String): LoadResponse {
        val link = getProperAnimeLink(url)
        val episodeloadApi = "https://ajax.gogo-load.com/ajax/load-list-episode"
        val doc = app.get(link).document

        val animeBody = doc.selectFirst(".anime_info_body_bg")
        val title = animeBody?.selectFirst("h1")!!.text()
        val poster = animeBody.selectFirst("img")?.attr("src")
        var description: String? = null
        val genre = ArrayList<String>()
        var year: Int? = null
        var status: String? = null
        var nativeName: String? = null
        var type: String? = null

        animeBody.select("p.type").forEach { pType ->
            when (pType.selectFirst("span")?.text()?.trim()) {
                "Plot Summary:" -> {
                    description = pType.text().replace("Plot Summary:", "").trim()
                }
                "Genre:" -> {
                    genre.addAll(pType.select("a").map {
                        it.attr("title")
                    })
                }
                "Released:" -> {
                    year = pType.text().replace("Released:", "").trim().toIntOrNull()
                }
                "Status:" -> {
                    status = pType.text().replace("Status:", "").trim()
                }
                "Other name:" -> {
                    nativeName = pType.text().replace("Other name:", "").trim()
                }
                "Type:" -> {
                    type = pType.text().replace("type:", "").trim()
                }
            }
        }

        val animeId = doc.selectFirst("#movie_id")!!.attr("value")
        val params = mapOf("ep_start" to "0", "ep_end" to "2000", "id" to animeId)

        val episodes = app.get(episodeloadApi, params = params).document.select("a").map {
            Episode(
                fixUrl(it.attr("href").trim()),
                "Episode " + it.selectFirst(".name")?.text()?.replace("EP", "")?.trim()
            )
        }.reversed()

        return newAnimeLoadResponse(title, link, getType(type.toString())) {
            japName = nativeName
            engName = title
            posterUrl = poster
            this.year = year
            addEpisodes(DubStatus.Subbed, episodes) // TODO CHECK
            plot = description
            tags = genre

            showStatus = getStatus(status.toString())
        }
    }

    data class GogoSources(
        @JsonProperty("source") val source: List<GogoSource>?,
        @JsonProperty("sourceBk") val sourceBk: List<GogoSource>?,
        //val track: List<Any?>,
        //val advertising: List<Any?>,
        //val linkiframe: String
    )

    data class GogoSource(
        @JsonProperty("file") val file: String,
        @JsonProperty("label") val label: String?,
        @JsonProperty("type") val type: String?,
        @JsonProperty("default") val default: String? = null
    )

    private suspend fun extractVideos(
        uri: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(uri).document

        val iframe = fixUrlNull(doc.selectFirst("div.play-video > iframe")?.attr("src")) ?: return

        argamap(
            {
                val link = iframe.replace("streaming.php", "download")
                val page = app.get(link, headers = mapOf("Referer" to iframe))

                page.document.select(".dowload > a").apmap {
                    if (it.hasAttr("download")) {
                        val qual = if (it.text()
                                .contains("HDP")
                        ) "1080" else qualityRegex.find(it.text())?.destructured?.component1()
                            .toString()
                        callback(
                            ExtractorLink(
                                "Gogoanime",
                                "Gogoanime",
                                it.attr("href"),
                                page.url,
                                getQualityFromName(qual),
                                it.attr("href").contains(".m3u8")
                            )
                        )
                    } else {
                        val url = it.attr("href")
                        loadExtractor(url, null, subtitleCallback, callback)
                    }
                }
            }, {
                val streamingResponse = app.get(iframe, headers = mapOf("Referer" to iframe))
                val streamingDocument = streamingResponse.document
                argamap({
                    streamingDocument.select(".list-server-items > .linkserver")
                        .forEach { element ->
                            val status = element.attr("data-status") ?: return@forEach
                            if (status != "1") return@forEach
                            val data = element.attr("data-video") ?: return@forEach
                            loadExtractor(data, streamingResponse.url, subtitleCallback, callback)
                        }
                }, {
                    val iv = "3134003223491201"
                    val secretKey = "37911490979715163134003223491201"
                    val secretDecryptKey = "54674138327930866480207815084989"
                    extractVidstream(
                        iframe,
                        this.name,
                        callback,
                        iv,
                        secretKey,
                        secretDecryptKey,
                        isUsingAdaptiveKeys = false,
                        isUsingAdaptiveData = true
                    )
                })
            }
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        extractVideos(data, subtitleCallback, callback)
        return true
    }
}
