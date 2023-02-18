package com.lagradost.cloudstream3.ui.settings

import android.os.Bundle
import android.view.View
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.getApiDubstatusSettings
import com.lagradost.cloudstream3.APIHolder.getApiProviderLangSettings
import com.lagradost.cloudstream3.AcraApplication.Companion.removeKey
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.getPref
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setPaddingBottom
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setUpToolbar
import com.lagradost.cloudstream3.utils.USER_SELECTED_HOMEPAGE_API
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showMultiDialog
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.navigate

class SettingsProviders : PreferenceFragmentCompat() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpToolbar(R.string.category_providers)
        setPaddingBottom()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        hideKeyboard()
        setPreferencesFromResource(R.xml.settings_providers, rootKey)
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

        getPref(R.string.test_providers_key)?.setOnPreferenceClickListener {
            // Somehow animations do not work without this.
            val options = NavOptions.Builder()
                .setEnterAnim(R.anim.enter_anim)
                .setExitAnim(R.anim.exit_anim)
                .setPopEnterAnim(R.anim.pop_enter)
                .setPopExitAnim(R.anim.pop_exit)
                .build()

            this@SettingsProviders.findNavController()
                .navigate(R.id.navigation_test_providers, null, options)
            true
        }

        getPref(R.string.prefer_media_type_key)?.setOnPreferenceClickListener {
            val names = enumValues<TvType>().sorted().map { it.name }
            val default =
                enumValues<TvType>().sorted().filter { it != TvType.NSFW }.map { it.ordinal }
            val defaultSet = default.map { it.toString() }.toSet()
            val currentList = try {
                settingsManager.getStringSet(getString(R.string.prefer_media_type_key), defaultSet)
                    ?.map {
                        it.toInt()
                    }
            } catch (e: Throwable) {
                null
            } ?: default

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

        getPref(R.string.provider_lang_key)?.setOnPreferenceClickListener {
            activity?.getApiProviderLangSettings()?.let { current ->
                val languages = APIHolder.apis.map { it.lang }.toSet()
                    .sortedBy { SubtitleHelper.fromTwoLettersToLanguage(it) } + AllLanguagesName

                val currentList = current.map {
                    languages.indexOf(it)
                }

                val names = languages.map {
                    if (it == AllLanguagesName) {
                        Pair(it, getString(R.string.all_languages_preference))
                    } else {
                        val emoji = SubtitleHelper.getFlagFromIso(it)
                        val name = SubtitleHelper.fromTwoLettersToLanguage(it)
                        val fullName = "$emoji $name"
                        Pair(it, fullName)
                    }
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
