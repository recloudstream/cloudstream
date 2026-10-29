package com.lagradost.cloudstream3.syncproviders.local

import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.DataStoreHelper
import java.net.URI
import java.util.concurrent.atomic.AtomicReference

data class SharedWatchTransfer(
    val version: Int = 1,
    val title: String = "",
    val apiName: String = "",
    val url: String = "",
    val season: Int? = null,
    val episode: Int? = null,
    val position: Long = 0,
    val duration: Long = 0,
) {
    @com.fasterxml.jackson.annotation.JsonIgnore
    fun isValid(): Boolean = runCatching {
        val address = URI(url)
        version == 1 && title.isNotBlank() && title.length <= 256 && apiName.isNotBlank() && apiName.length <= 128 &&
            title.none(Char::isISOControl) && apiName.none(Char::isISOControl) && url.length <= 8192 &&
            address.scheme in setOf("http", "https") && !address.host.isNullOrBlank() && address.userInfo == null &&
            (season == null || season in 0..10_000) && (episode == null || episode in 1..100_000) &&
            position in 0..604_800_000 && duration in 0..604_800_000 && (duration == 0L || position <= duration)
    }.getOrDefault(false)

    companion object {
        fun decode(bytes: ByteArray): SharedWatchTransfer? = runCatching {
            if (bytes.size > 16_384) return null
            val fields = hashMapOf<String, Any?>()
            com.fasterxml.jackson.core.JsonFactory().createParser(bytes).use { parser ->
                if (parser.nextToken() != com.fasterxml.jackson.core.JsonToken.START_OBJECT) return null
                while (parser.nextToken() != com.fasterxml.jackson.core.JsonToken.END_OBJECT) {
                    if (parser.currentToken != com.fasterxml.jackson.core.JsonToken.FIELD_NAME) return null
                    val name = parser.currentName
                    if (fields.containsKey(name)) return null
                    val token = parser.nextToken()
                    fields[name] = when (name) {
                        "title", "apiName", "url" -> if (token == com.fasterxml.jackson.core.JsonToken.VALUE_STRING) parser.text else return null
                        "version", "position", "duration" -> if (token == com.fasterxml.jackson.core.JsonToken.VALUE_NUMBER_INT) parser.longValue else return null
                        "season", "episode" -> when (token) {
                            com.fasterxml.jackson.core.JsonToken.VALUE_NULL -> null
                            com.fasterxml.jackson.core.JsonToken.VALUE_NUMBER_INT -> parser.longValue.takeIf { it in 0..100_000 }?.toInt() ?: return null
                            else -> return null
                        }
                        else -> return null
                    }
                }
                if (parser.nextToken() != null) return null
            }
            SharedWatchTransfer(version=(fields["version"] as? Long)?.takeIf { it == 1L }?.toInt() ?: return null,
                title=fields["title"] as? String ?: return null,apiName=fields["apiName"] as? String ?: return null,
                url=fields["url"] as? String ?: return null,season=fields["season"] as? Int,episode=fields["episode"] as? Int,
                position=fields["position"] as? Long ?: 0,duration=fields["duration"] as? Long ?: 0).takeIf { it.isValid() }
        }.getOrNull()
    }
}

internal object PendingWatchTransfer {
    private data class Pending(val value: SharedWatchTransfer, val account: String, val expires: Long)
    private val pending = AtomicReference<Pending?>(null)
    fun put(value: SharedWatchTransfer) {
        require(value.isValid())
        pending.set(Pending(value, DataStoreHelper.currentAccount, System.nanoTime() + 600_000_000_000L))
    }
    fun apply(apiName: String, url: String, parentId: Int, movie: Boolean, episodes: List<ResultEpisode>) {
        val entry = pending.get() ?: return
        if (entry.account != DataStoreHelper.currentAccount || System.nanoTime() > entry.expires) { pending.compareAndSet(entry,null); return }
        val value = entry.value
        if (value.apiName != apiName || value.url != url) return
        val episode = episodes.firstOrNull { movie || (it.episode == value.episode && it.season == value.season) } ?: return
        if (!pending.compareAndSet(entry, null)) return
        if (value.position > 0) {
            DataStoreHelper.setViewPos(episode.id, value.position, value.duration)
            DataStoreHelper.setLastWatched(parentId, episode.id, episode.episode, episode.season)
        }
    }
}
