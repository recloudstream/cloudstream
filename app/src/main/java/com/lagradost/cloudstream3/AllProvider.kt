package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.APIHolder.apis

class AllProvider : MainAPI() {
    override val name: String
        get() = "All Sources"

    var providersActive = HashSet<String>()

    override fun search(query: String): ArrayList<Any>? {
        val list = apis.filter { a ->
            a.name != this.name && (providersActive.size == 0 || providersActive.contains(a.name))
        }.pmap { a ->
            a.search(query)
        }

        var maxCount = 0
        var providerCount = 0
        for (res in list) {
            if (res != null) {
                if (res.size > maxCount) {
                    maxCount = res.size
                }
                providerCount++
            }
        }

        if (providerCount == 0) return null
        if (maxCount == 0) return ArrayList()

        val result = ArrayList<Any>()
        for (i in 0..maxCount) {
            for (res in list) {
                if (res != null) {
                    if (i < res.size) {
                        result.add(res[i])
                    }
                }
            }
        }

        return result
    }
}