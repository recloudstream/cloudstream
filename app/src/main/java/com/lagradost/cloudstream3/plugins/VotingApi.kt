package com.lagradost.cloudstream3.plugins

import android.util.Log
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import java.security.MessageDigest
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe

object VotingApi { // please do not cheat the votes lol
    private const val LOGKEY = "VotingApi"

    enum class VoteType(val value: Int) {
        UPVOTE(1),
        DOWNVOTE(-1),
        NONE(0)
    }

    private val apiDomain = "https://api.countapi.xyz"

    private fun transformUrl(url: String): String = // dont touch or all votes get reset
            MessageDigest
                .getInstance("SHA-256")
                .digest("${url}#funny-salt".toByteArray())
                .fold("") { str, it -> str + "%02x".format(it) }

    suspend fun SitePlugin.getVotes(): Int {
        return getVotes(url)
    }

    suspend fun SitePlugin.vote(requestType: VoteType): Int {
        return vote(url, requestType)
    }

    fun SitePlugin.getVoteType(): VoteType {
        if (repositoryUrl == null) return VoteType.NONE
        return getVoteType(url)
    }

    // Plugin url to Int
    private val votesCache = mutableMapOf<String, Int>()

    suspend fun getVotes(pluginUrl: String): Int {
        val url = "${apiDomain}/get/cs3-votes/${transformUrl(pluginUrl)}"
        Log.d(LOGKEY, "Requesting: $url")
        return votesCache[pluginUrl] ?: app.get(url).parsedSafe<Result>()?.value?.also {
            votesCache[pluginUrl] = it
        } ?: (0.also {
          ioSafe {
              createBucket(pluginUrl)
          }
        })
    }

    fun getVoteType(pluginUrl: String): VoteType {
        return getKey("cs3-votes/${transformUrl(pluginUrl)}") ?: VoteType.NONE
    }

    private suspend fun createBucket(pluginUrl: String) {
        val url = "${apiDomain}/create?namespace=cs3-votes&key=${transformUrl(pluginUrl)}&value=0&update_lowerbound=-2&update_upperbound=2&enable_reset=0"
        Log.d(LOGKEY, "Requesting: $url")
        app.get(url)
    }

    suspend fun vote(pluginUrl: String, requestType: VoteType): Int {
        val savedType: VoteType = getKey("cs3-votes/${transformUrl(pluginUrl)}") ?: VoteType.NONE
        var newType: VoteType = requestType
        var changeValue = 0
        if (requestType == savedType) {
            newType = VoteType.NONE
            changeValue = -requestType.value
        } else if (savedType == VoteType.NONE) {
            changeValue = requestType.value
        } else if (savedType != requestType) {
            changeValue = -savedType.value + requestType.value
        }
        val url = "${apiDomain}/update/cs3-votes/${transformUrl(pluginUrl)}?amount=${changeValue}"
        Log.d(LOGKEY, "Requesting: $url")
        val res = app.get(url).parsedSafe<Result>()?.value
        if (res != null) {
            setKey("cs3-votes/${transformUrl(pluginUrl)}", newType)
            votesCache[pluginUrl] = res
        }
        return res ?: 0
    }

    private data class Result(
        val value: Int?
    )
}