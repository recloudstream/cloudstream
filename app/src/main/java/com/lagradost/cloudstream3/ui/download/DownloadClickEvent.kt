package com.lagradost.cloudstream3.ui.download

import com.lagradost.cloudstream3.utils.VideoDownloadHelper

const val DOWNLOAD_ACTION_PLAY_FILE = 0
const val DOWNLOAD_ACTION_DELETE_FILE = 1
const val DOWNLOAD_ACTION_PAUSE_DOWNLOAD = 2
const val DOWNLOAD_ACTION_RESUME_DOWNLOAD = 3
const val DOWNLOAD_ACTION_LONG_CLICK = 4
const val DOWNLOAD_ACTION_DOWNLOAD = 5

data class DownloadClickEvent(val action: Int, val data: VideoDownloadHelper.DownloadEpisodeCached)
