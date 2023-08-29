package com.lagradost.cloudstream3.extractors.helper

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.base64Encode
import com.lagradost.cloudstream3.utils.AppUtils
import java.security.DigestException
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object AesHelper {

    fun cryptoAESHandler(
        data: String,
        pass: ByteArray,
        encrypt: Boolean = true,
        padding: String = "AES/CBC/PKCS5PADDING",
    ): String? {
        val parse = AppUtils.tryParseJson<AesData>(data) ?: return null
        val (key, iv) = generateKeyAndIv(pass, parse.s.hexToByteArray()) ?: throw ErrorLoadingException("failed to generate key")
        val cipher = Cipher.getInstance(padding)
        return if (!encrypt) {
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            String(cipher.doFinal(base64DecodeArray(parse.ct)))
        } else {
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            base64Encode(cipher.doFinal(parse.ct.toByteArray()))
        }
    }

    // https://stackoverflow.com/a/41434590/8166854
    fun generateKeyAndIv(
        password: ByteArray,
        salt: ByteArray,
        hashAlgorithm: String = "MD5",
        keyLength: Int = 32,
        ivLength: Int = 16,
        iterations: Int = 1
    ): List<ByteArray>? {

        val md = MessageDigest.getInstance(hashAlgorithm)
        val digestLength = md.digestLength
        val targetKeySize = keyLength + ivLength
        val requiredLength = (targetKeySize + digestLength - 1) / digestLength * digestLength
        val generatedData = ByteArray(requiredLength)
        var generatedLength = 0

        try {
            md.reset()

            while (generatedLength < targetKeySize) {
                if (generatedLength > 0)
                    md.update(
                        generatedData,
                        generatedLength - digestLength,
                        digestLength
                    )

                md.update(password)
                md.update(salt, 0, 8)
                md.digest(generatedData, generatedLength, digestLength)

                for (i in 1 until iterations) {
                    md.update(generatedData, generatedLength, digestLength)
                    md.digest(generatedData, generatedLength, digestLength)
                }

                generatedLength += digestLength
            }
            return listOf(
                generatedData.copyOfRange(0, keyLength),
                generatedData.copyOfRange(keyLength, targetKeySize)
            )
        } catch (e: DigestException) {
            return null
        }
    }

    fun String.hexToByteArray(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    private data class AesData(
        @JsonProperty("ct") val ct: String,
        @JsonProperty("iv") val iv: String,
        @JsonProperty("s") val s: String
    )

}