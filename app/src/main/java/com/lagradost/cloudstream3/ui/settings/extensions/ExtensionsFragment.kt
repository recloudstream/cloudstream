package com.lagradost.cloudstream3.ui.settings.extensions

import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.MainActivity.Companion.afterRepositoryLoadedEvent
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.Some
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.ui.result.setText
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTrueTvSettings
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setUpToolbar
import com.lagradost.cloudstream3.utils.AppUtils.downloadAllPluginsDialog
import com.lagradost.cloudstream3.utils.AppUtils.setDefaultFocus
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.widget.LinearRecycleViewLayoutManager
import kotlinx.android.synthetic.main.add_repo_input.*
import kotlinx.android.synthetic.main.fragment_extensions.*

class ExtensionsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_extensions, container, false)
    }

    private fun View.setLayoutWidth(weight: Int) {
        val param = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.MATCH_PARENT,
            weight.toFloat()
        )
        this.layoutParams = param
    }

    private val extensionViewModel: ExtensionsViewModel by activityViewModels()

    override fun onResume() {
        super.onResume()
        afterRepositoryLoadedEvent += ::reloadRepositories
    }

    override fun onStop() {
        super.onStop()
        afterRepositoryLoadedEvent -= ::reloadRepositories
    }

    private fun reloadRepositories(success: Boolean = true) {
        extensionViewModel.loadStats()
        extensionViewModel.loadRepositories()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //context?.fixPaddingStatusbar(extensions_root)

        setUpToolbar(R.string.extensions)

        repo_recycler_view?.adapter = RepoAdapter(false, {
            findNavController().navigate(
                R.id.navigation_settings_extensions_to_navigation_settings_plugins,
                PluginsFragment.newInstance(
                    it.name,
                    it.url,
                    false
                )
            )
        }, { repo ->
            // Prompt user before deleting repo
            main {
                val builder = AlertDialog.Builder(context ?: view.context)
                val dialogClickListener =
                    DialogInterface.OnClickListener { _, which ->
                        when (which) {
                            DialogInterface.BUTTON_POSITIVE -> {
                                ioSafe {
                                    RepositoryManager.removeRepository(view.context, repo)
                                    extensionViewModel.loadStats()
                                    extensionViewModel.loadRepositories()
                                }
                            }
                            DialogInterface.BUTTON_NEGATIVE -> {}
                        }
                    }

                builder.setTitle(R.string.delete_repository)
                    .setMessage(
                        context?.getString(R.string.delete_repository_plugins)
                    )
                    .setPositiveButton(R.string.delete, dialogClickListener)
                    .setNegativeButton(R.string.cancel, dialogClickListener)
                    .show().setDefaultFocus()
            }
        })

        observe(extensionViewModel.repositories) {
            repo_recycler_view?.isVisible = it.isNotEmpty()
            blank_repo_screen?.isVisible = it.isEmpty()
            (repo_recycler_view?.adapter as? RepoAdapter)?.updateList(it)
        }

        repo_recycler_view?.apply {
            context?.let { ctx ->
                layoutManager = LinearRecycleViewLayoutManager(ctx, nextFocusUpId, nextFocusDownId)
            }
        }

//        list_repositories?.setOnClickListener {
//            // Open webview on tv if browser fails
//            val isTv = isTvSettings()
//            openBrowser(PUBLIC_REPOSITORIES_LIST, isTv, this)
//
//            // Set clipboard on TV because the browser might not exist or work properly
//            if (isTv) {
//                val serviceClipboard =
//                    (activity?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?)
//                        ?: return@setOnClickListener
//                val clip = ClipData.newPlainText("Repository url", PUBLIC_REPOSITORIES_LIST)
//                serviceClipboard.setPrimaryClip(clip)
//            }
//        }

        observe(extensionViewModel.pluginStats) {
            when (it) {
                is Some.Success -> {
                    val value = it.value

                    plugin_storage_appbar?.isVisible = true
                    if (value.total == 0) {
                        plugin_download?.setLayoutWidth(1)
                        plugin_disabled?.setLayoutWidth(0)
                        plugin_not_downloaded?.setLayoutWidth(0)
                    } else {
                        plugin_download?.setLayoutWidth(value.downloaded)
                        plugin_disabled?.setLayoutWidth(value.disabled)
                        plugin_not_downloaded?.setLayoutWidth(value.notDownloaded)
                    }
                    plugin_not_downloaded_txt.setText(value.notDownloadedText)
                    plugin_disabled_txt.setText(value.disabledText)
                    plugin_download_txt.setText(value.downloadedText)
                }
                is Some.None -> {
                    plugin_storage_appbar?.isVisible = false
                }
            }
        }

        plugin_storage_appbar?.setOnClickListener {
            findNavController().navigate(
                R.id.navigation_settings_extensions_to_navigation_settings_plugins,
                PluginsFragment.newInstance(
                    getString(R.string.extensions),
                    "",
                    true
                )
            )
        }

        val addRepositoryClick = View.OnClickListener {
            val builder =
                AlertDialog.Builder(context ?: return@OnClickListener, R.style.AlertDialogCustom)
                    .setView(R.layout.add_repo_input)

            val dialog = builder.create()
            dialog.show()
            (activity?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager?)?.primaryClip?.getItemAt(
                0
            )?.text?.toString()?.let { copy ->
                dialog.repo_url_input?.setText(copy)
            }

//            dialog.list_repositories?.setOnClickListener {
//                // Open webview on tv if browser fails
//                openBrowser(PUBLIC_REPOSITORIES_LIST, isTvSettings(), this)
//                dialog.dismissSafe()
//            }

//            dialog.text2?.text = provider.name
            dialog.apply_btt?.setOnClickListener secondListener@{
                val name = dialog.repo_name_input?.text?.toString()
                ioSafe {
                    val url = dialog.repo_url_input?.text?.toString()
                        ?.let { it1 -> RepositoryManager.parseRepoUrl(it1) }
                    if (url.isNullOrBlank()) {
                        main {
                            showToast(activity, R.string.error_invalid_data, Toast.LENGTH_SHORT)
                        }
                    } else {
                        val fixedName = if (!name.isNullOrBlank()) name
                        else RepositoryManager.parseRepository(url)?.name ?: "No name"

                        val newRepo = RepositoryData(fixedName, url)
                        RepositoryManager.addRepository(newRepo)
                        extensionViewModel.loadStats()
                        extensionViewModel.loadRepositories()
                        this@ExtensionsFragment.activity?.downloadAllPluginsDialog(url, fixedName)
                    }
                }
                dialog.dismissSafe(activity)
            }
            dialog.cancel_btt?.setOnClickListener {
                dialog.dismissSafe(activity)
            }
        }

        val isTv = isTrueTvSettings()
        add_repo_button?.isGone = isTv
        add_repo_button_imageview_holder?.isVisible = isTv

        // Band-aid for Fire TV
        plugin_storage_appbar?.isFocusableInTouchMode = isTv
        add_repo_button_imageview?.isFocusableInTouchMode = isTv

        add_repo_button?.setOnClickListener(addRepositoryClick)
        add_repo_button_imageview?.setOnClickListener(addRepositoryClick)

        reloadRepositories()
    }
}