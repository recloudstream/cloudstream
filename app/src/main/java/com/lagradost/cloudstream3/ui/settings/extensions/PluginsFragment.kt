package com.lagradost.cloudstream3.ui.settings.extensions

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import com.lagradost.cloudstream3.AllLanguagesName
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.databinding.FragmentPluginsBinding
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.ui.home.HomeFragment.Companion.bindChips
import com.lagradost.cloudstream3.ui.result.FOCUS_SELF
import com.lagradost.cloudstream3.ui.result.setLinearListLayout
import com.lagradost.cloudstream3.ui.setRecycledViewPool
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setSystemBarsPadding
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setToolBarScrollFlags
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setUpToolbar
import com.lagradost.cloudstream3.utils.AppContextUtils.getApiProviderLangSettings
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showMultiDialog
import com.lagradost.cloudstream3.utils.SubtitleHelper.getNameNextToFlagEmoji

const val PLUGINS_BUNDLE_NAME = "name"
const val PLUGINS_BUNDLE_URL = "url"
const val PLUGINS_BUNDLE_LOCAL = "isLocal"

class PluginsFragment : BaseFragment<FragmentPluginsBinding>(
    BindingCreator.Inflate(FragmentPluginsBinding::inflate),
) {

    private val pluginViewModel: PluginsViewModel by activityViewModels()

    override fun onDestroyView() {
        pluginViewModel.clear() // clear for the next observe
        super.onDestroyView()
    }

    override fun fixLayout(view: View) {
        setSystemBarsPadding()
    }

    override fun onBindingCreated(binding: FragmentPluginsBinding) {
        // Since the ViewModel is getting reused the tvTypes must be cleared between uses
        pluginViewModel.tvTypes.clear()
        pluginViewModel.selectedLanguages = listOf()
        pluginViewModel.clear()

        // Filter by language set on preferred media
        activity?.let {
            val providerLangs = it.getApiProviderLangSettings().toList()
            if (!providerLangs.contains(AllLanguagesName)) {
                pluginViewModel.selectedLanguages = mutableListOf("none") + providerLangs
            }
        }

        val name = arguments?.getString(PLUGINS_BUNDLE_NAME)
        val url = arguments?.getString(PLUGINS_BUNDLE_URL)
        val isLocal = arguments?.getBoolean(PLUGINS_BUNDLE_LOCAL) == true

        if (url == null || name == null) {
            dispatchBackPressed()
            return
        }

        setToolBarScrollFlags()
        setUpToolbar(name)
        setupToolbar(binding, url)
        setupRecyclerView(binding, url, isLocal)
        setupSelectionToolbar(binding)

        if (isLocal) {
            setupLocalMode(binding, url)
        } else {
            setupRemoteMode(binding, url)
        }
    }

    private fun setupToolbar(binding: FragmentPluginsBinding, url: String) {
        binding.settingsToolbar.apply {
            setOnMenuItemClickListener { menuItem ->
                when (menuItem?.itemId) {
                    R.id.download_all -> {
                        PluginsViewModel.downloadAll(activity, url, pluginViewModel)
                    }

                    R.id.lang_filter -> {
                        showLanguageFilter()
                    }

                    else -> {}
                }
                return@setOnMenuItemClickListener true
            }

            val searchView = menu?.findItem(R.id.search_button)?.actionView as? SearchView
            setNavigationOnClickListener {
                if (searchView?.isIconified == false) {
                    searchView.isIconified = true
                } else {
                    dispatchBackPressed()
                }
            }
            searchView?.setOnQueryTextFocusChangeListener { _, hasFocus ->
                if (!hasFocus) pluginViewModel.search(null)
            }

            searchView?.setOnQueryTextListener(
                object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String?): Boolean {
                        pluginViewModel.search(query)
                        return true
                    }

                    override fun onQueryTextChange(newText: String?): Boolean {
                        pluginViewModel.search(newText)
                        return true
                    }
                }
            )
        }
    }

    private fun showLanguageFilter() {
        val languagesTagName = pluginViewModel.pluginLanguages
            .map { langTag ->
                Pair(
                    langTag,
                    getNameNextToFlagEmoji(langTag) ?: langTag
                )
            }
            .sortedBy {
                it.second.substringAfter("\u00a0").lowercase()
            }
            .toMutableList()

        if (languagesTagName.remove(Pair("none", "none"))) {
            languagesTagName.add(0, Pair("none", getString(R.string.no_data)))
        }

        val currentIndexList = pluginViewModel.selectedLanguages.map { langTag ->
            languagesTagName.indexOfFirst { lang -> lang.first == langTag }
        }

        activity?.showMultiDialog(
            languagesTagName.map { it.second },
            currentIndexList,
            getString(R.string.provider_lang_settings),
            {}
        ) { selectedList ->
            pluginViewModel.selectedLanguages =
                selectedList.map { languagesTagName[it].first }
            pluginViewModel.updateFilteredPlugins()
        }
    }

    private fun setupRecyclerView(binding: FragmentPluginsBinding, url: String, isLocal: Boolean) {
        binding.pluginRecyclerView.apply {
            setLinearListLayout(
                isHorizontal = false,
                nextDown = FOCUS_SELF,
                nextRight = FOCUS_SELF,
            )
            setRecycledViewPool(PluginAdapter.sharedPool)
            adapter = PluginAdapter(
                iconClickCallback = {
                    pluginViewModel.handlePluginAction(activity, url, it, isLocal)
                },
                longClickCallback = {
                    pluginViewModel.toggleSelectionMode(enabled = true)
                    pluginViewModel.toggleSelection(it.second.url)
                },
                clickCallback = {
                    pluginViewModel.toggleSelection(it.second.url)
                },
            )
        }

        observe(pluginViewModel.filteredPlugins) { (scrollToTop, list) ->
            (binding.pluginRecyclerView.adapter as? PluginAdapter)?.submitList(list)
            if (scrollToTop) {
                binding.pluginRecyclerView.scrollToPosition(0)
            }

            val selectedCount = list.count { it.isSelected && it.isInSelectionMode }
            binding.selectionToolbar.apply {
                isVisible = list.any { it.isInSelectionMode }
                binding.settingsToolbar.isVisible = !isVisible
                title = "$selectedCount Selected"
            }
        }
    }

    private fun setupSelectionToolbar(binding: FragmentPluginsBinding) {
        binding.selectionToolbar.apply {
            inflateMenu(R.menu.plugin_selection)
            setNavigationOnClickListener {
                pluginViewModel.toggleSelectionMode(enabled = false)
            }
            setOnMenuItemClickListener { menuItem ->
                handleBatchAction(menuItem.itemId)
                true
            }
        }
    }

    private fun handleBatchAction(itemId: Int) {
        val action = when (itemId) {
            R.id.action_batch_download -> PluginsViewModel.BatchAction.Download
            R.id.action_batch_enable -> PluginsViewModel.BatchAction.Enable
            R.id.action_batch_disable -> PluginsViewModel.BatchAction.Disable
            R.id.action_batch_delete -> PluginsViewModel.BatchAction.Delete
            R.id.action_batch_move -> {
                showMoveToFolderDialog()
                null
            }
            else -> null
        }
        action?.let {
            pluginViewModel.batchAction(activity, it)
        }
    }

    private fun showMoveToFolderDialog() {
        val folders = DataStoreHelper.getExtensionFolders()
        if (folders.isEmpty()) {
            showToast("Create a folder first in the Extensions screen")
        } else {
            val names = folders.keys.toList()
            activity?.showDialog(
                names,
                -1,
                "Move to Folder",
                showApply = true,
                dismissCallback = {}
            ) { index: Int ->
                val folderName = names[index]
                val selected = pluginViewModel.selectedPlugins.toList()
                val currentFolders = DataStoreHelper.getExtensionFolders().toMutableMap()
                val currentList =
                    currentFolders[folderName]?.toMutableList()
                        ?: mutableListOf()
                currentList.addAll(selected)
                currentFolders[folderName] = currentList.distinct()
                DataStoreHelper.setExtensionFolders(currentFolders)
                showToast("Moved to $folderName")
                pluginViewModel.toggleSelectionMode(enabled = false)
            }
        }
    }

    private fun setupLocalMode(binding: FragmentPluginsBinding, url: String) {
        binding.settingsToolbar.menu?.findItem(R.id.download_all)?.isVisible = false
        binding.settingsToolbar.menu?.findItem(R.id.lang_filter)?.isVisible = false
        pluginViewModel.updatePluginListLocal(
            filterDisabled = url == "disabled",
            folderName = if (url.startsWith("folder://")) url.removePrefix("folder://") else null
        )
        binding.tvtypesChipsScroll.root.isVisible = false
    }

    private fun setupRemoteMode(binding: FragmentPluginsBinding, url: String) {
        pluginViewModel.updatePluginList(context, url)
        binding.tvtypesChipsScroll.root.isVisible = true
        binding.settingsToolbar.menu?.findItem(R.id.download_all)?.isVisible = BuildConfig.DEBUG

        bindChips(
            binding.tvtypesChipsScroll.tvtypesChips,
            emptyList(),
            TvType.entries.toList(),
            callback = { list ->
                pluginViewModel.tvTypes.clear()
                pluginViewModel.tvTypes.addAll(list.map { it.name })
                pluginViewModel.updateFilteredPlugins()
            },
            nextFocusDown = R.id.plugin_recycler_view,
            nextFocusUp = null,
        )
    }

    companion object {
        fun newInstance(name: String, url: String, isLocal: Boolean): Bundle {
            return Bundle().apply {
                putString(PLUGINS_BUNDLE_NAME, name)
                putString(PLUGINS_BUNDLE_URL, url)
                putBoolean(PLUGINS_BUNDLE_LOCAL, isLocal)
            }
        }
    }
}
