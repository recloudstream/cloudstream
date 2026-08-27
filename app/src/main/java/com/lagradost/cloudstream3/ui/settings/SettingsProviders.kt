package com.lagradost.cloudstream3.ui.settings

import android.os.Bundle
import android.text.format.Formatter.formatShortFileSize
import android.view.View
import android.widget.Toast
import androidx.core.content.edit
import androidx.navigation.fragment.findNavController
import androidx.navigation.NavOptions
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.BasePreferenceFragmentCompat
import com.lagradost.cloudstream3.ui.home.HomeCache
import com.lagradost.cloudstream3.ui.player.RepoLinkGenerator
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.getPref
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setPaddingBottom
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setToolBarScrollFlags
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setUpToolbar
import com.lagradost.cloudstream3.utils.AppContextUtils.getApiDubstatusSettings
import com.lagradost.cloudstream3.utils.AppContextUtils.getApiProviderLangSettings
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showMultiDialog
import com.lagradost.cloudstream3.utils.SubtitleHelper.getNameNextToFlagEmoji
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard

class SettingsProviders : BasePreferenceFragmentCompat() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpToolbar(R.string.category_providers)
        setPaddingBottom()
        setToolBarScrollFlags()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        hideKeyboard()
        setPreferencesFromResource(R.xml.settings_providers, rootKey)
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(requireContext())

        val cacheNames = resources.getStringArray(R.array.cache_time_names)
        val cacheValues = resources.getIntArray(R.array.cache_time_values)

        fun updateCacheSummary() {
            val currentVal = DataStoreHelper.cacheTimeMinutes
            val index = cacheValues.indexOf(currentVal)
            getPref(R.string.cache_time_key)?.summary = if (index != -1) {
                cacheNames.getOrNull(index)
            } else {
                "${currentVal}m"
            }
        }
        updateCacheSummary()

        getPref(R.string.cache_time_key)?.setOnPreferenceClickListener {
            val currentVal = DataStoreHelper.cacheTimeMinutes
            val currentIndex = cacheValues.indexOf(currentVal).let { if (it == -1) 0 else it }

            activity?.showBottomDialog(
                cacheNames.toList(),
                currentIndex,
                getString(R.string.cache_time_settings),
                false,
                {}
            ) { selectedIndex ->
                val selectedMinutes = cacheValues.getOrNull(selectedIndex) ?: 0
                DataStoreHelper.cacheTimeMinutes = selectedMinutes
                updateCacheSummary()
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.clear_provider_cache_key)?.let { pref ->
            fun updateSummary() {
                try {
                    val size = HomeCache.getCacheSize(pref.context)
                    pref.summary = formatShortFileSize(pref.context, size)
                } catch (e: Exception) {
                    logError(e)
                }
            }

            updateSummary()

            pref.setOnPreferenceClickListener {
                try {
                    HomeCache.clearAll(context)
                    APIRepository.clearCache()
                    RepoLinkGenerator.cache.clear()
                    updateSummary()
                    showToast(R.string.clear_provider_cache_cleared, Toast.LENGTH_SHORT)
                } catch (e: Exception) {
                    logError(e)
                }
                return@setOnPreferenceClickListener true
            }
        }

        getPref(R.string.display_sub_key)?.setOnPreferenceClickListener {
            activity?.getApiDubstatusSettings()?.let { current ->
                val dublist = DubStatus.entries
                val names = dublist.map { it.name }

                val currentList = ArrayList<Int>()
                for (i in current) {
                    currentList.add(dublist.indexOf(i))
                }

                activity?.showMultiDialog(
                    names,
                    currentList,
                    getString(R.string.display_subbed_dubbed_settings),
                    {}
                ) { selectedList ->
                    APIRepository.dubStatusActive = selectedList.map { dublist[it] }.toHashSet()
                    settingsManager.edit {
                        putStringSet(
                            getString(R.string.display_sub_key),
                            selectedList.map { names[it] }.toMutableSet()
                        )
                    }
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
                {}
            ) { selectedList ->
                settingsManager.edit {
                    putStringSet(
                        getString(R.string.prefer_media_type_key),
                        selectedList.map { it.toString() }.toMutableSet()
                    )
                }
                DataStoreHelper.currentHomePage = null
                //(context ?: CloudStreamApp.context)?.let { ctx -> app.initClient(ctx) }
            }

            return@setOnPreferenceClickListener true
        }

        getPref(R.string.provider_lang_key)?.setOnPreferenceClickListener {
            activity?.getApiProviderLangSettings()?.let { currentLangTags ->
                val languagesTagName = APIHolder.apis.withLock {
                    listOf(Pair(AllLanguagesName, getString(R.string.all_languages_preference))) +
                        APIHolder.apis.map { Pair(it.lang, getNameNextToFlagEmoji(it.lang) ?: it.lang) }
                            .toSet().sortedBy { it.second.substringAfter("\u00a0").lowercase() }
                }

                val currentIndexList = currentLangTags.map { langTag ->
                    languagesTagName.indexOfFirst { lang -> lang.first == langTag }
                }

                activity?.showMultiDialog(
                    languagesTagName.map { it.second },
                    currentIndexList,
                    getString(R.string.provider_lang_settings),
                    {}
                ) { selectedList ->
                    settingsManager.edit {
                        putStringSet(
                            getString(R.string.provider_lang_key),
                            selectedList.map { languagesTagName[it].first }.toSet()
                        )
                    }
                    // APIRepository.providersActive = it.context.getApiSettings()
                }
            }

            return@setOnPreferenceClickListener true
        }
    }
}
