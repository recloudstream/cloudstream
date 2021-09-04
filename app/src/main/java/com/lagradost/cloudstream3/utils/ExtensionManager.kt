package com.lagradost.cloudstream3.utils

import android.content.Context
import dalvik.system.PathClassLoader
import java.io.File

class ExtensionManager {
    interface TestSource {
        fun doMath(): Int
    }

    fun getSourceFromDex(context: Context, pkgName: String, file: File): TestSource? {
        val loader = PathClassLoader(file.absolutePath, null, context.classLoader)
        var source: TestSource? = null

        source = when (val obj = Class.forName(pkgName, false, loader).newInstance()) {
            is TestSource -> obj
            else -> null
        }

        return source
    }
}