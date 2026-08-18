package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import io.ktor.http.Url
import io.ktor.http.decodeURLPart
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class Bysezejataos : ByseSX() {
    override val name = "Bysezejataos"
    override val mainUrl = "https://bysezejataos.com"
}

class ByseBuho : ByseSX() {
    override val name = "ByseBuho"
    override val mainUrl = "https://bysebuho.com"
}

class ByseVepoin : ByseSX() {
    override val name = "ByseVepoin"
    override val mainUrl = "https://bysevepoin.com"
}

class ByseQekaho : ByseSX() {
    override val name = "ByseQekaho"
    override val mainUrl = "https://byseqekaho.com"
}

open class ByseSX : ExtractorApi() {
    override val name = "Byse"
    override val mainUrl = "https://byse.sx"
    override val requiresReferer = true

    private val aesGcm = CryptographyProvider.Default.get(AES.GCM)

    private fun b64UrlDecode(s: String): ByteArray {
        val fixed = s.replace('-', '+').replace('_', '/')
        val pad = (4 - fixed.length % 4) % 4
        return base64DecodeArray(fixed + "=".repeat(pad))
    }

    private fun getBaseUrl(url: String): String {
        return Url(url).let { "${it.protocol.name}://${it.host}" }
    }

    private fun getCodeFromUrl(url: String): String {
        val path = Url(url).encodedPath.decodeURLPart()
        return path.trimEnd('/').substringAfterLast('/')
    }

    private suspend fun getDetails(mainUrl: String): DetailsRoot? {
        val base = getBaseUrl(mainUrl)
        val code = getCodeFromUrl(mainUrl)
        val url = "$base/api/videos/$code/embed/details"
        return app.get(url).parsedSafe<DetailsRoot>()
    }

    private suspend fun getPlayback(mainUrl: String): PlaybackRoot? {
        val details = getDetails(mainUrl) ?: return null
        val embedFrameUrl = details.embedFrameUrl
        val embedBase = getBaseUrl(embedFrameUrl)
        val code = getCodeFromUrl(embedFrameUrl)
        val playbackUrl = "$embedBase/api/videos/$code/embed/playback"
        val headers = mapOf(
            "accept" to "*/*",
            "accept-language" to "en-US,en;q=0.5",
            "priority" to "u=1, i",
            "referer" to embedFrameUrl,
            "x-embed-parent" to mainUrl,
        )
        return app.get(playbackUrl, headers = headers).parsedSafe<PlaybackRoot>()
    }

    private fun buildAesKey(playback: Playback): ByteArray {
        val p1 = b64UrlDecode(playback.keyParts[0])
        val p2 = b64UrlDecode(playback.keyParts[1])
        return p1 + p2
    }

    @OptIn(DelicateCryptographyApi::class)
    private suspend fun decryptPlayback(playback: Playback): String? {
        val keyBytes = buildAesKey(playback)
        val ivBytes = b64UrlDecode(playback.iv)
        val cipherBytes = b64UrlDecode(playback.payload)

        val aesKey = aesGcm.keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, keyBytes)
        // 128-bit GCM tag (default)
        val cipher = aesKey.cipher()
        val plainBytes = cipher.decryptWithIv(ivBytes, cipherBytes)

        var jsonStr = plainBytes.decodeToString()
        if (jsonStr.startsWith("\uFEFF")) jsonStr = jsonStr.substring(1)

        val root = try {
            tryParseJson<PlaybackDecrypt>(jsonStr)
        } catch (_: Exception) {
            return null
        }

        return root?.sources?.firstOrNull()?.url
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val refererUrl = getBaseUrl(url)
        val playbackRoot = getPlayback(url) ?: return
        val streamUrl = decryptPlayback(playbackRoot.playback) ?: return

        val headers = mapOf("Referer" to refererUrl)
        M3u8Helper.generateM3u8(
            name,
            streamUrl,
            mainUrl,
            headers = headers,
        ).forEach(callback)
    }
}

@Serializable
data class DetailsRoot(
    @JsonProperty("id") @SerialName("id") val id: Long,
    @JsonProperty("code") @SerialName("code") val code: String,
    @JsonProperty("title") @SerialName("title") val title: String,
    @JsonProperty("poster_url") @SerialName("poster_url") val posterUrl: String,
    @JsonProperty("description") @SerialName("description") val description: String,
    @JsonProperty("created_at") @SerialName("created_at") val createdAt: String,
    @JsonProperty("owner_private") @SerialName("owner_private") val ownerPrivate: Boolean,
    @JsonProperty("embed_frame_url") @SerialName("embed_frame_url") val embedFrameUrl: String,
)

@Serializable
data class PlaybackRoot(
    @JsonProperty("playback") @SerialName("playback") val playback: Playback,
)

@Serializable
data class Playback(
    @JsonProperty("algorithm") @SerialName("algorithm") val algorithm: String,
    @JsonProperty("iv") @SerialName("iv") val iv: String,
    @JsonProperty("payload") @SerialName("payload") val payload: String,
    @JsonProperty("key_parts") @SerialName("key_parts") val keyParts: List<String>,
    @JsonProperty("expires_at") @SerialName("expires_at") val expiresAt: String,
    @JsonProperty("decrypt_keys") @SerialName("decrypt_keys") val decryptKeys: DecryptKeys,
    @JsonProperty("iv2") @SerialName("iv2") val iv2: String,
    @JsonProperty("payload2") @SerialName("payload2") val payload2: String,
)

@Serializable
data class DecryptKeys(
    @JsonProperty("edge_1") @SerialName("edge_1") val edge1: String,
    @JsonProperty("edge_2") @SerialName("edge_2") val edge2: String,
    @JsonProperty("legacy_fallback") @SerialName("legacy_fallback") val legacyFallback: String,
)

@Serializable
data class PlaybackDecrypt(
    @JsonProperty("sources") @SerialName("sources") val sources: List<PlaybackDecryptSource>,
)

@Serializable
data class PlaybackDecryptSource(
    @JsonProperty("quality") @SerialName("quality") val quality: String,
    @JsonProperty("label") @SerialName("label") val label: String,
    @JsonProperty("mime_type") @SerialName("mime_type") val mimeType: String,
    @JsonProperty("url") @SerialName("url") val url: String,
    @JsonProperty("bitrate_kbps") @SerialName("bitrate_kbps") val bitrateKbps: Long,
    @JsonProperty("height") @SerialName("height") val height: Int?,
)
