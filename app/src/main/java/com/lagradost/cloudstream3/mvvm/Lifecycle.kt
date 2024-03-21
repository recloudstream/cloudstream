package com.lagradost.cloudstream3.mvvm

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData

/** NOTE: Only one observer at a time per value */
fun <T> LifecycleOwner.observe(liveData: LiveData<T>, action: (t: T) -> Unit) {
    liveData.removeObservers(this)
    liveData.observe(this) { it?.let { t -> action(t) } }
}

/** NOTE: Only one observer at a time per value */
fun <T> LifecycleOwner.observeNullable(liveData: LiveData<T>, action: (t: T) -> Unit) {
    liveData.removeObservers(this)
    liveData.observe(this) { action(it) }
}
