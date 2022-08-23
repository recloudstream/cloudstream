package com.lagradost.cloudstream3.ui.settings

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.getApiDubstatusSettings
import com.lagradost.cloudstream3.APIHolder.getApiProviderLangSettings
import com.lagradost.cloudstream3.AcraApplication.Companion.removeKey
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.getPref
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setPaddingBottom
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setUpToolbar
import com.lagradost.cloudstream3.utils.USER_SELECTED_HOMEPAGE_API
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showMultiDialog
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard

fun getCurrentLocale(context: Context): String {
    val res = context.resources
    // Change locale settings in the app.
    // val dm = res.displayMetrics
    val conf = res.configuration
    return conf?.locale?.language ?: "en"
}

// idk, if you find a way of automating this it would be great
// https://www.iemoji.com/view/emoji/1794/flags/antarctica
// Emoji Character Encoding Data --> C/C++/Java Src
// https://en.wikipedia.org/wiki/List_of_ISO_639-1_codes leave blank for auto
val appLanguages = arrayListOf(
    Triple("", "Spanish", "es"),
    Triple("", "English", "en"),
    Triple("", "Viet Nam", "vi"),
    Triple("", "Dutch", "nl"),
    Triple("", "French", "fr"),
    Triple("", "Greek", "el"),
    Triple("", "Swedish", "sv"),
    Triple("", "Tagalog", "tl"),
    Triple("", "Polish", "pl"),
    Triple("", "Hindi", "hi"),
    Triple("", "Malayalam", "ml"),
    Triple("", "Norsk", "no"),
    Triple("", "German", "de"),
    Triple("", "Arabic", "ar"),
    Triple("", "Turkish", "tr"),
    Triple("", "Macedonian", "mk"),
    Triple("\uD83C\uDDF5\uD83C\uDDF9", "Portuguese", "pt"),
    Triple("\uD83C\uDDE7\uD83C\uDDF7", "Brazilian Portuguese", "bp"),
    Triple("", "Romanian", "ro"),
    Triple("", "Italian", "it"),
    Triple("", "Chinese", "zh"),
    Triple("\uD83C\uDDEE\uD83C\uDDE9", "Indonesian", "in"),
    Triple("", "Czech", "cs"),
).sortedBy { it.second } //ye, we go alphabetical, so ppl don't put their lang on top

class SettingsLang : PreferenceFragmentCompat() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpToolbar(R.string.category_preferred_media_and_lang)
        setPaddingBottom()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        hideKeyboard()
        setPreferencesFromResource(R.xml.settings_media_lang, rootKey)
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(requireContext())

        getPref(R.string.display_sub_key)?.setOnPreferenceClickListener {
            activity?.getApiDubstatusSettings()?.let { current ->
                val dublist = DubStatus.values()
                val names = dublist.map { it.name }

                val currentList = ArrayList<Int>()
                for (i in current) {
                    currentList.add(dublist.indexOf(i))
                }

                activity?.showMultiDialog(
                    names,
                    currentList,
                    getString(R.string.display_subbed_dubbed_settings),
                    {}) { selectedList ->
                    APIRepository.dubStatusActive = selectedList.map { dublist[it] }.toHashSet()

                    settingsManager.edit().putStringSet(
                        this.getString(R.string.display_sub_key),
                        selectedList.map { names[it] }.toMutableSet()
                    ).apply()
                }
            }

            return@setOnPreferenceClickListener true
        }

        getPref(R.string.prefer_media_type_key)?.setOnPreferenceClickListener {
            val names = enumValues<TvType>().sorted().map { it.name }
            val default = enumValues<TvType>().sorted().filter { it != TvType.NSFW }.map { it.ordinal }
            val defaultSet = default.map { it.toString() }.toSet()
            val currentList = try {
                settingsManager.getStringSet(getString(R.string.prefer_media_type_key), defaultSet)?.map {
                    it.toInt()
                }
            } catch (e: Throwable) { null }  ?: default

            activity?.showMultiDialog(
                names,
                currentList,
                getString(R.string.preferred_media_settings),
                {}) { selectedList ->
                settingsManager.edit().putStringSet(
                    this.getString(R.string.prefer_media_type_key),
                    selectedList.map { it.toString() }.toMutableSet()
                ).apply()
                removeKey(USER_SELECTED_HOMEPAGE_API)
                //(context ?: AcraApplication.context)?.let { ctx -> app.initClient(ctx) }
            }

            return@setOnPreferenceClickListener true
        }

        getPref(R.string.locale_key)?.setOnPreferenceClickListener { pref ->
            val tempLangs = appLanguages.toMutableList()
            //if (beneneCount > 100) {
            //    tempLangs.add(Triple("\uD83E\uDD8D", "mmmm... monke", "mo"))
            //}
            val current = getCurrentLocale(pref.context)
            val languageCodes = tempLangs.map { (_, _, iso) -> iso }
            val languageNames = tempLangs.map { (emoji, name, iso) ->
                val flag = emoji.ifBlank { SubtitleHelper.getFlagFromIso(iso) ?: "ERROR" }
                "$flag $name"
            }
            val index = languageCodes.indexOf(current)

            activity?.showDialog(
                languageNames, index, getString(R.string.app_language), true, { }
            ) { languageIndex ->
                try {
                    val code = languageCodes[languageIndex]
                    CommonActivity.setLocale(activity, code)
                    settingsManager.edit().putString(getString(R.string.locale_key), code).apply()
                    activity?.recreate()
                } catch (e: Exception) {
                    logError(e)
                }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.provider_lang_key)?.setOnPreferenceClickListener {
            activity?.getApiProviderLangSettings()?.let { current ->
                val langs = APIHolder.apis.map { it.lang }.toSet()
                    .sortedBy { SubtitleHelper.fromTwoLettersToLanguage(it) }

                val currentList = ArrayList<Int>()
                for (i in current) {
                    currentList.add(langs.indexOf(i))
                }

                val names = langs.map {
                    val emoji = SubtitleHelper.getFlagFromIso(it)
                    val name = SubtitleHelper.fromTwoLettersToLanguage(it)
                    val fullName = "$emoji $name"
                    Pair(it, fullName)
                }

                activity?.showMultiDialog(
                    names.map { it.second },
                    currentList,
                    getString(R.string.provider_lang_settings),
                    {}) { selectedList ->
                    settingsManager.edit().putStringSet(
                        this.getString(R.string.provider_lang_key),
                        selectedList.map { names[it].first }.toMutableSet()
                    ).apply()
                    //APIRepository.providersActive = it.context.getApiSettings()
                }
            }

            return@setOnPreferenceClickListener true
        }
    }
}
