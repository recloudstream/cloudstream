package com.lagradost.cloudstream3.plugins

import android.util.Log
import android.widget.Toast
import com.lagradost.cloudstream3.AcraApplication.Companion.context
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.R
import java.security.MessageDigest
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.Coroutines.main
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object VotingApi { // please do not cheat the votes lol
    private const val LOGKEY = "VotingApi"

    private const val apiDomain = "https://counterapi.com/api"

    private fun transformUrl(url: String): String = // dont touch or all votes get reset
        MessageDigest
            .getInstance("SHA-256")
            .digest("${url}#funny-salt".toByteArray())
            .fold("") { str, it -> str + "%02x".format(it) }

    suspend fun SitePlugin.getVotes(): Int {
        return getVotes(url)
    }

    fun SitePlugin.hasVoted(): Boolean {
        return hasVoted(url)
    }

    suspend fun SitePlugin.vote(): Int {
        return vote(url)
    }

    fun SitePlugin.canVote(): Boolean {
        return canVote(this.url)
    }

    // Plugin url to Int
    private val votesCache = mutableMapOf<String, Int>()

    private fun getRepository(pluginUrl: String) = pluginUrl
        .split("/")
        .drop(2)
        .take(3)
        .joinToString("-")

    private suspend fun readVote(pluginUrl: String): Int {
        var url = "${apiDomain}/cs-${getRepository(pluginUrl)}/vote/${transformUrl(pluginUrl)}?readOnly=true"
        Log.d(LOGKEY, "Requesting: $url")
        return app.get(url).parsedSafe<Result>()?.value ?: 0
    }

    private suspend fun writeVote(pluginUrl: String): Boolean {
        var url = "${apiDomain}/cs-${getRepository(pluginUrl)}/vote/${transformUrl(pluginUrl)}"
        Log.d(LOGKEY, "Requesting: $url")
        return app.get(url).parsedSafe<Result>()?.value != null
    }

    suspend fun getVotes(pluginUrl: String): Int =
            votesCache[pluginUrl] ?: readVote(pluginUrl).also {
                votesCache[pluginUrl] = it
            }

    fun hasVoted(pluginUrl: String) =
        getKey("cs3-votes/${transformUrl(pluginUrl)}") ?: false

    fun canVote(pluginUrl: String): Boolean {
        if (!PluginManager.urlPlugins.contains(pluginUrl)) return false
        return true
    }

    private val voteLock = Mutex()
    suspend fun vote(pluginUrl: String): Int {
        // Prevent multiple requests at the same time.
        voteLock.withLock {
            if (!canVote(pluginUrl)) {
                main {
                    Toast.makeText(context, R.string.extension_install_first, Toast.LENGTH_SHORT)
                        .show()
                }
                return getVotes(pluginUrl)
            }

            if (hasVoted(pluginUrl)) {
                main {
                    Toast.makeText(context, R.string.already_voted, Toast.LENGTH_SHORT)
                        .show()
                }
                return getVotes(pluginUrl)
            }


            if (writeVote(pluginUrl)) {
                setKey("cs3-votes/${transformUrl(pluginUrl)}", true)
                votesCache[pluginUrl] = votesCache[pluginUrl]?.plus(1) ?: 1
            }

            return getVotes(pluginUrl)
        }
    }

    private data class Result(
        val value: Int?
    )
}