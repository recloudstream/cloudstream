package com.lagradost.cloudstream3.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual val workerDispatcher: CoroutineDispatcher = Dispatchers.IO
internal actual typealias WorkerThread = androidx.annotation.WorkerThread
