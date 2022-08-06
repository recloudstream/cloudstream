package com.lagradost.cloudstream3.ui.settings.extensions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import kotlinx.android.synthetic.main.fragment_extensions.*

const val PLUGINS_BUNDLE_NAME = "name"
const val PLUGINS_BUNDLE_URL = "url"

class PluginsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_extensions, container, false)
    }

    private val extensionViewModel: ExtensionsViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        context?.fixPaddingStatusbar(extensions_root)

        val name = arguments?.getString(PLUGINS_BUNDLE_NAME)
        val url = arguments?.getString(PLUGINS_BUNDLE_URL)
        if (url == null) {
            activity?.onBackPressed()
            return
        }

        ioSafe {
            val plugins = extensionViewModel.getPlugins(url)
            println("GET PLUGINS $plugins")
            main {
                repo_recycler_view?.adapter = PluginAdapter(plugins) { plugin, isDownloaded ->
                    ioSafe {
                        val (success, message) = if (isDownloaded) {
                            PluginManager.deletePlugin(view.context, plugin.url, plugin.name) to R.string.plugin_deleted
                        } else {
                            PluginManager.downloadPlugin(
                                view.context,
                                plugin.url,
                                plugin.name
                            ) to R.string.plugin_loaded
                        }

                        println("Success: $success")
                        if (success) {
                            main {
                                showToast(activity, message, Toast.LENGTH_SHORT)
                                this@PluginAdapter.reloadStoredPlugins()
                                // Dirty and needs a fix
                                repo_recycler_view?.adapter?.notifyDataSetChanged()
                            }
                        }
                    }
                }
            }
        }
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