package com.lagradost.cloudstream3.ui.settings.extensions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.lagradost.cloudstream3.APIHolder.getApiProviderLangSettings
import com.lagradost.cloudstream3.AllLanguagesName
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.home.HomeFragment.Companion.bindChips
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTvSettings
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setUpToolbar
import com.lagradost.cloudstream3.ui.settings.appLanguages
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showMultiDialog
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import kotlinx.android.synthetic.main.fragment_plugins.*
import kotlinx.android.synthetic.main.tvtypes_chips.*
import kotlinx.android.synthetic.main.tvtypes_chips_scroll.*

const val PLUGINS_BUNDLE_NAME = "name"
const val PLUGINS_BUNDLE_URL = "url"
const val PLUGINS_BUNDLE_LOCAL = "isLocal"

class PluginsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_plugins, container, false)
    }

    private val pluginViewModel: PluginsViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Since the ViewModel is getting reused the tvTypes must be cleared between uses
        pluginViewModel.tvTypes.clear()
        pluginViewModel.languages = listOf()
        pluginViewModel.search(null)

        // Filter by language set on preferred media
        activity?.let {
            val providerLangs = it.getApiProviderLangSettings().toList()
            if (!providerLangs.contains(AllLanguagesName)) {
                pluginViewModel.languages = mutableListOf("none") + providerLangs
                //Log.i("DevDebug", "providerLang => ${pluginViewModel.languages.toJson()}")
            }
        }

        val name = arguments?.getString(PLUGINS_BUNDLE_NAME)
        val url = arguments?.getString(PLUGINS_BUNDLE_URL)
        val isLocal = arguments?.getBoolean(PLUGINS_BUNDLE_LOCAL) == true

        if (url == null || name == null) {
            activity?.onBackPressed()
            return
        }

        setUpToolbar(name)

        settings_toolbar?.setOnMenuItemClickListener { menuItem ->
            when (menuItem?.itemId) {
                R.id.download_all -> {
                    PluginsViewModel.downloadAll(activity, url, pluginViewModel)
                }
                R.id.lang_filter -> {
                    val tempLangs = appLanguages.toMutableList()
                    val languageCodes = mutableListOf("none") + tempLangs.map { (_, _, iso) -> iso }
                    val languageNames =
                        mutableListOf(getString(R.string.no_data)) + tempLangs.map { (emoji, name, iso) ->
                            val flag =
                                emoji.ifBlank { SubtitleHelper.getFlagFromIso(iso) ?: "ERROR" }
                            "$flag $name"
                        }
                    val selectedList =
                        pluginViewModel.languages.map { it -> languageCodes.indexOf(it) }

                    activity?.showMultiDialog(
                        languageNames,
                        selectedList,
                        getString(R.string.provider_lang_settings),
                        {}) { newList ->
                        pluginViewModel.languages = newList.map { it -> languageCodes[it] }
                        pluginViewModel.updateFilteredPlugins()
                    }
                }
                else -> {}
            }
            return@setOnMenuItemClickListener true
        }

        val searchView =
            settings_toolbar?.menu?.findItem(R.id.search_button)?.actionView as? SearchView

        // Don't go back if active query
        settings_toolbar?.setNavigationOnClickListener {
            if (searchView?.isIconified == false) {
                searchView.isIconified = true
            } else {
                activity?.onBackPressed()
            }
        }

//        searchView?.onActionViewCollapsed = {
//            pluginViewModel.search(null)
//        }

        // Because onActionViewCollapsed doesn't wanna work we need this workaround :(
        searchView?.setOnQueryTextFocusChangeListener { _, hasFocus ->
            if (!hasFocus) pluginViewModel.search(null)
        }

        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                pluginViewModel.search(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                pluginViewModel.search(newText)
                return true
            }
        })


        plugin_recycler_view?.adapter =
            PluginAdapter {
                pluginViewModel.handlePluginAction(activity, url, it, isLocal)
            }

        if (isTvSettings()) {
            // Scrolling down does not reveal the whole RecyclerView on TV, add to bypass that.
            plugin_recycler_view?.setPadding(0, 0, 0, 200.toPx)
        }

        observe(pluginViewModel.filteredPlugins) { (scrollToTop, list) ->
            (plugin_recycler_view?.adapter as? PluginAdapter)?.updateList(list)

            if (scrollToTop)
                plugin_recycler_view?.scrollToPosition(0)
        }

        if (isLocal) {
            // No download button and no categories on local
            settings_toolbar?.menu?.findItem(R.id.download_all)?.isVisible = false
            settings_toolbar?.menu?.findItem(R.id.lang_filter)?.isVisible = false
            pluginViewModel.updatePluginListLocal()
            tv_types_scroll_view?.isVisible = false
        } else {
            pluginViewModel.updatePluginList(context, url)
            tv_types_scroll_view?.isVisible = true

            bindChips(home_select_group, emptyList(), TvType.values().toList()) { list ->
                pluginViewModel.tvTypes.clear()
                pluginViewModel.tvTypes.addAll(list.map { it.name })
                pluginViewModel.updateFilteredPlugins()
            }
        }
    }

    companion object {
        fun newInstance(name: String, url: String, isLocal: Boolean): Bundle {
            return Bundle().apply {
                putString(PLUGINS_BUNDLE_NAME, name)
                putString(PLUGINS_BUNDLE_URL, url)
                putBoolean(PLUGINS_BUNDLE_LOCAL, isLocal)
            }
        }

//        class RepoSearchView(context: Context) : android.widget.SearchView(context) {
//            var onActionViewCollapsed = {}
//
//            override fun onActionViewCollapsed() {
//                onActionViewCollapsed()
//            }
//        }

    }
}