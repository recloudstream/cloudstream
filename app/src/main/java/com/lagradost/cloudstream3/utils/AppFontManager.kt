package com.lagradost.cloudstream3.utils

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.media3.ui.SubtitleView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.github.khoben.woff2android.Woff2Typeface
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.app
import java.io.File
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

object AppFontManager {
    private const val cacheFolder = "app_fonts"
    private val faceRegex =
        Regex(
            """/\*\s*([^*]+?)\s*\*/\s*@font-face\s*\{.*?src:\s*url\(([^)]+)\)""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
    private val lock = Any()
    private var cachedFont: Pair<String, Typeface>? = null

    private val recyclerListener = object : RecyclerView.OnChildAttachStateChangeListener {
        override fun onChildViewAttachedToWindow(view: View) {
            applyToViewTree(view)
        }

        override fun onChildViewDetachedFromWindow(view: View) = Unit
    }

    val activityLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (activity is FragmentActivity) {
                activity.supportFragmentManager.registerFragmentLifecycleCallbacks(
                    fragmentLifecycleCallbacks,
                    true
                )
            }
            if (activity is ComponentActivity) {
                activity.lifecycleScope.launch {
                    warmUp(activity)
                    refresh(activity)
                }
            } else {
                activity.window?.decorView?.post {
                    applyToViewTree(it)
                }
            }
        }

        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    private val fragmentLifecycleCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentViewCreated(
            fm: FragmentManager,
            f: Fragment,
            v: View,
            savedInstanceState: Bundle?
        ) {
            v.post {
                applyToViewTree(v)
            }
        }
    }

    suspend fun setSelectedFont(context: Context, rawName: String?): Result<String?> {
        val fontName = rawName?.trim()?.takeIf { it.isNotEmpty() }
        return runCatching {
            if (fontName == null) {
                PreferenceManager.getDefaultSharedPreferences(context).edit {
                    remove(context.getString(R.string.app_font_key))
                }
                synchronized(lock) {
                    cachedFont = null
                }
                null
            } else {
                val key = cacheKey(fontName, currentLocale(context.resources.configuration))
                val typeface = loadTypeface(context, fontName)
                    ?: throw IllegalArgumentException(context.getString(R.string.app_font_invalid))
                synchronized(lock) {
                    cachedFont = key to typeface
                }
                PreferenceManager.getDefaultSharedPreferences(context).edit {
                    putString(context.getString(R.string.app_font_key), fontName)
                    putString(
                        context.getString(R.string.app_font_recent_key),
                        updatedRecentFonts(context, fontName).joinToString("\n")
                    )
                }
                fontName
            }
        }
    }

    fun getSelectedFont(context: Context): String? {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getString(context.getString(R.string.app_font_key), null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun getRecentFonts(context: Context): List<String> {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getString(context.getString(R.string.app_font_recent_key), null)
            ?.split('\n')
            ?.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            ?: emptyList()
    }

    fun getSuggestedFonts(context: Context): List<String> {
        return buildList {
            addAll(getRecentFonts(context))
            addAll(context.resources.getStringArray(R.array.app_font_suggestions))
        }.distinctBy { it.lowercase(Locale.ROOT) }
    }

    fun getSummary(context: Context): String {
        return getSelectedFont(context) ?: context.getString(R.string.app_font_default)
    }

    fun refresh(activity: Activity) {
        activity.window?.decorView?.post {
            applyToViewTree(it)
        }
    }

    fun applyToViewTree(root: View) {
        val baseTypeface = getCachedTypeface(root.context) ?: return
        val selectedFont = getSelectedFont(root.context) ?: return
        val key = cacheKey(selectedFont, currentLocale(root.context.resources.configuration))
        applyInternal(root, baseTypeface, key)
    }

    private fun applyInternal(view: View, baseTypeface: Typeface, key: String) {
        if (view is SubtitleView) {
            return
        }

        if (view is TextView) {
            val currentKey = view.getTag(R.id.app_font_tag) as? String
            if (currentKey != key) {
                val style = view.typeface?.style ?: Typeface.NORMAL
                view.typeface = Typeface.create(baseTypeface, style)
                view.setTag(R.id.app_font_tag, key)
            }
        }

        if (view is RecyclerView && view.getTag(R.id.app_font_recycler_listener_tag) == null) {
            view.addOnChildAttachStateChangeListener(recyclerListener)
            view.setTag(R.id.app_font_recycler_listener_tag, recyclerListener)
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                applyInternal(view.getChildAt(index), baseTypeface, key)
            }
        }
    }

    private suspend fun warmUp(context: Context) {
        if (getSelectedFont(context) == null) {
            return
        }
        loadTypeface(context, getSelectedFont(context))
    }

    private fun getCachedTypeface(context: Context): Typeface? {
        val fontName = getSelectedFont(context) ?: return null
        val key = cacheKey(fontName, currentLocale(context.resources.configuration))
        synchronized(lock) {
            cachedFont?.takeIf { it.first == key }?.let { return it.second }
        }
        val file = getFontFile(context, fontName, preferredSubset(currentLocale(context.resources.configuration)))
        if (!file.exists()) {
            return null
        }
        return runCatching {
            val typeface = createTypeface(file)
            synchronized(lock) {
                cachedFont = key to typeface
            }
            typeface
        }.getOrNull()
    }

    private suspend fun loadTypeface(context: Context, fontName: String?): Typeface? {
        fontName ?: return null
        getCachedTypeface(context)?.let { return it }
        val locale = currentLocale(context.resources.configuration)
        val subset = preferredSubset(locale)
        val file = getFontFile(context, fontName, subset)
        if (!file.exists()) {
            val css = app.get(
                "https://api.fonts.coollabs.io/css2?family=${encodeFamily(fontName)}&display=swap",
                cacheTime = 7,
                cacheUnit = TimeUnit.DAYS
            ).text
            val downloadUrl = resolveDownloadUrl(css, locale) ?: return null
            file.parentFile?.mkdirs()
            val fontBytes = app.get(
                downloadUrl,
                cacheTime = 30,
                cacheUnit = TimeUnit.DAYS
            ).okhttpResponse.body?.bytes() ?: return null
            file.writeBytes(fontBytes)
        }
        return runCatching {
            val typeface = createTypeface(file)
            synchronized(lock) {
                cachedFont = cacheKey(fontName, locale) to typeface
            }
            typeface
        }.getOrNull()
    }

    private fun resolveDownloadUrl(css: String, locale: Locale): String? {
        val fontFaces = faceRegex.findAll(css).map {
            it.groupValues[1].trim() to it.groupValues[2].trim().removePrefix("\"").removeSuffix("\"")
        }.toList()
        if (fontFaces.isEmpty()) {
            return null
        }
        val preferred = preferredSubsets(locale)
        for (subset in preferred) {
            fontFaces.firstOrNull { it.first.equals(subset, true) }?.let { return it.second }
        }
        return fontFaces.firstOrNull()?.second
    }

    private fun preferredSubsets(locale: Locale): List<String> {
        return buildList {
            add(preferredSubset(locale))
            add("latin-ext")
            add("latin")
        }.distinct()
    }

    private fun preferredSubset(locale: Locale): String {
        val language = locale.language.lowercase(Locale.ROOT)
        val script = locale.script.lowercase(Locale.ROOT)
        return when {
            language == "vi" -> "vietnamese"
            language == "ja" -> "japanese"
            language == "ko" -> "korean"
            language == "zh" && locale.country.equals("TW", true) -> "chinese-traditional"
            language == "zh" -> "chinese-simplified"
            language == "he" || language == "iw" -> "hebrew"
            language == "ar" || script == "arab" -> "arabic"
            language == "hi" || script == "deva" -> "devanagari"
            language == "th" -> "thai"
            language in setOf("ru", "uk", "bg", "be", "mk", "sr", "kk", "ky", "mn") || script == "cyrl" -> "cyrillic"
            language in setOf("el") || script == "grek" -> "greek"
            else -> "latin"
        }
    }

    private fun createTypeface(file: File): Typeface {
        return Woff2Typeface.get().createFromFile(file)
    }

    private fun currentLocale(configuration: Configuration): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            configuration.locale
        }
    }

    private fun updatedRecentFonts(context: Context, fontName: String): List<String> {
        return buildList {
            add(fontName)
            addAll(getRecentFonts(context))
        }.distinctBy { it.lowercase(Locale.ROOT) }.take(8)
    }

    private fun getFontFile(context: Context, fontName: String, subset: String): File {
        return context.cacheDir.resolve(cacheFolder).resolve("${safeName(fontName)}-$subset.woff2")
    }

    private fun safeName(value: String): String {
        return Normalizer.normalize(value, Normalizer.Form.NFKD)
            .replace(Regex("[^A-Za-z0-9]+"), "-")
            .trim('-')
            .lowercase(Locale.ROOT)
            .ifEmpty { "font" }
    }

    private fun cacheKey(fontName: String, locale: Locale): String {
        return "${safeName(fontName)}-${preferredSubset(locale)}"
    }

    private fun encodeFamily(fontName: String): String {
        return Uri.encode(fontName.trim())
    }
}
