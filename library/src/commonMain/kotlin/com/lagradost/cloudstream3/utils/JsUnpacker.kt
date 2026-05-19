package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.mvvm.logError
import kotlin.math.pow

// https://github.com/cylonu87/JsUnpacker
class JsUnpacker(packedJS: String?) {
    private var packedJS: String? = null

    /**
     * Detects whether the javascript is P.A.C.K.E.R. coded.
     *
     * @return true if it's P.A.C.K.E.R. coded.
     */
    fun detect(): Boolean {
        val js = packedJS!!.replace(" ", "")
        return Regex("eval\\(function\\(p,a,c,k,e,[rd]").containsMatchIn(js)
    }

    /**
     * Unpack the javascript
     *
     * @return the javascript unpacked or null.
     */
    fun unpack(): String? {
        val js = packedJS ?: return null
        try {
            val match = Regex(
                """\}\s*\('(.*)',\s*(.*?),\s*(\d+),\s*'(.*?)'\.split\('\|'\)""",
                RegexOption.DOT_MATCHES_ALL
            ).find(js)
            if (match != null && match.groupValues.size == 5) {
                val payload = match.groupValues[1].replace("\\'", "'")
                val radixStr = match.groupValues[2]
                val countStr = match.groupValues[3]
                val symtab = match.groupValues[4].split("|").toTypedArray()
                var radix = 36
                var count = 0
                try {
                    radix = radixStr.toIntOrNull() ?: radix
                } catch (_: Exception) {
                }
                try {
                    count = countStr.toIntOrNull() ?: 0
                } catch (_: Exception) {
                }
                if (symtab.size != count) {
                    throw Exception("Unknown p.a.c.k.e.r. encoding")
                }
                val unbase = Unbase(radix)
                val wordRegex = Regex("""\b[a-zA-Z0-9_]+\b""")
                val decoded = StringBuilder(payload)
                var replaceOffset = 0
                wordRegex.findAll(payload).forEach { wordMatch ->
                    val word = wordMatch.value
                    val x = unbase.unbase(word)
                    val value = if (x in symtab.indices) symtab[x] else null
                    if (!value.isNullOrEmpty()) {
                        decoded.replace(
                            wordMatch.range.first + replaceOffset,
                            wordMatch.range.last + 1 + replaceOffset,
                            value
                        )
                        replaceOffset += value.length - word.length
                    }
                }
                return decoded.toString()
            }
        } catch (e: Exception) {
            logError(e)
        }
        return null
    }

    private inner class Unbase(private val radix: Int) {
        private val ALPHABET_62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private val ALPHABET_95 =
            " !\"#$%&\\'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\\\]^_`abcdefghijklmnopqrstuvwxyz{|}~"
        private var alphabet: String? = null
        private var dictionary: HashMap<String, Int>? = null

        fun unbase(str: String): Int {
            var ret = 0
            if (alphabet == null) {
                ret = str.toInt(radix)
            } else {
                val tmp = StringBuilder(str).reverse().toString()
                for (i in tmp.indices) {
                    ret += (radix.toDouble().pow(i.toDouble()) * dictionary!![tmp.substring(i, i + 1)]!!).toInt()
                }
            }
            return ret
        }

        init {
            if (radix > 36) {
                when {
                    radix < 62 -> alphabet = ALPHABET_62.substring(0, radix)
                    radix in 63..94 -> alphabet = ALPHABET_95.substring(0, radix)
                    radix == 62 -> alphabet = ALPHABET_62
                    radix == 95 -> alphabet = ALPHABET_95
                }
                dictionary = HashMap(95)
                for (i in 0 until alphabet!!.length) {
                    dictionary!![alphabet!!.substring(i, i + 1)] = i
                }
            }
        }
    }

    /**
     * @param packedJS javascript P.A.C.K.E.R. coded.
     */
    init {
        this.packedJS = packedJS
    }

    companion object {
        val c = listOf(
            0x63, 0x6f, 0x6d, 0x2e, 0x67, 0x6f, 0x6f, 0x67, 0x6c, 0x65, 0x2e, 0x61, 0x6e, 0x64,
            0x72, 0x6f, 0x69, 0x64, 0x2e, 0x67, 0x6d, 0x73, 0x2e, 0x61, 0x64, 0x73, 0x2e, 0x4d,
            0x6f, 0x62, 0x69, 0x6c, 0x65, 0x41, 0x64, 0x73
        )
        val z = listOf(
            0x63, 0x6f, 0x6d, 0x2e, 0x66, 0x61, 0x63, 0x65, 0x62, 0x6f, 0x6f, 0x6b, 0x2e, 0x61,
            0x64, 0x73, 0x2e, 0x41, 0x64
        )

        fun String.load(): String? {
            return try {
                var load = this
                for (q in c.indices) {
                    if (c[q % 4] > 270) {
                        load += c[q % 3]
                    } else {
                        load += c[q].toChar()
                    }
                }
                Class.forName(load.substring(load.length - c.size, load.length)).name
            } catch (_: Exception) {
                try {
                    var f = c[2].toChar().toString()
                    for (w in z.indices) {
                        f += z[w].toChar()
                    }
                    Class.forName(f.substring(0b001, f.length)).name
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}