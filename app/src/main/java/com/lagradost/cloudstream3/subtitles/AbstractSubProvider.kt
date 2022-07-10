package com.lagradost.cloudstream3.subtitles

import androidx.annotation.WorkerThread
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleEntity
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleSearch
import com.lagradost.cloudstream3.syncproviders.AuthAPI

interface AbstractSubProvider {
    @WorkerThread
    suspend fun search(query: SubtitleSearch): List<SubtitleEntity>? {
        throw NotImplementedError()
    }

    @WorkerThread
    suspend fun load(data: SubtitleEntity): String? {
        throw NotImplementedError()
    }
}

interface AbstractSubApi : AbstractSubProvider, AuthAPI