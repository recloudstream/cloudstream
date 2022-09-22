package com.lagradost.cloudstream3.plugins

import android.util.Log
import android.widget.Toast
import com.lagradost.cloudstream3.AcraApplication.Companion.context
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.R
import java.security.MessageDigest
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
        return getVoteType(url)
    }

    fun SitePlugin.canVote(): Boolean {
        return canVote(this.url)
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
        val url =
            "${apiDomain}/create?namespace=cs3-votes&key=${transformUrl(pluginUrl)}&value=0&update_lowerbound=-2&update_upperbound=2&enable_reset=0"
        Log.d(LOGKEY, "Requesting: $url")
        app.get(url)
    }

    fun canVote(pluginUrl: String): Boolean {
        if (!PluginManager.urlPlugins.contains(pluginUrl)) return false
        return true
    }

    private val voteLock = Mutex()
    suspend fun vote(pluginUrl: String, requestType: VoteType): Int {
        // Prevent multiple requests at the same time.
        voteLock.withLock {
            if (!canVote(pluginUrl)) {
                main {
                    Toast.makeText(context, R.string.extension_install_first, Toast.LENGTH_SHORT)
                        .show()
                }
                return getVotes(pluginUrl)
            }

            val savedType: VoteType =
                getKey("cs3-votes/${transformUrl(pluginUrl)}") ?: VoteType.NONE

            val newType = if (requestType == savedType) VoteType.NONE else requestType
            val changeValue = if (requestType == savedType) {
                -requestType.value
            } else if (savedType == VoteType.NONE) {
                requestType.value
            } else if (savedType != requestType) {
                -savedType.value + requestType.value
            } else 0

            // Pre-emptively set vote key
            setKey("cs3-votes/${transformUrl(pluginUrl)}", newType)

            val url =
                "${apiDomain}/update/cs3-votes/${transformUrl(pluginUrl)}?amount=${changeValue}"
            Log.d(LOGKEY, "Requesting: $url")
            val res = app.get(url).parsedSafe<Result>()?.value

            if (res == null) {
                // "Refund" key if the response is invalid
                setKey("cs3-votes/${transformUrl(pluginUrl)}", savedType)
            } else {
                votesCache[pluginUrl] = res
            }
            return res ?: 0
        }
    }

    private data class Result(
        val value: Int?
    )
}