package com.lagradost.cloudstream3.extractors.helper

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.base64Encode
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.jsoup.nodes.Document
import java.net.URI
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object GogoHelper {

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

    // https://github.com/saikou-app/saikou/blob/45d0a99b8a72665a29a1eadfb38c506b842a29d7/app/src/main/java/ani/saikou/parsers/anime/extractors/GogoCDN.kt#L97
    // No Licence on the function
    private fun cryptoHandler(
        string: String,
        iv: String,
        secretKeyString: String,
        encrypt: Boolean = true
    ): String {
        //println("IV: $iv, Key: $secretKeyString, encrypt: $encrypt, Message: $string")
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
                    ExtractorLink(
                        mainApiName,
                        mainApiName,
                        source.file,
                        mainUrl,
                        getQualityFromName(source.label),
                    )
                )
            }
        }

        sources.source?.forEach {
            invokeGogoSource(it, callback)
        }
        sources.sourceBk?.forEach {
            invokeGogoSource(it, callback)
        }
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
}