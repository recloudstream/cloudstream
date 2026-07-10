package com.lagradost.cloudstream3.extractors.helper

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.Prerelease
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.base64Encode
import com.lagradost.cloudstream3.utils.AppUtils
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.MD5
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object AesHelper {

    private val provider = CryptographyProvider.Default
    private val aesCbc = provider.get(AES.CBC)
    @OptIn(DelicateCryptographyApi::class)
    private val md5Hasher = provider.get(MD5).hasher()

    @OptIn(DelicateCryptographyApi::class)
    @Prerelease
    suspend fun rawAesCbc(
        input: ByteArray,
        key: ByteArray,
        iv: ByteArray,
        encrypt: Boolean = true,
        padding: Boolean = true,
    ): ByteArray {
        val aesKey = aesCbc.keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, key)
        val cipher = aesKey.cipher(padding = padding)
        return if (!encrypt) {
            cipher.decryptWithIv(iv, input)
        } else cipher.encryptWithIv(iv, input)
    }

    suspend fun cryptoAESHandler(
        data: String,
        pass: ByteArray,
        encrypt: Boolean = true,
        padding: Boolean = true,
    ): String? {
        val parse = AppUtils.tryParseJson<AesData>(data) ?: return null
        val (key, iv) = generateKeyAndIv(
            password = pass,
            salt = parse.s.hexToByteArray(),
            ivLength = parse.iv.length / 2,
            saltLength = parse.s.length / 2,
        ) ?: return null

        return if (!encrypt) {
            val plainBytes = rawAesCbc(base64DecodeArray(parse.ct), key, iv, encrypt = false, padding = padding)
            plainBytes.decodeToString()
        } else {
            base64Encode(rawAesCbc(parse.ct.encodeToByteArray(), key, iv, encrypt = true, padding = padding))
        }
    }

    // Deprecate after next stable
    /* @Deprecated(
        message = "Set padding = false for no padding",
        level = DeprecationLevel.WARNING,
    ) */
    fun cryptoAESHandler(
        data: String,
        pass: ByteArray,
        encrypt: Boolean = true,
        padding: String,
    ): String? {
        // If it ends with NoPadding (e.g. "AES/CBC/NoPadding"), then it
        // doesn't have padding, otherwise we treat as if it does.
        val hasPadding = !padding.endsWith("NoPadding")
        return runBlocking { cryptoAESHandler(data, pass, encrypt, hasPadding) }
    }

    // https://stackoverflow.com/a/41434590/8166854
    fun generateKeyAndIv(
        password: ByteArray,
        salt: ByteArray,
        keyLength: Int = 32,
        ivLength: Int,
        saltLength: Int,
        iterations: Int = 1,
    ): Pair<ByteArray, ByteArray>? {
        return try {
            val digestLength = 16 // MD5 digest is always 16 bytes
            val targetKeySize = keyLength + ivLength
            val requiredLength = (targetKeySize + digestLength - 1) / digestLength * digestLength
            val generatedData = ByteArray(requiredLength)
            var generatedLength = 0

            while (generatedLength < targetKeySize) {
                val hashFn = md5Hasher.createHashFunction()
                if (generatedLength > 0) {
                    // update(source, startIndex, endIndex) — endIndex is exclusive
                    hashFn.update(generatedData, generatedLength - digestLength, generatedLength)
                }
                hashFn.update(password)
                hashFn.update(salt, 0, saltLength)
                val digest = hashFn.hashToByteArray()
                digest.copyInto(generatedData, generatedLength)

                for (i in 1 until iterations) {
                    val iterFn = md5Hasher.createHashFunction()
                    iterFn.update(generatedData, generatedLength, generatedLength + digestLength)
                    val iterDigest = iterFn.hashToByteArray()
                    iterDigest.copyInto(generatedData, generatedLength)
                }

                generatedLength += digestLength
            }

            generatedData.copyOfRange(0, keyLength) to
                generatedData.copyOfRange(keyLength, targetKeySize)
        } catch (_: Exception) {
            null
        }
    }

    fun String.hexToByteArray(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    @Serializable
    private data class AesData(
        @JsonProperty("ct") @SerialName("ct") val ct: String,
        @JsonProperty("iv") @SerialName("iv") val iv: String,
        @JsonProperty("s") @SerialName("s") val s: String,
    )
}
