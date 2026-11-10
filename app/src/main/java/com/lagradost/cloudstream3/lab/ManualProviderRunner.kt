package com.lagradost.cloudstream3.lab

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import java.util.Collections

/** Invokes only the chosen public provider method. No playback or automatic test sequence. */
object ManualProviderRunner {
    enum class Method(val title: String) {
        MAIN_PAGE("getMainPage(page, request)"),
        SEARCH("search(query)"),
        PAGED_SEARCH("search(query, page)"),
        QUICK_SEARCH("quickSearch(query)"),
        LOAD("load(url)"),
        LOAD_LINKS("loadLinks(data, isCasting)")
    }

    data class Request(
        val method: Method,
        val value: String = "",
        val page: Int = 1,
        val mainPageName: String = "",
        val horizontalImages: Boolean = false,
        val isCasting: Boolean = false,
    )

    suspend fun run(provider: MainAPI, request: Request): Any? {
        require(request.page > 0) { "Page must be a positive integer." }
        if (request.method != Method.MAIN_PAGE) {
            require(request.value.isNotBlank()) { "Please enter the required input." }
        }
        return when (request.method) {
            Method.MAIN_PAGE -> provider.getMainPage(request.page, MainPageRequest(
                request.mainPageName, request.value, request.horizontalImages
            ))
            Method.SEARCH -> provider.search(request.value)
            Method.PAGED_SEARCH -> provider.search(request.value, request.page)
            Method.QUICK_SEARCH -> provider.quickSearch(request.value)
            Method.LOAD -> provider.load(request.value)
            Method.LOAD_LINKS -> {
                val links = Collections.synchronizedList(mutableListOf<ExtractorLink>())
                val subtitles = Collections.synchronizedList(mutableListOf<SubtitleFile>())
                val success = provider.loadLinks(request.value, request.isCasting,
                    { subtitles.add(it) }, { links.add(it) })
                mapOf("success" to success, "links" to synchronized(links) { links.toList() },
                    "subtitles" to synchronized(subtitles) { subtitles.toList() })
            }
        }
    }
}
