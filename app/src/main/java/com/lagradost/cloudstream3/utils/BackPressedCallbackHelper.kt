package com.lagradost.cloudstream3.utils

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import java.lang.ref.WeakReference
import java.util.WeakHashMap

object BackPressedCallbackHelper {

    private val backPressedCallbacks =
        WeakHashMap<ComponentActivity, MutableMap<String, OnBackPressedCallback>>()

    class CallbackHelper(
        private val activityRef: WeakReference<ComponentActivity>,
        private val callback: OnBackPressedCallback
    ) {
        fun runDefault() {
            val activity = activityRef.get() ?: return
            val wasEnabled = callback.isEnabled
            callback.isEnabled = false
            try {
                activity.onBackPressedDispatcher.onBackPressed()
            } finally {
                callback.isEnabled = wasEnabled
            }
        }
    }

    fun ComponentActivity.attachBackPressedCallback(
        id: String,
        callback: CallbackHelper.() -> Unit
    ) {
        val callbackMap = backPressedCallbacks.getOrPut(this) { mutableMapOf() }
        if (callbackMap.containsKey(id)) return

        // We use WeakReference to protect against potential leaks.
        val activityRef = WeakReference(this)
        val newCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                CallbackHelper(activityRef, this).callback()
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
