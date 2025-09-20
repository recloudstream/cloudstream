package com.lagradost.cloudstream3.ui.settings.extensions

import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import androidx.core.view.marginTop
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.MainActivity.Companion.afterRepositoryLoadedEvent
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.AddRepoInputBinding
import com.lagradost.cloudstream3.databinding.FragmentExtensionsBinding
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.mvvm.observeNullable
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.ui.result.FOCUS_SELF
import com.lagradost.cloudstream3.ui.result.setLinearListLayout
import com.lagradost.cloudstream3.utils.setText
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setToolBarScrollFlags
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setUpToolbar
import com.lagradost.cloudstream3.utils.AppContextUtils.addRepositoryDialog
import com.lagradost.cloudstream3.utils.AppContextUtils.setDefaultFocus
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe

class ExtensionsFragment : Fragment() {
    var binding: FragmentExtensionsBinding? = null
    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val localBinding = FragmentExtensionsBinding.inflate(inflater, container, false)
        binding = localBinding
        return localBinding.root//inflater.inflate(R.layout.fragment_extensions, container, false)
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
        setToolBarScrollFlags()

        binding?.repoRecyclerView?.apply {
            setLinearListLayout(
                isHorizontal = false,
                nextUp = R.id.settings_toolbar, //FOCUS_SELF, // back has no id so we cant :pensive:
                nextDown = R.id.plugin_storage_appbar,
                nextRight = FOCUS_SELF,
                nextLeft = R.id.nav_rail_view
            )

            if (!isLayout(TV))
                binding?.addRepoButton?.let { button ->
                    button.post {
                        setPadding(
                            paddingLeft,
                            paddingTop,
                            paddingRight,
                            button.measuredHeight + button.marginTop + button.marginBottom
                        )
                    }
                }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                    val dy = scrollY - oldScrollY
                    if (dy > 0) { //check for scroll down
                        binding?.addRepoButton?.shrink() // hide
                    } else if (dy < -5) {
                        binding?.addRepoButton?.extend() // show
                    }
                }
            }
            adapter = RepoAdapter(false, {
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
        }

        observe(extensionViewModel.repositories) {
            binding?.repoRecyclerView?.isVisible = it.isNotEmpty()
            binding?.blankRepoScreen?.isVisible = it.isEmpty()
            (binding?.repoRecyclerView?.adapter as? RepoAdapter)?.updateList(it)
        }

        /*binding?.repoRecyclerView?.apply {
            context?.let { ctx ->
                layoutManager = LinearRecycleViewLayoutManager(ctx, nextFocusUpId, nextFocusDownId)
            }
        }*/

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

        observeNullable(extensionViewModel.pluginStats) { value ->
            binding?.apply {
                if (value == null) {
                    pluginStorageAppbar.isVisible = false

                    return@observeNullable
                }

                pluginStorageAppbar.isVisible = true
                if (value.total == 0) {
                    pluginDownload.setLayoutWidth(1)
                    pluginDisabled.setLayoutWidth(0)
                    pluginNotDownloaded.setLayoutWidth(0)
                } else {
                    pluginDownload.setLayoutWidth(value.downloaded)
                    pluginDisabled.setLayoutWidth(value.disabled)
                    pluginNotDownloaded.setLayoutWidth(value.notDownloaded)
                }
                pluginNotDownloadedTxt.setText(value.notDownloadedText)
                pluginDisabledTxt.setText(value.disabledText)
                pluginDownloadTxt.setText(value.downloadedText)
            }
        }

        binding?.pluginStorageAppbar?.setOnClickListener {
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
            val ctx = context ?: return@OnClickListener
            val binding = AddRepoInputBinding.inflate(LayoutInflater.from(ctx), null, false)
            val builder =
                AlertDialog.Builder(ctx, R.style.AlertDialogCustom)
                    .setView(binding.root)

            val dialog = builder.create()
            dialog.show()
            (activity?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager?)?.primaryClip?.getItemAt(
                0
            )?.text?.toString()?.let { copiedText ->
                if (copiedText.contains(RepoAdapter.SHAREABLE_REPO_SEPARATOR)) {
                    // text is of format <repository name> : <repository url>
                    val (name, url) = copiedText.split(RepoAdapter.SHAREABLE_REPO_SEPARATOR, limit = 2)
                    binding.repoUrlInput.setText(url.trim())
                    binding.repoNameInput.setText(name.trim())
                } else {
                    binding.repoUrlInput.setText(copiedText)
                }
            }

//            dialog.list_repositories?.setOnClickListener {
//                // Open webview on tv if browser fails
//                openBrowser(PUBLIC_REPOSITORIES_LIST, isTvSettings(), this)
//                dialog.dismissSafe()
//            }

//            dialog.text2?.text = provider.name
            binding.applyBtt.setOnClickListener secondListener@{
                val name = binding.repoNameInput.text?.toString()
                ioSafe {
                    val url = binding.repoUrlInput.text?.toString()
                        ?.let { it1 -> RepositoryManager.parseRepoUrl(it1) }
                    if (url.isNullOrBlank()) {
                        main {
                            showToast(R.string.error_invalid_data, Toast.LENGTH_SHORT)
                        }
                    } else {
                        val repository = RepositoryManager.parseRepository(url)

                        // Exit if wrong repository
                        if (repository == null) {
                            showToast(R.string.no_repository_found_error, Toast.LENGTH_LONG)
                            return@ioSafe
                        }

                        val fixedName = if (!name.isNullOrBlank()) name
                        else repository.name
                        val newRepo = RepositoryData(repository.iconUrl,fixedName, url)
                        RepositoryManager.addRepository(newRepo)
                        extensionViewModel.loadStats()
                        extensionViewModel.loadRepositories()

                        val plugins = RepositoryManager.getRepoPlugins(url)
                        if (plugins.isNullOrEmpty()) {
                            showToast(R.string.no_plugins_found_error, Toast.LENGTH_LONG)
                        } else {
                            this@ExtensionsFragment.activity?.addRepositoryDialog(
                                fixedName,
                                url,
                            )
                        }
                    }
                }
                dialog.dismissSafe(activity)
            }
            binding.cancelBtt.setOnClickListener {
                dialog.dismissSafe(activity)
            }
        }

        val isTv = isLayout(TV)
        binding?.apply {
            addRepoButton.isGone = isTv
            addRepoButtonImageviewHolder.isVisible = isTv

            // Band-aid for Fire TV
            pluginStorageAppbar.isFocusableInTouchMode = isTv
            addRepoButtonImageview.isFocusableInTouchMode = isTv

            addRepoButton.setOnClickListener(addRepositoryClick)
            addRepoButtonImageview.setOnClickListener(addRepositoryClick)
        }
        reloadRepositories()
    }
}