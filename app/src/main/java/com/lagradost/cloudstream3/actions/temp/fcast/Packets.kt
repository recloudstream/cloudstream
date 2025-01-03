package com.lagradost.cloudstream3.actions.temp.fcast

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


data class PlayMessage(
    val container: String,
    val url: String? = null,
    val content: String? = null,
    val time: Double? = null,
    val speed: Double? = null,
    val headers: Map<String, String>? = null
)

data class SeekMessage(
    val time: Double
)

data class PlaybackUpdateMessage(
    val generationTime: Long,
    val time: Double,
    val duration: Double,
    val state: Int,
    val speed: Double
)

data class VolumeUpdateMessage(
    val generationTime: Long,
    val volume: Double
)

data class PlaybackErrorMessage(
    val message: String
)

data class SetSpeedMessage(
    val speed: Double
)

data class SetVolumeMessage(
    val volume: Double
)

data class VersionMessage(
    val version: Long
)
