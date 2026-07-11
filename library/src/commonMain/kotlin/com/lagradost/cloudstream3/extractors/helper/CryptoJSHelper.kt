package com.lagradost.cloudstream3.extractors.helper

import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.base64Encode
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.MD5
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlin.math.min

/**
 * Conforming with CryptoJS AES method
 */
// see https://gist.github.com/thackerronak/554c985c3001b16810af5fc0eb5c358f
@Suppress("unused", "FunctionName", "SameParameterValue")
object CryptoJS {
    private const val KEY_SIZE = 256
    private const val IV_SIZE = 128

    // Seriously crypto-js, what's wrong with you?
    private const val APPEND = "Salted__"

    private val provider = CryptographyProvider.Default
    private val aesCbc = provider.get(AES.CBC)
    @OptIn(DelicateCryptographyApi::class)
    private val md5Hasher = provider.get(MD5).hasher()

    /**
     * Encrypt
     * @param password passphrase
     * @param plainText plain string
     */
    @OptIn(DelicateCryptographyApi::class)
    fun encrypt(password: String, plainText: String): String {
        val saltBytes = generateSalt(8)
        val key = ByteArray(KEY_SIZE / 8)
        val iv = ByteArray(IV_SIZE / 8)
        evpkdf(password.encodeToByteArray(), KEY_SIZE, IV_SIZE, saltBytes, 1, key, iv)

        val aesKey = aesCbc.keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, key)
        val cipher = aesKey.cipher(padding = true)
        val cipherText = cipher.encryptWithIvBlocking(iv, plainText.encodeToByteArray())

        // Create CryptoJS-like encrypted: "Salted__" || salt || ciphertext
        val sBytes = APPEND.encodeToByteArray()
        val b = ByteArray(sBytes.size + saltBytes.size + cipherText.size)
        sBytes.copyInto(destination = b, destinationOffset = 0)
        saltBytes.copyInto(destination = b, destinationOffset = sBytes.size)
        cipherText.copyInto(destination = b, destinationOffset = sBytes.size + saltBytes.size)

        return base64Encode(b)
    }

    /**
     * Decrypt
     * Thanks Artjom B. for this: http://stackoverflow.com/a/29152379/4405051
     * @param password passphrase
     * @param cipherText encrypted string
     */
    @OptIn(DelicateCryptographyApi::class)
    fun decrypt(password: String, cipherText: String): String {
        val ctBytes = base64DecodeArray(cipherText)
        val saltBytes = ctBytes.copyOfRange(8, 16)
        val cipherTextBytes = ctBytes.copyOfRange(16, ctBytes.size)

        val key = ByteArray(KEY_SIZE / 8)
        val iv = ByteArray(IV_SIZE / 8)
        evpkdf(password.encodeToByteArray(), KEY_SIZE, IV_SIZE, saltBytes, 1, key, iv)

        val aesKey = aesCbc.keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, key)
        val cipher = aesKey.cipher(padding = true)
        val plainText = cipher.decryptWithIvBlocking(iv, cipherTextBytes)
        return plainText.decodeToString()
    }

    private fun evpkdf(
        password: ByteArray,
        keySize: Int,
        ivSize: Int,
        salt: ByteArray,
        iterations: Int,
        resultKey: ByteArray,
        resultIv: ByteArray,
    ): ByteArray {
        val keySize = keySize / 32
        val ivSize = ivSize / 32
        val targetKeySize = keySize + ivSize
        val derivedBytes = ByteArray(targetKeySize * 4)
        var numberOfDerivedWords = 0
        var block: ByteArray? = null

        while (numberOfDerivedWords < targetKeySize) {
            val hashFn = md5Hasher.createHashFunction()
            if (block != null) hashFn.update(block)
            hashFn.update(password)
            hashFn.update(salt)
            block = hashFn.hashToByteArray()

            for (i in 1 until iterations) {
                val iterFn = md5Hasher.createHashFunction()
                iterFn.update(block!!)
                block = iterFn.hashToByteArray()
            }

            block.copyInto(
                destination = derivedBytes,
                destinationOffset = numberOfDerivedWords * 4,
                startIndex = 0,
                endIndex = min(block.size, (targetKeySize - numberOfDerivedWords) * 4)
            )

            numberOfDerivedWords += block.size / 4
        }

        derivedBytes.copyInto(destination = resultKey, destinationOffset = 0, startIndex = 0, endIndex = keySize * 4)
        derivedBytes.copyInto(destination = resultIv, destinationOffset = 0, startIndex = keySize * 4, endIndex = (keySize + ivSize) * 4)
        return derivedBytes // key + iv
    }

    private fun generateSalt(length: Int): ByteArray =
        CryptographyRandom.nextBytes(length)
}
