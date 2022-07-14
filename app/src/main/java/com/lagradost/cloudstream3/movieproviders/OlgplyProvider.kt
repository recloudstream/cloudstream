package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.metaproviders.TmdbLink
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.nicehttp.requestCreator
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject

class OlgplyProvider : TmdbProvider() {
    override var mainUrl = "https://olgply.com"
    override val apiName = "Olgply"
    override var name = "Olgply"
    override val instantLinkLoading = true
    override val useMetaLoadResponse = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    private suspend fun loadLinksWithWebView(
        url: String,
//        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val foundVideo = WebViewResolver(
            Regex("""movies4discord""")
        ).resolveUsingWebView(
            requestCreator("GET", url)
        ).first ?: return

        callback.invoke(
            ExtractorLink(
                this.name,
                "Movies4Discord",
                foundVideo.url.toString(),
                "",
                Qualities.Unknown.value
            )
        )
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val mappedData = parseJson<TmdbLink>(data)
        val tmdbId = mappedData.tmdbID ?: return false
        val jsRegex = Regex("""eval\(.*\);""")

        val apiUrl =
            "https://olgply.xyz/${tmdbId}${mappedData.season?.let { "/$it" } ?: ""}${mappedData.episode?.let { "/$it" } ?: ""}"
        val html =
            app.get(apiUrl).text
        val rhino = Context.enter()
        rhino.optimizationLevel = -1
        val scope: Scriptable = rhino.initSafeStandardObjects()
        val documentJs = """
            Plyr = function(){};
            
            hlsPrototype = {
                loadSource(url) {
                    this.url = url;
                }
            };

            function Hls() {};
            Hls.isSupported = function(){return true};
            
            Hls.prototype = hlsPrototype;
            Hls.prototype.constructor = Hls;

            document = {
                "querySelector" : function() {}
            };
        """.trimIndent()

        val foundJs = jsRegex.find(html)?.groupValues?.getOrNull(0) ?: return false
        try {
            rhino.evaluateString(scope, documentJs + foundJs, "JavaScript", 1, null)
        } catch (e: Exception) {
        }

        val hls = scope.get("hls", scope) as? ScriptableObject

        if (hls != null) {
            callback.invoke(
                ExtractorLink(
                    this.name,
                    this.name,
                    hls["url"].toString(),
                    this.mainUrl + "/",
                    Qualities.Unknown.value,
                    headers = mapOf("range" to "bytes=0-"),
                    isM3u8 = true
                )
            )
        } else {
            // Disgraceful fallback, but the js for Movies4Discord refuses to work correctly :(
            loadLinksWithWebView(apiUrl, callback)
        }
        return true
    }
}