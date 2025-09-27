package com.lagradost.cloudstream3.ui.settings

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.os.ConfigurationCompat
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.APIHolder.allProviders
import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.databinding.AddRemoveSitesBinding
import com.lagradost.cloudstream3.databinding.AddSiteInputBinding
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.network.initClient
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.beneneCount
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.getPref
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.hideOn
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setPaddingBottom
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setToolBarScrollFlags
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setUpToolbar
import com.lagradost.cloudstream3.ui.settings.utils.getChooseFolderLauncher
import com.lagradost.cloudstream3.utils.BatteryOptimizationChecker.isAppRestricted
import com.lagradost.cloudstream3.utils.BatteryOptimizationChecker.showBatteryOptimizationDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showMultiDialog
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.USER_PROVIDER_API
import com.lagradost.cloudstream3.utils.VideoDownloadManager
import com.lagradost.cloudstream3.utils.VideoDownloadManager.getBasePath
import java.util.Locale

// Change local language settings in the app.
fun getCurrentLocale(context: Context): String {
    val conf = context.resources.configuration

    return ConfigurationCompat.getLocales(conf)?.get(0)?.toLanguageTag() ?: "en"
}

/**
 * List of app supported languages.
 * Language code shall be a IETF BCP 47 conformant tag
 *
 * See locales on:
 * https://github.com/unicode-org/cldr-json/blob/main/cldr-json/cldr-core/availableLocales.json
 * https://www.iana.org/assignments/language-subtag-registry/language-subtag-registry
 * https://android.googlesource.com/platform/frameworks/base/+/android-16.0.0_r2/core/res/res/values/locale_config.xml
 * https://iso639-3.sil.org/code_tables/639/data/all
*/
val appLanguages = arrayListOf(
    /* begin language list */
    Pair("Afrikaans", "af"),
    Pair("Azərbaycan dili", "az"),
    Pair("Bahasa Indonesia", "in"),
    Pair("Bahasa Melayu", "ms"),
    Pair("Deutsch", "de"),
    Pair("English", "en"),
    Pair("Español", "es"),
    Pair("Esperanto", "eo"),
    Pair("Français", "fr"),
    Pair("Galego", "gl"),
    Pair("hrvatski", "hr"),
    Pair("Italiano", "it"),
    Pair("Latviešu valoda", "lv"),
    Pair("Lietuvių kalba", "lt"),
    Pair("Magyar", "hu"),
    Pair("Malti", "mt"),
    Pair("mmmm... monke", "qt"),
    Pair("Nederlands", "nl"),
    Pair("Norsk bokmål", "no"),
    Pair("Norsk nynorsk", "nn"),
    Pair("Polski", "pl"),
    Pair("Português", "pt"),
    Pair("Português (Brasil)", "pt-BR"),
    Pair("Română", "ro"),
    Pair("Slovenčina", "sk"),
    Pair("Soomaaliga", "so"),
    Pair("Svenska", "sv"),
    Pair("Tagalog", "tl"),
    Pair("Tiếng Việt", "vi"),
    Pair("Türkçe", "tr"),
    Pair("Wikang Filipino", "fil"),
    Pair("Čeština", "cs"),
    Pair("Ελληνικά", "el"),
    Pair("български", "bg"),
    Pair("македонски", "mk"),
    Pair("русский", "ru"),
    Pair("українська", "uk"),
    Pair("עברית", "iw"),
    Pair("اردو", "ur"),
    Pair("العربية", "ar"),
    Pair("اللهجة النجدية", "ars"),
    Pair("عربي شامي", "apc"),
    Pair("فارسی", "fa"),
    Pair("کوردیی ناوەندی", "ckb"),
    Pair("नेपाली", "ne"),
    Pair("हिन्दी", "hi"),
    Pair("অসমীয়া", "as"),
    Pair("বাংলা", "bn"),
    Pair("ଓଡ଼ିଆ", "or"),
    Pair("தமிழ்", "ta"),
    Pair("ಕನ್ನಡ", "kn"),
    Pair("മലയാളം", "ml"),
    Pair("ဗမာစာ", "my"),
    Pair("ትግርኛ", "ti"),
    Pair("አማርኛ", "am"),
    Pair("中文", "zh"),
    Pair("日本語 (にほんご)", "ja"),
    Pair("正體中文(臺灣)", "zh-TW"),
    Pair("한국어", "ko"),
/* end language list */
).sortedBy { it.first.lowercase(Locale.ROOT) } // ye, we go alphabetical, so ppl don't put their lang on top

