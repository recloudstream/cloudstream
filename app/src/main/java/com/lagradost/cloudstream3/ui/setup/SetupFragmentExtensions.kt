package com.lagradost.cloudstream3.ui.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.plugins.RepositoryManager.PREBUILT_REPOSITORIES
import com.lagradost.cloudstream3.ui.settings.extensions.PluginsViewModel
import com.lagradost.cloudstream3.ui.settings.extensions.RepoAdapter
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import kotlinx.android.synthetic.main.fragment_extensions.*
import kotlinx.android.synthetic.main.fragment_setup_media.*


class SetupFragmentExtensions : Fragment() {
    companion object {
        const val SETUP_EXTENSION_BUNDLE_IS_SETUP = "isSetup"
        fun newInstance(isSetup: Boolean): Bundle {
            return Bundle().apply {
                putBoolean(SETUP_EXTENSION_BUNDLE_IS_SETUP, isSetup)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_setup_extensions, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        context?.fixPaddingStatusbar(setup_root)
        val isSetup = arguments?.getBoolean(SETUP_EXTENSION_BUNDLE_IS_SETUP) ?: false

        with(context) {
            if (this == null) return

            repo_recycler_view?.adapter = RepoAdapter(true, {}, {
                PluginsViewModel.downloadAll(activity, it.url, null)
            }).apply { updateList(PREBUILT_REPOSITORIES) }

            if (!isSetup) {
                next_btt.setText(R.string.setup_done)
            }
            prev_btt?.isVisible = isSetup

            next_btt?.setOnClickListener {
                // Continue setup
                if (isSetup)
                    findNavController().navigate(R.id.action_navigation_setup_extensions_to_navigation_setup_provider_languages)
                else
                    findNavController().popBackStack()
            }

            prev_btt?.setOnClickListener {
                findNavController().popBackStack()
            }
        }
    }


}