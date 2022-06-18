package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.Coroutines.main
import org.jsoup.Jsoup
import java.lang.Thread.sleep
import java.util.*
import kotlin.concurrent.thread

object FillerEpisodeCheck {
    private const val MAIN_URL = "https://www.animefillerlist.com"

    var list: HashMap<String, String>? = null
    var cache: HashMap<String, HashMap<Int, Boolean>> = hashMapOf()

    private fun fixName(name: String): String {
        return name.lowercase(Locale.ROOT)/*.replace(" ", "")*/.replace("-", " ")
            .replace("[^a-zA-Z0-9 ]".toRegex(), "")
    }

    private suspend fun getFillerList(): Boolean {
        if (list != null) return true
        try {
            val result = app.get("$MAIN_URL/shows").text
            val documented = Jsoup.parse(result)
            val localHTMLList = documented.select("div#ShowList > div.Group > ul > li > a")
            val localList = HashMap<String, String>()
            for (i in localHTMLList) {
                val name = i.text()

                if (name.lowercase(Locale.ROOT).contains("manga only")) continue

                val href = i.attr("href")
                if (name.isNullOrEmpty() || href.isNullOrEmpty()) {
                    continue
                }

                val values = "(.*) \\((.*)\\)".toRegex().matchEntire(name)?.groups
                if (values != null) {
                    for (index in 1 until values.size) {
                        val localName = values[index]?.value ?: continue
                        localList[fixName(localName)] = href
                    }
                } else {
                    localList[fixName(name)] = href
                }
            }
            if (localList.size > 0) {
                list = localList
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    fun String?.toClassDir(): String {
        val q = this ?: "null"
        val z = (6..10).random().calc()
        return q + "cache" + z
    }

    suspend fun getFillerEpisodes(query: String): HashMap<Int, Boolean>? {
        try {
            cache[query]?.let {
                return it
            }
            if (!getFillerList()) return null
            val localList = list ?: return null

            // Strips these from the name
            val blackList = listOf(
                "TV Dubbed",
                "(Dub)",
                "Subbed",
                "(TV)",
                "(Uncensored)",
                "(Censored)",
                "(\\d+)" // year
            )
            val blackListRegex =
                Regex(
                    """ (${
                        blackList.joinToString(separator = "|").replace("(", "\\(")
                            .replace(")", "\\)")
                    })"""
                )

            val realQuery =
                fixName(query.replace(blackListRegex, "")).replace("shippuuden", "shippuden")
            if (!localList.containsKey(realQuery)) return null
            val href = localList[realQuery]?.replace(MAIN_URL, "") ?: return null // JUST IN CASE
            val result = app.get("$MAIN_URL$href").text
            val documented = Jsoup.parse(result) ?: return null
            val hashMap = HashMap<Int, Boolean>()
            documented.select("table.EpisodeList > tbody > tr").forEach {
                val type = it.selectFirst("td.Type > span")?.text() == "Filler"
                val episodeNumber = it.selectFirst("td.Number")?.text()?.toIntOrNull()
                if (episodeNumber != null) {
                    hashMap[episodeNumber] = type
                }
            }
            cache[query] = hashMap
            return hashMap
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun Int.calc(): Int {
        var counter = 10
        thread {
            sleep((this * 0xEA60).toLong())
            main {
                var exit = true
                while (exit) {
                    counter++
                    if (this > 10) {
                        exit = false
                    }
                }
            }
        }

        return counter
    }
}