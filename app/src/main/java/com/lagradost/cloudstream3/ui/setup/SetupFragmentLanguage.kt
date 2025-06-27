package com.lagradost.cloudstream3.ui.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.ArrayAdapter
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentSetupLanguageBinding
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.ui.settings.appLanguages
import com.lagradost.cloudstream3.ui.settings.getCurrentLocale
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar

const val HAS_DONE_SETUP_KEY = "HAS_DONE_SETUP"

class SetupFragmentLanguage : Fragment() {
    var binding: FragmentSetupLanguageBinding? = null

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val localBinding = FragmentSetupLanguageBinding.inflate(inflater, container, false)
        binding = localBinding
        return localBinding.root
        //return inflater.inflate(R.layout.fragment_setup_language, container, false)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // We don't want a crash for all users
        safe {
            fixPaddingStatusbar(binding?.setupRoot)

            val ctx = context ?: return@safe
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(ctx)

            val arrayAdapter =
                ArrayAdapter<String>(ctx, R.layout.sort_bottom_single_choice)

            binding?.apply {
                // Icons may crash on some weird android versions?
                safe {
                    val drawable = when {
                        BuildConfig.DEBUG -> R.drawable.cloud_2_gradient_debug
                        BuildConfig.FLAVOR == "prerelease" -> R.drawable.cloud_2_gradient_beta
                        else -> R.drawable.cloud_2_gradient
                    }
                    appIconImage.setImageDrawable(ContextCompat.getDrawable(ctx, drawable))
                }

                val current = getCurrentLocale(ctx)
                val languageCodes = appLanguages.map { it.third }
                val languageNames = appLanguages.map { (emoji, name, iso) ->
                    val flag = emoji.ifBlank { SubtitleHelper.getFlagFromIso(iso) ?: "ERROR" }
                    "$flag $name"
                }
                val index = languageCodes.indexOf(current)

                arrayAdapter.addAll(languageNames)
                listview1.adapter = arrayAdapter
                listview1.choiceMode = AbsListView.CHOICE_MODE_SINGLE
                listview1.setItemChecked(index, true)

                listview1.setOnItemClickListener { _, _, position, _ ->
                    val code = languageCodes[position]
                    CommonActivity.setLocale(activity, code)
                    settingsManager.edit().putString(getString(R.string.locale_key), code)
                        .apply()
                    activity?.recreate()
                }

                nextBtt.setOnClickListener {
                    // If no plugins go to plugins page
                    val nextDestination = if (
                        PluginManager.getPluginsOnline().isEmpty()
                        && PluginManager.getPluginsLocal().isEmpty()
                    //&& PREBUILT_REPOSITORIES.isNotEmpty()
                    ) R.id.action_navigation_global_to_navigation_setup_extensions
                    else R.id.action_navigation_setup_language_to_navigation_setup_provider_languages

                    findNavController().navigate(
                        nextDestination,
                        SetupFragmentExtensions.newInstance(true)
                    )
                }

                skipBtt.setOnClickListener {
                    findNavController().navigate(R.id.navigation_home)
                }
            }

        }
    }
}