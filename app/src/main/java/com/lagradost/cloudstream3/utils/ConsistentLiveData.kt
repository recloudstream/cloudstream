package com.lagradost.cloudstream3.utils

import androidx.annotation.MainThread
import androidx.lifecycle.LiveData
import com.lagradost.cloudstream3.mvvm.Resource

/**
 * This is an atomic LiveData where you can do .value instantly after doing .postValue.
 *
 * The default behavior is a footgun that will cause race conditions, 
 * as we do not really care if it is posted as we only want the latest data (even in the binding).
 *
 * Fuck all that is LiveData, because we want this value to be accessible everywhere instantly.
 * */
open class ConsistentLiveData<T>(initValue : T? = null) : LiveData<T>(initValue) {
    @Volatile private var internalValue : T? = initValue

    override fun getValue(): T? {
        return internalValue
    }

    /** If someone want the old behavior then good for them */
    val postedValue : T? get() = super.getValue()

    public override fun postValue(value : T?) {
        super.postValue(value)
        internalValue = value
    }

    @MainThread
    public override fun setValue(value: T?) {
        super.setValue(value)
        internalValue = value
    }
}

/** Atomic resource livedata, to make it easier to work with resources without local copies */
class ResourceLiveData<T>(initValue : Resource<T>? = null) : ConsistentLiveData<Resource<T>>(initValue) {
    var success
        get() = when(val output = this.value) {
            is Resource.Success<T> -> {
                output.value
            }
            else -> null
        }
        set(value) = this.postValue(value?.let { Resource.Success<T>(it) } )
}