fun Pair<String, String>.nameNextToFlagEmoji(): String {
    // fallback to [A][A] -> [?] question mak flag
    val flag = SubtitleHelper.getFlagFromIso(this.second) ?: "\ud83c\udde6\ud83c\udde6"

    return "$flag\u00a0${this.first}" // \u00a0 non-breaking space
}

class SettingsGeneral : PreferenceFragmentCompat() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpToolbar(R.string.category_general)
        setPaddingBottom()
        setToolBarScrollFlags()
    }

    data class CustomSite(
        @JsonProperty("parentJavaClass") // javaClass.simpleName
        val parentJavaClass: String,
        @JsonProperty("name")
        val name: String,
        @JsonProperty("url")
        val url: String,
        @JsonProperty("lang")
        val lang: String,
    )

    private val pathPicker = getChooseFolderLauncher { uri, path ->
        val context = context ?: AcraApplication.context ?: return@getChooseFolderLauncher
        (path ?: uri.toString()).let {
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(getString(R.string.download_path_key), uri.toString())
                .putString(getString(R.string.download_path_key_visual), it)
                .apply()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        hideKeyboard()
        setPreferencesFromResource(R.xml.settings_general, rootKey)
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(requireContext())

        fun getCurrent(): MutableList<CustomSite> {
            return getKey<Array<CustomSite>>(USER_PROVIDER_API)?.toMutableList()
                ?: mutableListOf()
        }

        getPref(R.string.locale_key)?.setOnPreferenceClickListener { pref ->
            val current = getCurrentLocale(pref.context)
            val languageTagsIETF = appLanguages.map { it.second }
            val languageNames = appLanguages.map { it.nameNextToFlagEmoji() }
            val currentIndex = languageTagsIETF.indexOf(current)

            activity?.showDialog(
                languageNames, currentIndex, getString(R.string.app_language), true, { }
            ) { selectedLangIndex ->
                try {
                    val langTagIETF = languageTagsIETF[selectedLangIndex]
                    CommonActivity.setLocale(activity, langTagIETF)
                    settingsManager.edit().putString(getString(R.string.locale_key), langTagIETF).apply()
                    activity?.recreate()
                } catch (e: Exception) {
                    logError(e)
                }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.battery_optimisation_key)?.hideOn(TV or EMULATOR)?.setOnPreferenceClickListener {
            val ctx = context ?: return@setOnPreferenceClickListener false

            if (isAppRestricted(ctx)) {
                ctx.showBatteryOptimizationDialog()
            } else {
                showToast(R.string.app_unrestricted_toast)
            }

            true
        }

        fun showAdd() {
            val providers = synchronized(allProviders) { allProviders.distinctBy { it.javaClass }.sortedBy { it.name } }
            activity?.showDialog(
                providers.map { "${it.name} (${it.mainUrl})" },
                -1,
                context?.getString(R.string.add_site_pref) ?: return,
                true,
                {}) { selection ->
                val provider = providers.getOrNull(selection) ?: return@showDialog

                val binding : AddSiteInputBinding = AddSiteInputBinding.inflate(layoutInflater,null,false)

                val builder =
                    AlertDialog.Builder(context ?: return@showDialog, R.style.AlertDialogCustom)
                        .setView(binding.root)

                val dialog = builder.create()
                dialog.show()

                binding.text2.text = provider.name
                binding.applyBtt.setOnClickListener {
                    val name = binding.siteNameInput.text?.toString()
                    val url = binding.siteUrlInput.text?.toString()
                    val lang = binding.siteLangInput.text?.toString()
                    val realLang = if (lang.isNullOrBlank()) provider.lang else lang
                    if (url.isNullOrBlank() || name.isNullOrBlank()) {
                        showToast(R.string.error_invalid_data, Toast.LENGTH_SHORT)
                        return@setOnClickListener
                    }

                    val current = getCurrent()
                    val newSite = CustomSite(provider.javaClass.simpleName, name, url, realLang)
                    current.add(newSite)
                    setKey(USER_PROVIDER_API, current.toTypedArray())
                    // reload apis
                    MainActivity.afterPluginsLoadedEvent.invoke(false)

                    dialog.dismissSafe(activity)
                }
                binding.cancelBtt.setOnClickListener {
                    dialog.dismissSafe(activity)
                }
            }
        }

        fun showDelete() {
            val current = getCurrent()

            activity?.showMultiDialog(
                current.map { it.name },
                listOf(),
                context?.getString(R.string.remove_site_pref) ?: return,
                {}) { indexes ->
                current.removeAll(indexes.map { current[it] })
                setKey(USER_PROVIDER_API, current.toTypedArray())
            }
        }

        fun showAddOrDelete() {
            val binding : AddRemoveSitesBinding = AddRemoveSitesBinding.inflate(layoutInflater,null,false)
            val builder =
                AlertDialog.Builder(context ?: return, R.style.AlertDialogCustom)
                    .setView(binding.root)

            val dialog = builder.create()
            dialog.show()

            binding.addSite.setOnClickListener {
                showAdd()
                dialog.dismissSafe(activity)
            }
            binding.removeSite.setOnClickListener {
                showDelete()
                dialog.dismissSafe(activity)
            }
        }

        getPref(R.string.override_site_key)?.setOnPreferenceClickListener { _ ->

            if (getCurrent().isEmpty()) {
                showAdd()
            } else {
                showAddOrDelete()
            }

            return@setOnPreferenceClickListener true
        }

        getPref(R.string.legal_notice_key)?.setOnPreferenceClickListener {
            val builder: AlertDialog.Builder =
                AlertDialog.Builder(it.context, R.style.AlertDialogCustom)
            builder.setTitle(R.string.legal_notice)
            builder.setMessage(R.string.legal_notice_text)
            builder.show()
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.dns_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.dns_pref)
            val prefValues = resources.getIntArray(R.array.dns_pref_values)

            val currentDns =
                settingsManager.getInt(getString(R.string.dns_pref), 0)

            activity?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(currentDns),
                getString(R.string.dns_pref),
                true,
                {}) {
                settingsManager.edit().putInt(getString(R.string.dns_pref), prefValues[it]).apply()
                (context ?: AcraApplication.context)?.let { ctx -> app.initClient(ctx) }
            }
            return@setOnPreferenceClickListener true
        }

        fun getDownloadDirs(): List<String> {
            return safe {
                context?.let { ctx ->
                    val defaultDir = VideoDownloadManager.getDefaultDir(ctx)?.filePath()

                    val first = listOf(defaultDir)
                    (try {
                        val currentDir = ctx.getBasePath().let { it.first?.filePath() ?: it.second }

                        (first +
                                ctx.getExternalFilesDirs("").mapNotNull { it.path } +
                                currentDir)
                    } catch (e: Exception) {
                        first
                    }).filterNotNull().distinct()
                }
            } ?: emptyList()
        }

        settingsManager.edit().putBoolean(getString(R.string.jsdelivr_proxy_key), getKey(getString(R.string.jsdelivr_proxy_key), false) ?: false).apply()
        getPref(R.string.jsdelivr_proxy_key)?.setOnPreferenceChangeListener { _, newValue ->
            setKey(getString(R.string.jsdelivr_proxy_key), newValue)
            return@setOnPreferenceChangeListener true
        }

        getPref(R.string.download_path_key)?.setOnPreferenceClickListener {
            val dirs = getDownloadDirs()

            val currentDir =
                settingsManager.getString(getString(R.string.download_path_key_visual), null)
                    ?: context?.let { ctx -> VideoDownloadManager.getDefaultDir(ctx)?.filePath() }

            activity?.showBottomDialog(
                dirs + listOf(getString(R.string.custom)),
                dirs.indexOf(currentDir),
                getString(R.string.download_path_pref),
                true,
                {}) {
                // Last = custom
                if (it == dirs.size) {
                    try {
                        pathPicker.launch(Uri.EMPTY)
                    } catch (e: Exception) {
                        logError(e)
                    }
                } else {
                    // Sets both visual and actual paths.
                    // key = used path
                    // visual = visual path
                    settingsManager.edit()
                        .putString(getString(R.string.download_path_key), dirs[it])
                        .putString(getString(R.string.download_path_key_visual), dirs[it])
                        .apply()
                }
            }
            return@setOnPreferenceClickListener true
        }

        try {
            beneneCount =
                settingsManager.getInt(getString(R.string.benene_count), 0)
            getPref(R.string.benene_count)?.let { pref ->
                pref.summary =
                    if (beneneCount <= 0) getString(R.string.benene_count_text_none) else getString(
                        R.string.benene_count_text
                    ).format(
                        beneneCount
                    )

                pref.setOnPreferenceClickListener {
                    try {
                        beneneCount++
                        if (beneneCount%20 == 0) {
                            activity?.navigate(R.id.action_navigation_settings_general_to_easterEggMonkeFragment)
                        }
                        settingsManager.edit().putInt(
                            getString(R.string.benene_count),
                            beneneCount
                        ).apply()
                        it.summary = getString(R.string.benene_count_text).format(beneneCount)
                    } catch (e: Exception) {
                        logError(e)
                    }

                    return@setOnPreferenceClickListener true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
