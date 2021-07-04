package com.lagradost.cloudstream3.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

object Coroutines {
    fun main(work: suspend (() -> Unit)) : Job {
        return CoroutineScope(Dispatchers.Main).launch {
            work()
        }
    }
}