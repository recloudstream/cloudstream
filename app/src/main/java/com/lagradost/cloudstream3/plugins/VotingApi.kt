package com.lagradost.cloudstream3.plugins

import android.util.Log
import android.widget.Toast
import com.lagradost.cloudstream3.CloudStreamApp.Companion.context
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.R
import java.security.MessageDigest
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.Coroutines.main
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object VotingApi {

    private const val LOGKEY = "VotingApi"
    private const val API_DOMAIN = "https://api.countify.xyz"

    private fun transformUrl(url: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest("${url}#funny-salt".toByteArray())
            .fold("") { str, it -> str + "%02x".format(it) }

    suspend fun SitePlugin.getVotes(): Int = getVotes(url)
    fun SitePlugin.hasVoted(): Boolean = hasVoted(url)
    suspend fun SitePlugin.vote(): Int = vote(url)
    fun SitePlugin.canVote(): Boolean = canVote(this.url)

    private val votesCache = mutableMapOf<String, Int>()

    private suspend fun readVote(pluginUrl: String): Int {
        val id = transformUrl(pluginUrl)
        val url = "$API_DOMAIN/get-total/$id"
        Log.d(LOGKEY, "Requesting GET: $url")
        return app.get(url).parsedSafe<CountifyResult>()?.count ?: 0
    }

    private suspend fun writeVote(pluginUrl: String): Boolean {
        val id = transformUrl(pluginUrl)
        val url = "$API_DOMAIN/increment/$id"
        Log.d(LOGKEY, "Requesting POST: $url")
        return app.post(url, emptyMap<String, String>())
            .parsedSafe<CountifyResult>()?.count != null
    }

    suspend fun getVotes(pluginUrl: String): Int =
        votesCache[pluginUrl] ?: readVote(pluginUrl).also {
            votesCache[pluginUrl] = it
        }

    fun hasVoted(pluginUrl: String) =
        getKey("cs3-votes/${transformUrl(pluginUrl)}") ?: false

    fun canVote(pluginUrl: String): Boolean =
        PluginManager.urlPlugins.contains(pluginUrl)

    private val voteLock = Mutex()

    suspend fun vote(pluginUrl: String): Int {
        voteLock.withLock {
            if (!canVote(pluginUrl)) {
                main {
                    Toast.makeText(
                        context,
                        R.string.extension_install_first,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return getVotes(pluginUrl)
            }

            if (hasVoted(pluginUrl)) {
                main {
                    Toast.makeText(
                        context,
                        R.string.already_voted,
                        Toast.LENGTH_SHORT
                    ).show()
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

    private data class CountifyResult(
        val id: String? = null,
        val count: Int? = null
    )
}
