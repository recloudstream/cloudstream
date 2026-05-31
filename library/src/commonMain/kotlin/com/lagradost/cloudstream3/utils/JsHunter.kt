package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.mvvm.logError
import kotlin.math.pow

// author: https://github.com/daarkdemon
class JsHunter(private val hunterJS: String) {

    /**
     * Detects whether the javascript is H.U.N.T.E.R coded.
     *
     * @return true if it's H.U.N.T.E.R coded.
     */
    fun detect(): Boolean {
        val regex = Regex("eval\\(function\\(h,u,n,t,e,r\\)")
        return regex.containsMatchIn(hunterJS)
    }

    /**
     * Unpack the javascript
     *
     * @return the javascript unhunt or null.
     */
    fun dehunt(): String? {
        try {
            val regex = Regex("""\}\("([^"]+)",[^,]+,\s*"([^"]+)",\s*(\d+),\s*(\d+)""", RegexOption.DOT_MATCHES_ALL)
            val match = regex.find(hunterJS)
            if (match != null && match.groupValues.size == 5) {
                val h = match.groupValues[1]
                val n = match.groupValues[2]
                val t = match.groupValues[3].toInt()
                val e = match.groupValues[4].toInt()
                return hunter(h, n, t, e)
            }
        } catch (e: Exception) {
            logError(e)
        }
        return null
    }

    private fun duf(d: String, e: Int, f: Int = 10): Int {
        val str = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ+/"
        val g = str.toList()
        val h = g.take(e)
        val i = g.take(f)
        val dList = d.reversed().toList()
        var j = 0.0
        for ((c, b) in dList.withIndex()) {
            if (b in h) {
                j += h.indexOf(b) * e.toDouble().pow(c)
            }
        }
        var k = ""
        while (j > 0) {
            k = i[(j % f).toInt()] + k
            j = (j - j % f) / f
        }
        return k.toIntOrNull() ?: 0
    }

    private fun hunter(h: String, n: String, t: Int, e: Int): String {
        var result = ""
        var i = 0
        while (i < h.length) {
            var j = 0
            var s = ""
            while (h[i] != n[e]) {
                s += h[i]
                i++
            }
            while (j < n.length) {
                s = s.replace(n[j], j.digitToChar())
                j++
            }
            result += (duf(s, e) - t).toChar()
            i++
        }
        return result
    }
}