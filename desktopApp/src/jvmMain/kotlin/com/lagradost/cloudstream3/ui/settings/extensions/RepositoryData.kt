package com.lagradost.cloudstream3.ui.settings.extensions

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/** Same binary shape as the Android app's RepositoryData, used by extensions that manage repositories */
@JsonIgnoreProperties(ignoreUnknown = true)
data class RepositoryData(
    val iconUrl: String?,
    val name: String,
    val url: String,
    val description: String? = null,
) {
    constructor(name: String, url: String) : this(iconUrl = null, name = name, url = url, description = null)

    constructor(iconUrl: String?, name: String, url: String) : this(
        iconUrl = iconUrl,
        name = name,
        url = url,
        description = null
    )
}
