package com.lagradost.cloudstream3.utils

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

object BackPressedCallbackHelper {
    private var backPressedCallback: OnBackPressedCallback? = null

    fun ComponentActivity.attachBackPressedCallback(callback: () -> Unit) {
        if (backPressedCallback == null) {
            backPressedCallback = object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    callback.invoke()
                }
            }
        }

        backPressedCallback?.isEnabled = true

        onBackPressedDispatcher.addCallback(
            this@attachBackPressedCallback,
            backPressedCallback ?: return
        )
    }

    fun detachBackPressedCallback() {
        backPressedCallback?.isEnabled = false
        backPressedCallback = null
    }
}