package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app

class Evoload1 : Evoload() {
    override var mainUrl = "https://evoload.io"
}

open class Evoload : ExtractorApi() {
    override val name: String = "Evoload"
    override val mainUrl: String = "https://www.evoload.io"
    //private val srcRegex = Regex("""video .*src="(.*)""""")  // would be possible to use the parse and find src attribute
    override val requiresReferer = true



    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val id = url.replace("https://evoload.io/e/", "")  // wanted media id
        val csrv_token = app.get("https://csrv.evosrv.com/captcha?m412548=").text  // whatever that is
        val captchaPass = app.get("https://cd2.evosrv.com/html/jsx/e.jsx").text.take(300).split("captcha_pass = '")[1].split("\'")[0]  //extract the captcha pass from the js response (located in the 300 first chars)
        val payload = mapOf("code" to id, "csrv_token" to csrv_token, "pass" to captchaPass)
        val r = app.post("https://evoload.io/SecurePlayer", data=(payload)).text
        val link = Regex("src\":\"(.*?)\"").find(r)?.destructured?.component1() ?: return listOf()
        return listOf(
            ExtractorLink(
                name,
                name,
                link,
                url,
                Qualities.Unknown.value,
            )
        )
    }
}
