package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class Event<T> {
    private val observers = mutableSetOf<(T) -> Unit>()

    val size: Int get() = observers.size

    operator fun plusAssign(observer: (T) -> Unit) {
        observers.add(observer)
    }

    operator fun minusAssign(observer: (T) -> Unit) {
        observers.remove(observer)
    }

    private val invokeMutex = Mutex()

    operator fun invoke(value: T) {
        ioSafe {
            invokeMutex.withLock { // Can crash otherwise
                for (observer in observers)
                    observer(value)
            }
        }
    }
}
