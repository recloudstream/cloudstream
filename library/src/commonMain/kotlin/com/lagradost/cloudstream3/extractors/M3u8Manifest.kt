package com.lagradost.cloudstream3.extractors

//{"auto":"/manifests/movies/15559/1624728920/qDwu5BOsfAwfTmnnjmkmXA/master.m3u8","1080p":"https://vdoc3.sallenes.space/qDwu5BOsfAwfTmnnjmkmXA/1624728920/storage6/movies/the-man-with-the-iron-heart-2017/1080p/index.m3u8","720p":"https://vdoc3.sallenes.space/qDwu5BOsfAwfTmnnjmkmXA/1624728920/storage6/movies/the-man-with-the-iron-heart-2017/720p/index.m3u8","360p":"https://vdoc3.sallenes.space/qDwu5BOsfAwfTmnnjmkmXA/1624728920/storage6/movies/the-man-with-the-iron-heart-2017/360p/index.m3u8","480p":"https://vdoc3.sallenes.space/qDwu5BOsfAwfTmnnjmkmXA/1624728920/storage6/movies/the-man-with-the-iron-heart-2017/480p/index.m3u8"}
object M3u8Manifest {
    // URL = first, QUALITY = second
    fun extractLinks(m3u8Data: String): ArrayList<Pair<String, String>> {
        val data: ArrayList<Pair<String, String>> = ArrayList()
        Regex("\"(.*?)\":\"(.*?)\"").findAll(m3u8Data).forEach {
            var quality = it.groupValues[1].replace("auto", "Auto")
            if (quality != "Auto" && !quality.endsWith('p')) quality += "p"
            val url = it.groupValues[2]
            data.add(Pair(url, quality))
        }
        return data
    }
}