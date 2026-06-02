package com.lagradost.cloudstream3.extractors.helper

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.base64Encode
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import org.jsoup.nodes.Document
import java.net.URI

object GogoHelper {

    private val aesCbc = CryptographyProvider.Default.get(AES.CBC)

    /**
     * @param id base64Decode(show_id) + IV
     * @return the encryption key
     */
    private fun getKey(id: String): String? {
        return safe {
            id.map {
                it.code.toString(16)
            }.joinToString("").substring(0, 32)
        }
    }

    // https://github.com/saikou-app/saikou/blob/45d0a99b8a72665a29a1eadfb38c506b842a29d7/app/src/main/java/ani/saikou/parsers/anime/extractors/GogoCDN.kt#L97
    // No Licence on the function
    @OptIn(DelicateCryptographyApi::class)
    private fun cryptoHandler(
        string: String,
        iv: String,
        secretKeyString: String,
        encrypt: Boolean = true
    ): String {
        val ivBytes = iv.encodeToByteArray()
        val keyBytes = secretKeyString.encodeToByteArray()
        val aesKey = aesCbc.keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, keyBytes)
        val cipher = aesKey.cipher(padding = true)

        return if (!encrypt) {
            val plainBytes = cipher.decryptWithIvBlocking(ivBytes, base64DecodeArray(string))
            plainBytes.decodeToString()
        } else {
            base64Encode(cipher.encryptWithIvBlocking(ivBytes, string.encodeToByteArray()))
        }
    }

    /**
     * @param iframeUrl something like https://gogoplay4.com/streaming.php?id=XXXXXX
     * @param mainApiName used for ExtractorLink names and source
     * @param iv secret iv from site, required non-null if isUsingAdaptiveKeys is off
     * @param secretKey secret key for decryption from site, required non-null if isUsingAdaptiveKeys is off
     * @param secretDecryptKey secret key to decrypt the response json, required non-null if isUsingAdaptiveKeys is off
     * @param isUsingAdaptiveKeys generates keys from IV and ID, see getKey()
     * @param isUsingAdaptiveData generate encrypt-ajax data based on $("script[data-name='episode']")[0].dataset.value
     */
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
            val dataEncrypted = realDocument.select("script[data-name='episode']").attr("data-value")
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
        val dataencrypted = jsonResponse.parsedSafe<GogoJsonData>()?.data ?: return@safeApiCall
        val datadecrypted = cryptoHandler(dataencrypted, foundIv, foundDecryptKey, false)
        val sources = AppUtils.parseJson<GogoSources>(datadecrypted)

        suspend fun invokeGogoSource(
            source: GogoSource,
            sourceCallback: (ExtractorLink) -> Unit
        ) {
            if (source.file.contains(".m3u8")) {
                M3u8Helper.generateM3u8(
                    mainApiName,
                    source.file,
                    mainUrl,
                    headers = mapOf("Origin" to "https://plyr.link")
                ).forEach(sourceCallback)
            } else {
                sourceCallback.invoke(
                    newExtractorLink(
                        mainApiName,
                        mainApiName,
                        source.file,
                    ) {
                        this.referer = mainUrl
                        this.quality = getQualityFromName(source.label)
                    }
                )
            }
        }

        sources.source?.forEach { invokeGogoSource(it, callback) }
        sources.sourceBk?.forEach { invokeGogoSource(it, callback) }
    }

    data class GogoSources(
        @JsonProperty("source") val source: List<GogoSource>?,
        @JsonProperty("sourceBk") val sourceBk: List<GogoSource>?,
    )

    data class GogoSource(
        @JsonProperty("file") val file: String,
        @JsonProperty("label") val label: String?,
        @JsonProperty("type") val type: String?,
        @JsonProperty("default") val default: String? = null
    )

    data class GogoJsonData(
        @JsonProperty("data") val data: String? = null
    )
}
