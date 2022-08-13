package com.lagradost.cloudstream3.ui.settings.extensions

import android.content.Context
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setUpToolbar
import kotlinx.android.synthetic.main.fragment_plugins.*

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
                else -> {}
            }
            return@setOnMenuItemClickListener true
        }

        val searchView =
            settings_toolbar?.menu?.findItem(R.id.search_button)?.actionView as? RepoSearchView

        // Don't go back if active query
        settings_toolbar?.setNavigationOnClickListener {
            if (searchView?.isIconified == false) {
                searchView?.isIconified = true
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

        searchView?.setOnQueryTextListener(object : android.widget.SearchView.OnQueryTextListener {
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

        observe(pluginViewModel.plugins) {
            (plugin_recycler_view?.adapter as? PluginAdapter?)?.updateList(it)
            plugin_recycler_view?.scrollToPosition(0)
        }

        if (isLocal) {
            // No download button
            settings_toolbar?.menu?.findItem(R.id.download_all)?.isVisible = false
            pluginViewModel.updatePluginListLocal()
        } else {
            pluginViewModel.updatePluginList(url)
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

        class RepoSearchView(context: Context) : android.widget.SearchView(context) {
//            var onActionViewCollapsed = {}
//
//            override fun onActionViewCollapsed() {
//                onActionViewCollapsed()
//            }
        }

    }
}