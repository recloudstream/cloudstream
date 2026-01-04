package com.lagradost.cloudstream3.ui.setup

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.MainActivity.Companion.afterRepositoryLoadedEvent
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentSetupExtensionsBinding
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.plugins.RepositoryManager.PREBUILT_REPOSITORIES
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.ui.settings.extensions.PluginsViewModel
import com.lagradost.cloudstream3.ui.settings.extensions.RepoAdapter
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding

class SetupFragmentExtensions : BaseFragment<FragmentSetupExtensionsBinding>(
    BaseFragment.BindingCreator.Inflate(FragmentSetupExtensionsBinding::inflate)
) {
    companion object {
        const val SETUP_EXTENSION_BUNDLE_IS_SETUP = "isSetup"

        /**
         * If false then this is treated a singular screen with a done button
         * */
        fun newInstance(isSetup: Boolean): Bundle {
            return Bundle().apply {
                putBoolean(SETUP_EXTENSION_BUNDLE_IS_SETUP, isSetup)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        afterRepositoryLoadedEvent += ::setRepositories
    }

    override fun onStop() {
        super.onStop()
        afterRepositoryLoadedEvent -= ::setRepositories
    }

    override fun fixLayout(view: View) {
        fixSystemBarsPadding(view)
    }

    private fun setRepositories(success: Boolean = true) {
        main {
            val repositories = RepositoryManager.getRepositories() + PREBUILT_REPOSITORIES
            val hasRepos = repositories.isNotEmpty()
            binding?.repoRecyclerView?.isVisible = hasRepos
            binding?.blankRepoScreen?.isVisible = !hasRepos

            if (hasRepos) {
                binding?.repoRecyclerView?.adapter = RepoAdapter(true, {}, {
                    PluginsViewModel.downloadAll(activity, it.url, null)
                }).apply { submitList(repositories.toList()) }
            }
//            else {
//                list_repositories?.setOnClickListener {
//                    // Open webview on tv if browser fails
//                    openBrowser(PUBLIC_REPOSITORIES_LIST, isTvSettings(), this)
//                }
//            }
        }
    }

    override fun onBindingCreated(binding: FragmentSetupExtensionsBinding) {
        val isSetup = arguments?.getBoolean(SETUP_EXTENSION_BUNDLE_IS_SETUP) ?: false

        safe {
            setRepositories()
            binding.apply {
                if (!isSetup) {
                    nextBtt.setText(R.string.setup_done)
                }
                prevBtt.isVisible = isSetup

                nextBtt.setOnClickListener {
                    // Continue setup
                    if (isSetup)
                        if (
                        // If any available languages
                            synchronized(apis) { apis.distinctBy { it.lang }.size > 1 }
                        ) {
                            findNavController().navigate(R.id.action_navigation_setup_extensions_to_navigation_setup_provider_languages)
                        } else {
                            findNavController().navigate(R.id.action_navigation_setup_extensions_to_navigation_setup_media)
                        }
                    else
                        findNavController().navigate(R.id.navigation_home)
                }

                prevBtt.setOnClickListener {
                    findNavController().navigate(R.id.navigation_setup_language)
                }
            }
        }
    }
}
