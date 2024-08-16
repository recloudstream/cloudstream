package com.lagradost.cloudstream3.utils

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import java.util.WeakHashMap

object BackPressedCallbackHelper {
    private val backPressedCallbacks = WeakHashMap<ComponentActivity, OnBackPressedCallback>()

    fun ComponentActivity.attachBackPressedCallback(callback: () -> Unit) {
        if (backPressedCallbacks[this] == null) {
            val newCallback = object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    callback.invoke()
                }
            }
            backPressedCallbacks[this] = newCallback
            onBackPressedDispatcher.addCallback(this, newCallback)
        }

        backPressedCallbacks[this]?.isEnabled = true
    }

    fun ComponentActivity.detachBackPressedCallback() {
        backPressedCallbacks[this]?.isEnabled = false
        backPressedCallbacks.remove(this)
    }
}