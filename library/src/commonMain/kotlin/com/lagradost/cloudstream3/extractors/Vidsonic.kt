package com.lagradost.cloudstream3.extractors

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink

class Vidsonic() : ExtractorApi() {
    override val name: String = "Vidsonic"
    override val mainUrl: String = "https://vidsonic.net"
    override val requiresReferer: Boolean = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // Extracted JavaScript code that decodes the encrypted m3u8 stream URL:
        //
        // const _0x1 = '3363616238|3638666534|6264323565|3666616366|6636333662|6230626339|30613d3564|6d26743130|6b74693170|336563793d|64695f656c|6966263634|3332363033|3737313d73|6572697078|6526333d64|695f726576|7265733f38|75336d2e72|657473616d|2f7431306b|7469317033|6563792f38|392f657275|6365732f74|656e2e6369|6e6f736469|762e31302d|73752d7473|2f2f3a7370|747468';
        // const _0x2 = function(_0x3) {
        //    const _0x4 = _0x3.split('|').join('');
        //    let _0x5 = '';
        //     for (let _0x6 = 0; _0x6 < _0x4.length; _0x6 += 2) {
        //       _0x5 += String.fromCharCode(parseInt(_0x4.substr(_0x6, 2), 16));
        //     }
        //     return _0x5.split('').reverse().join('');
        // };
        // const _0x7 = _0x2(_0x1); <-- now contains the stream URL

        val response = app.get(url).text
        val encodedStreamUrl = response
            .substringAfter("const _0x1 = ")
            .substringBefore(";")
            .replace("'", "")

        // (improved) Java implementation of the JavaScript code from above
        val streamUrl = encodedStreamUrl
            .replace("|", "")
            // always two base16 digits together build one ASCII char
            .chunked(2)
            .map {
                Integer.parseInt(it, 16).toChar()
            }
            .joinToString("")
            .reversed()

        callback.invoke(
            newExtractorLink(
                source = name,
                name = name,
                url = streamUrl,
                type = ExtractorLinkType.M3U8
            )
        )
    }
}