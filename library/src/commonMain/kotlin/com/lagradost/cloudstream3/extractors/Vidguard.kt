package com.lagradost.cloudstream3.extractors

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeJSON
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import java.util.Base64

class Vidguardto1 : Vidguardto() {
    override val mainUrl = "https://bembed.net"
}

class Vidguardto2 : Vidguardto() {
    override val mainUrl = "https://listeamed.net"
}

class Vidguardto3 : Vidguardto() {
    override val mainUrl = "https://vgfplay.com"
}

open class Vidguardto : ExtractorApi() {
    override val name = "Vidguard"
    override val mainUrl = "https://vidguard.to"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val embedUrl = url.replace("/d/", "/e/")
        val res = app.get(embedUrl)
        val resc = res.document.select("script:containsData(eval)").firstOrNull()?.data()
        resc?.let {
            val jsonStr2 = AppUtils.parseJson<SvgObject>(runJS2(it))
            val watchlink = sigDecode(jsonStr2.stream)

            callback.invoke(
                ExtractorLink(
                    this.name,
                    name,
                    watchlink,
                    this.mainUrl,
                    Qualities.Unknown.value,
                    INFER_TYPE
                )
            )
        }
    }

    private fun sigDecode(url: String): String {
        val sig = url.split("sig=")[1].split("&")[0]
        var t = ""
        for (v in sig.chunked(2)) {
            val byteValue = Integer.parseInt(v, 16) xor 2
            t += byteValue.toChar()
        }
        val padding = when (t.length % 4) {
            2 -> "=="
            3 -> "="
            else -> ""
        }
        val decoded = Base64.getDecoder().decode((t + padding).toByteArray(Charsets.UTF_8))
        t = String(decoded).dropLast(5).reversed()
        val charArray = t.toCharArray()
        for (i in 0 until charArray.size - 1 step 2) {
            val temp = charArray[i]
            charArray[i] = charArray[i + 1]
            charArray[i + 1] = temp
        }
        val modifiedSig = String(charArray).dropLast(5)
        return url.replace(sig, modifiedSig)
    }

    private fun runJS2(hideMyHtmlContent: String): String {
        var result = ""
        val r = Runnable {
            Log.d("runJS", "start")
            val rhino = Context.enter()
            rhino.initSafeStandardObjects()
            rhino.optimizationLevel = -1
            val scope: Scriptable = rhino.initSafeStandardObjects()
            scope.put("window", scope, scope)
            try {
                Log.d("runJS", "Executing JavaScript: $hideMyHtmlContent")
                rhino.evaluateString(
                    scope,
                    hideMyHtmlContent,
                    "JavaScript",
                    1,
                    null
                )
                val svgObject = scope.get("svg", scope)
                result = if (svgObject is NativeObject) {
                    NativeJSON.stringify(Context.getCurrentContext(), scope, svgObject, null, null)
                        .toString()
                } else {
                    Context.toString(svgObject)
                }
                Log.d("runJS", "Result: $result")
            } catch (e: Exception) {
                Log.e("runJS", "Error executing JavaScript: ${e.message}")
            } finally {
                Context.exit()
            }
        }
        val t = Thread(ThreadGroup("A"), r, "thread_rhino", 2000000)// StackSize 2Mb: Run in a thread because rhino requires more stack size for large scripts.
        t.start();
        t.join()
        t.interrupt()
        return result
    }

    data class SvgObject(
        val stream: String,
        val hash: String
    )
}
