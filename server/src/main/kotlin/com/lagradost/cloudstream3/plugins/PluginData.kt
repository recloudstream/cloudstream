package com.lagradost.cloudstream3.plugins

data class PluginData(
    val internalName: String,
    val url: String? = null,
    val isOnline: Boolean = true,
    val filePath: String,
    val version: Int,
)
