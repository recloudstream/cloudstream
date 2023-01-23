package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.mvvm.logError
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.math.pow

//author: https://github.com/daarkdemon
class JsHunter(private val hunterJS: String) {

    /**
     * Detects whether the javascript is H.U.N.T.E.R coded.
     *
     * @return true if it's H.U.N.T.E.R coded.
     */
    fun detect(): Boolean {
        val p = Pattern.compile("eval\\(function\\(h,u,n,t,e,r\\)")
        val searchResults = p.matcher(hunterJS)
        return searchResults.find()
    }

    /**
     * Unpack the javascript
     *
     * @return the javascript unhunt or null.
     */

    fun dehunt(): String? {
        try {
            val p: Pattern =
                Pattern.compile(
                    """}\("([^"]+)",[^,]+,\s*"([^"]+)",\s*(\d+),\s*(\d+)""",
                    Pattern.DOTALL
                )
            val searchResults: Matcher = p.matcher(hunterJS)
            if (searchResults.find() && searchResults.groupCount() == 4) {
                val h = searchResults.group(1)!!.toString()
                val n = searchResults.group(2)!!.toString()
                val t = searchResults.group(3)!!.toInt()
                val e = searchResults.group(4)!!.toInt()
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