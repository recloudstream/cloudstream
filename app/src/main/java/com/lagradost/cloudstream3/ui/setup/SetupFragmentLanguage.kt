package com.lagradost.cloudstream3.ui.setup

import android.widget.AbsListView
import android.widget.ArrayAdapter
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.databinding.FragmentSetupLanguageBinding
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.ui.settings.appLanguages
import com.lagradost.cloudstream3.ui.settings.getCurrentLocale
import com.lagradost.cloudstream3.ui.settings.nameNextToFlagEmoji

const val HAS_DONE_SETUP_KEY = "HAS_DONE_SETUP"

class SetupFragmentLanguage : BaseFragment<FragmentSetupLanguageBinding>(
    BaseFragment.BindingCreator.Inflate(FragmentSetupLanguageBinding::inflate)
) {
    override fun onBindingCreated(binding: FragmentSetupLanguageBinding) {
        // We don't want a crash for all users
        safe {
            val ctx = context ?: return@safe
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(ctx)

            val arrayAdapter =
                ArrayAdapter<String>(ctx, R.layout.sort_bottom_single_choice)

            binding.apply {
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
                val languageTagsIETF = appLanguages.map { it.second }
                val languageNames = appLanguages.map { it.nameNextToFlagEmoji() }
                val currentIndex = languageTagsIETF.indexOf(current)

                arrayAdapter.addAll(languageNames)
                listview1.adapter = arrayAdapter
                listview1.choiceMode = AbsListView.CHOICE_MODE_SINGLE
                listview1.setItemChecked(currentIndex, true)

                listview1.setOnItemClickListener { _, _, selectedLangIndex, _ ->
                    val langTagIETF = languageTagsIETF[selectedLangIndex]
                    CommonActivity.setLocale(activity, langTagIETF)
                    settingsManager.edit().putString(getString(R.string.locale_key), langTagIETF)
                        .apply()
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
                    setKey(HAS_DONE_SETUP_KEY, true)
                    findNavController().navigate(R.id.navigation_home)
                }
            }
        }
    }
}
