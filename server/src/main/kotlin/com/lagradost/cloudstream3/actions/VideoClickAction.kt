package com.lagradost.cloudstream3.actions

abstract class VideoClickAction(
    val name: String
) {
    var sourcePlugin: String? = null
}

object VideoClickActionHolder {
    val allVideoClickActions: MutableList<VideoClickAction> = mutableListOf()
}
