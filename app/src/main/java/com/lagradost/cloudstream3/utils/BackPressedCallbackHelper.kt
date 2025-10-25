package com.lagradost.cloudstream3.utils

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import java.util.WeakHashMap

object BackPressedCallbackHelper {
    private val backPressedCallbacks = WeakHashMap<ComponentActivity, MutableMap<String, OnBackPressedCallback>>()

    fun ComponentActivity.attachBackPressedCallback(id: String, callback: () -> Unit) {
        val callbackMap = backPressedCallbacks.getOrPut(this) { mutableMapOf() }

        if (callbackMap.containsKey(id)) return

        val newCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                callback.invoke()
            }
        }
        callbackMap[id] = newCallback

        onBackPressedDispatcher.addCallback(this, newCallback)
    }

    fun ComponentActivity.disableBackPressedCallback(id : String) {
        backPressedCallbacks[this]?.get(id)?.isEnabled = false
    }

    fun ComponentActivity.enableBackPressedCallback(id : String) {
        backPressedCallbacks[this]?.get(id)?.isEnabled = true
    }

    fun ComponentActivity.detachBackPressedCallback(id: String) {
        val callbackMap = backPressedCallbacks[this] ?: return

        callbackMap[id]?.let { callback ->
            callback.isEnabled = false
            callbackMap.remove(id)
        }

        if (callbackMap.isEmpty()) {
            backPressedCallbacks.remove(this)
        }
    }
}