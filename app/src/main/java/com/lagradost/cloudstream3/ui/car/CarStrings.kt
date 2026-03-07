package com.lagradost.cloudstream3.ui.car

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.R
import java.util.Locale

/**
 * Singleton helper for Android Auto localization.
 * Wraps Android Resources to support overriding the language based on app settings,
 * independent of the system locale.
 */
object CarStrings {
    private var resources: Resources? = null
    
    /**
     * Initialize the helper with the application context.
     * Call this in Application.onCreate().
     */
    fun init(context: Context) {
        // Read user preference
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val localeCode = prefs.getString(context.getString(R.string.locale_key), null)
        
        // If a specific locale is set (e.g. "it"), force it.
        // Otherwise, use system default.
        if (!localeCode.isNullOrEmpty()) {
            val locale = Locale(localeCode)
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            resources = context.createConfigurationContext(config).resources
        } else {
            resources = context.resources
        }
    }

    /**
     * Get a localized string by Resource ID.
     */
    fun get(@StringRes id: Int, vararg args: Any): String {
        val res = resources ?: throw IllegalStateException("CarStrings not initialized! Call init() in Application.onCreate()")
        return if (args.isEmpty()) {
            res.getString(id)
        } else {
            res.getString(id, *args)
        }
    }
}
