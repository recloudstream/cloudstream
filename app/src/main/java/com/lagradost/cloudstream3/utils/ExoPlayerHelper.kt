package com.lagradost.cloudstream3.utils

import com.google.android.exoplayer2.database.ExoDatabaseProvider
import com.google.android.exoplayer2.offline.DownloadManager
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import com.lagradost.cloudstream3.MainActivity
import java.util.concurrent.Executor

object ExoPlayerHelper {
    private val context = MainActivity.mainContext
    val databaseProvider = ExoDatabaseProvider(context)
    val downloadExecutor = Executor { obj: Runnable -> obj.run() }
    val dataSourceFactory = DefaultHttpDataSourceFactory()

    val downloadCache: SimpleCache by lazy {
        SimpleCache(
            context.cacheDir,
            LeastRecentlyUsedCacheEvictor(20 * 1024 * 1024),
            databaseProvider
        )
    }

    val downloadManager: DownloadManager by lazy {
        DownloadManager(context,
            databaseProvider,
            downloadCache,
            dataSourceFactory,
            downloadExecutor)
    }

}