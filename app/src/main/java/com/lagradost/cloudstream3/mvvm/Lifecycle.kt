package com.lagradost.cloudstream3.mvvm

import android.view.View
import androidx.activity.ComponentActivity
import androidx.core.view.doOnAttach
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.viewbinding.ViewBinding
import com.lagradost.cloudstream3.ui.BaseFragment

/** NOTE: Only one observer at a time per value */
fun <T> ComponentActivity.observe(liveData: LiveData<T>, action: (T) -> Unit) {
    observeNullable(liveData) { t -> t?.run(action) }
}

/** NOTE: Only one observer at a time per value */
fun <T> ComponentActivity.observeNullable(liveData: LiveData<T>, action: (T?) -> Unit) {
    liveData.removeObservers(this)
    liveData.observe(this, action)
}

/** NOTE: Only one observer at a time per value */
fun <T, V : ViewBinding> BaseFragment<V>.observe(liveData: LiveData<T>, action: (T) -> Unit) {
    observeNullable(liveData) { t -> t?.run(action) }
}

/**
 * Attaches an observable to the root binding, instead of the fragment. This is more efficient as
 * it will not call observe if the view is in the background.
 *
 * NOTE: Only one observer at a time per value
 * */
fun <T, V : ViewBinding> BaseFragment<V>.observeNullable(
    liveData: LiveData<T>, action: (T?) -> Unit
) {
    val root = this.binding?.root
    if (root == null) {
        liveData.removeObservers(this)
        liveData.observe(this, action)
    } else {
        root.doOnAttach { view ->
            // On attach should make findViewTreeLifecycleOwner non-null, but use "this" just in case
            val owner: LifecycleOwner = view.findViewTreeLifecycleOwner() ?: this@observeNullable
            liveData.removeObservers(owner)
            liveData.observe(owner, action)
        }
    }
}

/** NOTE: Only one observer at a time per value */
fun <T> View.observe(liveData: LiveData<T>, action: (T) -> Unit) {
    observeNullable(liveData) { t -> t?.run(action) }
}

/** NOTE: Only one observer at a time per value */
fun <T> View.observeNullable(liveData: LiveData<T>, action: (T?) -> Unit) {
    doOnAttach { view ->
        // On attach should make findViewTreeLifecycleOwner non-null
        val owner: LifecycleOwner? = view.findViewTreeLifecycleOwner()
        if(owner == null) {
            debugException { "Expected non-null findViewTreeLifecycleOwner" }
            return@doOnAttach
        }
        liveData.removeObservers(owner)
        liveData.observe(owner, action)
    }
}