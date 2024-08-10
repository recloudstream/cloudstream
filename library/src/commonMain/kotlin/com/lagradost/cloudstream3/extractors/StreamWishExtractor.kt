package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getPacked

class Mwish : StreamwishExt() {
    override val name = "Mwish"
    override val mainUrl = "https://mwish.pro"
}

class Dwish : StreamwishExt() {
    override val name = "Dwish"
    override val mainUrl = "https://dwish.pro"
}

class Ewish : StreamwishExt() {
    override val name = "Embedwish"
    override val mainUrl = "https://embedwish.com"
}

class Wishembed : StreamwishExt() {
    override val name = "Wishembed"
    override val mainUrl = "https://wishembed.pro"
}

class Kswplayer : StreamwishExt() {
    override val name = "Kswplayer"
    override val mainUrl = "https://kswplayer.info"
}

class Wishfast: StreamwishExt() {
    override val name = "Wishfast"
    override val mainUrl = "https://wishfast.top"
}

class Streamwish2 : StreamwishExt() {
    override val mainUrl = "https://streamwish.site"
}

class Sfastwish : StreamwishExt() {
    override val name = "Sfastwish"
    override val mainUrl = "https://sfastwish.com"
}

class Strwish : StreamwishExt() {
    override val name = "Strwish"
    override val mainUrl = "https://strwish.xyz"
}

class Strwish2 : StreamwishExt() {
    override val name = "Strwish"
    override val mainUrl = "https://strwish.com"
}

class Flaswish : StreamwishExt() {
    override val name = "Flaswish"
    override val mainUrl = "https://flaswish.com"
}

class Awish : StreamwishExt() {
    override val name = "Awish"
    override val mainUrl = "https://awish.pro"
}

class Obeywish : StreamwishExt() {
    override val name = "Obeywish"
    override val mainUrl = "https://obeywish.com"
}

class Jodwish : StreamwishExt() {
    override val name = "Jodwish"
    override val mainUrl = "https://jodwish.com"
}

class Swhoi : StreamwishExt() {
    override val name = "Swhoi"
    override val mainUrl = "https://swhoi.com"
}

class Multimovies : StreamwishExt() {
    override val name = "Multimovies"
    override val mainUrl = "https://multimovies.cloud"
}

class UqloadsXyz : StreamwishExt() {
    override val name = "Uqloads"
    override val mainUrl = "https://uqloads.xyz"
}

class Doodporn : StreamwishExt() {
    override val name = "Doodporn"
    override val mainUrl = "https://doodporn.xyz"
}

class Cdnwish : StreamwishExt() {
    override val name = "Cdnwish"
    override val mainUrl = "https://cdnwish.com"
}

class Asnwish : StreamwishExt() {
    override val name = "Asnwish"
    override val mainUrl = "https://asnwish.com"
}

class Nekowish : StreamwishExt() {
    override val name = "Nekowish"
    override val mainUrl = "https://nekowish.my.id"
}

class Nekostream : StreamwishExt() {
    override val name = "Nekostream"
    override val mainUrl = "https://neko-stream.click"
}

class Swdyu : StreamwishExt() {
    override val name = "Swdyu"
    override val mainUrl = "https://swdyu.com"
}

class Wishonly : StreamwishExt() {
    override val name = "Wishonly"
    override val mainUrl = "https://wishonly.site"
}

open class StreamwishExt : ExtractorApi() {
    override val name = "Streamwish"
    override val mainUrl = "https://streamwish.to"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "Accept" to "*/*",
            "Connection" to "keep-alive",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "Origin" to "$mainUrl/",
            "User-Agent" to USER_AGENT
        )
		val response = app.get(getEmbedUrl(url), referer = referer)
        val script = if (!getPacked(response.text).isNullOrEmpty()) {
            getAndUnpack(response.text)
        } else {
            response.document.selectFirst("script:containsData(sources:)")?.data()
        }
        val m3u8 =
            Regex("file:\\s*\"(.*?m3u8.*?)\"").find(script ?: return)?.groupValues?.getOrNull(1)
        M3u8Helper.generateM3u8(
            name,
            m3u8 ?: return,
            mainUrl,
			headers = headers
        ).forEach(callback)		
    }
	
	private fun getEmbedUrl(url: String): String {
        return if (url.contains("/f/")) {
            val videoId = url.substringAfter("/f/")
            "$mainUrl/$videoId"
        } else {
            url
        }
    }

}
