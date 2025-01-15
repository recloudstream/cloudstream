package com.lagradost.cloudstream3.extractors.helper

import com.lagradost.cloudstream3.utils.StringUtils.decodeUri
import com.lagradost.cloudstream3.utils.StringUtils.encodeUri

// Taken from https://github.com/saikou-app/saikou/blob/b35364c8c2a00364178a472fccf1ab72f09815b4/app/src/main/java/ani/saikou/parsers/anime/NineAnime.kt
// GNU General Public License v3.0 https://github.com/saikou-app/saikou/blob/main/LICENSE.md
object NineAnimeHelper {
    private const val nineAnimeKey =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private const val cipherKey = "kMXzgyNzT3k5dYab"

    fun encodeVrf(text: String, mainKey: String): String {
        return encode(
            encrypt(
                cipher(mainKey, encode(text)),
                nineAnimeKey
        )//.replace("""=+$""".toRegex(), "")
        )
    }

    fun decodeVrf(text: String, mainKey: String): String {
        return decode(cipher(mainKey, decrypt(text, nineAnimeKey)))
    }

    fun encrypt(input: String, key: String): String {
        if (input.any { it.code > 255 }) throw Exception("illegal characters!")
        var output = ""
        for (i in input.indices step 3) {
            val a = intArrayOf(-1, -1, -1, -1)
            a[0] = input[i].code shr 2
            a[1] = (3 and input[i].code) shl 4
            if (input.length > i + 1) {
                a[1] = a[1] or (input[i + 1].code shr 4)
                a[2] = (15 and input[i + 1].code) shl 2
            }
            if (input.length > i + 2) {
                a[2] = a[2] or (input[i + 2].code shr 6)
                a[3] = 63 and input[i + 2].code
            }
            for (n in a) {
                if (n == -1) output += "="
                else {
                    if (n in 0..63) output += key[n]
                }
            }
        }
        return output
    }

    fun cipher(key: String, text: String): String {
        val arr = IntArray(256) { it }

        var u = 0
        var r: Int
        arr.indices.forEach {
            u = (u + arr[it] + key[it % key.length].code) % 256
            r = arr[it]
            arr[it] = arr[u]
            arr[u] = r
        }
        u = 0
        var c = 0

        return text.indices.map { j ->
            c = (c + 1) % 256
            u = (u + arr[c]) % 256
            r = arr[c]
            arr[c] = arr[u]
            arr[u] = r
            (text[j].code xor arr[(arr[c] + arr[u]) % 256]).toChar()
        }.joinToString("")
    }

    @Suppress("SameParameterValue")
    private fun decrypt(input: String, key: String): String {
        val t = if (input.replace("""[\t\n\f\r]""".toRegex(), "").length % 4 == 0) {
            input.replace("""==?$""".toRegex(), "")
        } else input
        if (t.length % 4 == 1 || t.contains("""[^+/0-9A-Za-z]""".toRegex())) throw Exception("bad input")
        var i: Int
        var r = ""
        var e = 0
        var u = 0
        for (o in t.indices) {
            e = e shl 6
            i = key.indexOf(t[o])
            e = e or i
            u += 6
            if (24 == u) {
                r += ((16711680 and e) shr 16).toChar()
                r += ((65280 and e) shr 8).toChar()
                r += (255 and e).toChar()
                e = 0
                u = 0
            }
        }
        return if (12 == u) {
            e = e shr 4
            r + e.toChar()
        } else {
            if (18 == u) {
                e = e shr 2
                r += ((65280 and e) shr 8).toChar()
                r += (255 and e).toChar()
            }
            r
        }
    }

    fun encode(input: String): String =
        input.encodeUri().replace("+", "%20")

    private fun decode(input: String): String = input.decodeUri()
}