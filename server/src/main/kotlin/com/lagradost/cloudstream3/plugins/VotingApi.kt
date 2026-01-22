package com.lagradost.cloudstream3.plugins

import com.lagradost.cloudstream3.app
import java.security.MessageDigest

object VotingApi {
    private const val API_DOMAIN = "https://counterapi.com/api"

    private fun transformUrl(url: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("${url}#funny-salt".toByteArray())
            .fold("") { str, it -> str + "%02x".format(it) }

    private fun getRepository(pluginUrl: String) = pluginUrl
        .split("/")
        .drop(2)
        .take(3)
        .joinToString("-")

    suspend fun getVotes(pluginUrl: String): Int {
        val url =
            "${API_DOMAIN}/cs-${getRepository(pluginUrl)}/vote/${transformUrl(pluginUrl)}?readOnly=true"
        return runCatching {
            app.get(url).parsedSafe<Result>()?.value ?: 0
        }.getOrDefault(0)
    }

    private data class Result(
        val value: Int?
    )
}
