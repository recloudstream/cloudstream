package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.mvvm.logError
import java.lang.Exception
import java.lang.StringBuilder
import java.util.HashMap
import java.util.regex.Pattern
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
        val p = Pattern.compile("eval\\(function\\(p,a,c,k,e,[rd]")
        val m = p.matcher(js)
        return m.find()
    }

    /**
     * Unpack the javascript
     *
     * @return the javascript unpacked or null.
     */
    fun unpack(): String? {
        val js = packedJS
        try {
            var p =
                Pattern.compile("""}\s*\('(.*)',\s*(.*?),\s*(\d+),\s*'(.*?)'\.split\('\|'\)""", Pattern.DOTALL)
            var m = p.matcher(js)
            if (m.find() && m.groupCount() == 4) {
                val payload = m.group(1).replace("\\'", "'")
                val radixStr = m.group(2)
                val countStr = m.group(3)
                val symtab = m.group(4).split("\\|".toRegex()).toTypedArray()
                var radix = 36
                var count = 0
                try {
                    radix = radixStr.toInt()
                } catch (e: Exception) {
                }
                try {
                    count = countStr.toInt()
                } catch (e: Exception) {
                }
                if (symtab.size != count) {
                    throw Exception("Unknown p.a.c.k.e.r. encoding")
                }
                val unbase = Unbase(radix)
                p = Pattern.compile("\\b\\w+\\b")
                m = p.matcher(payload)
                val decoded = StringBuilder(payload)
                var replaceOffset = 0
                while (m.find()) {
                    val word = m.group(0)
                    val x = unbase.unbase(word)
                    var value: String? = null
                    if (x < symtab.size) {
                        value = symtab[x]
                    }
                    if (value != null && value.isNotEmpty()) {
                        decoded.replace(m.start() + replaceOffset, m.end() + replaceOffset, value)
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
                    radix < 62 -> {
                        alphabet = ALPHABET_62.substring(0, radix)
                    }
                    radix in 63..94 -> {
                        alphabet = ALPHABET_95.substring(0, radix)
                    }
                    radix == 62 -> {
                        alphabet = ALPHABET_62
                    }
                    radix == 95 -> {
                        alphabet = ALPHABET_95
                    }
                }
                dictionary = HashMap(95)
                for (i in 0 until alphabet!!.length) {
                    dictionary!![alphabet!!.substring(i, i + 1)] = i
                }
            }
        }
    }
    /**
     * @param  packedJS javascript P.A.C.K.E.R. coded.
     */
    init {
        this.packedJS = packedJS
    }
}