package com.lagradost.cloudstream3.actions.temp.fcast

import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// See https://gitlab.com/futo-org/fcast/-/wikis/Protocol-version-1
enum class Opcode(val value: Byte) {
    None(0),
    Play(1),
    Pause(2),
    Resume(3),
    Stop(4),
    Seek(5),
    PlaybackUpdate(6),
    VolumeUpdate(7),
    SetVolume(8),
    PlaybackError(9),
    SetSpeed(10),
    Version(11),
    Ping(12),
    Pong(13);
}

@Serializable
data class PlayMessage(
    @JsonProperty("container") @SerialName("container") val container: String,
    @JsonProperty("url") @SerialName("url") val url: String? = null,
    @JsonProperty("content") @SerialName("content") val content: String? = null,
    @JsonProperty("time") @SerialName("time") val time: Double? = null,
    @JsonProperty("speed") @SerialName("speed") val speed: Double? = null,
    @JsonProperty("headers") @SerialName("headers") val headers: Map<String, String>? = null,
)

data class SeekMessage(
    val time: Double,
)

data class PlaybackUpdateMessage(
    val generationTime: Long,
    val time: Double,
    val duration: Double,
    val state: Int,
    val speed: Double,
)

data class VolumeUpdateMessage(
    val generationTime: Long,
    val volume: Double,
)

data class PlaybackErrorMessage(
    val message: String,
)

data class SetSpeedMessage(
    val speed: Double,
)

data class SetVolumeMessage(
    val volume: Double,
)

data class VersionMessage(
    val version: Long,
)
