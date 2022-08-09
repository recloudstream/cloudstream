package com.lagradost.cloudstream3.ui.settings.extensions

import android.os.Bundle
import android.view.*
import androidx.appcompat.view.menu.MenuBuilder
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setUpToolbar
import kotlinx.android.synthetic.main.fragment_plugins.*

const val PLUGINS_BUNDLE_NAME = "name"
const val PLUGINS_BUNDLE_URL = "url"

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

        plugin_recycler_view?.adapter =
            PluginAdapter {
                pluginViewModel.handlePluginAction(activity, url, it)
            }

        observe(pluginViewModel.plugins) {
            (plugin_recycler_view?.adapter as? PluginAdapter?)?.updateList(it)
        }

        pluginViewModel.updatePluginList(url)
    }

    companion object {
        fun newInstance(name: String, url: String): Bundle {
            return Bundle().apply {
                putString(PLUGINS_BUNDLE_NAME, name)
                putString(PLUGINS_BUNDLE_URL, url)
            }
        }
    }
}