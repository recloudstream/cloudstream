package com.lagradost.cloudstream3.utils

/** Copy of the Android app's Event, used by extensions to listen for app events */
class Event<T> {
    private val observers = mutableSetOf<(T) -> Unit>()

    val size: Int get() = observers.size

    operator fun plusAssign(observer: (T) -> Unit) {
        synchronized(observers) {
            observers.add(observer)
        }
    }

    operator fun minusAssign(observer: (T) -> Unit) {
        synchronized(observers) {
            observers.remove(observer)
        }
    }

    operator fun invoke(value: T) {
        // Copy so observers can unsubscribe themselves while being called
        val current = synchronized(observers) { observers.toList() }
        for (observer in current) observer(value)
    }
}
