@file:Suppress("unused")

package com.lagradost.cloudstream3

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.DesktopContext
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.getKeys
import com.lagradost.cloudstream3.utils.DataStore.removeKey
import com.lagradost.cloudstream3.utils.DataStore.removeKeys
import com.lagradost.cloudstream3.utils.DataStore.setKey
import com.lagradost.cloudstream4.desktop.ui.openInBrowser

/** Desktop copy of the Android Application class' companion, which extensions use for settings */
class CloudStreamApp {
    companion object {
        tailrec fun Context.getActivity(): Activity? = when (this) {
            is Activity -> this
            is ContextWrapper -> if (getBaseContext() === this) null else getBaseContext().getActivity()
            else -> null
        }

        // Kept void-returning setters explicit: extensions link against `(...)V` descriptors
        val context: Context? get() = DesktopContext

        fun <T : Any> getKeyClass(path: String, valueType: Class<T>): T? = context?.getKey(path, valueType)
        fun <T : Any> setKeyClass(path: String, value: T) {
            context?.setKey(path, value)
        }
        fun removeKeys(folder: String): Int? = context?.removeKeys(folder)
        fun <T> setKey(path: String, value: T) {
            context?.setKey(path, value)
        }
        fun <T> setKey(folder: String, path: String, value: T) {
            context?.setKey(folder, path, value)
        }
        inline fun <reified T : Any> getKey(path: String, defVal: T?): T? = context?.getKey(path, defVal)
        inline fun <reified T : Any> getKey(path: String): T? = context?.getKey(path)
        inline fun <reified T : Any> getKey(folder: String, path: String): T? = context?.getKey(folder, path)
        inline fun <reified T : Any> getKey(folder: String, path: String, defVal: T?): T? =
            context?.getKey(folder, path, defVal)

        fun getKeys(folder: String): List<String>? = context?.getKeys(folder)
        fun removeKey(folder: String, path: String) {
            context?.removeKey(folder, path)
        }
        fun removeKey(path: String) {
            context?.removeKey(path)
        }
        fun openBrowser(url: String) = openInBrowser(url)
    }
}
