package com.lagradost.cloudstream3.ui.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.ArrayAdapter
import androidx.core.util.forEach
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.AllLanguagesName
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.databinding.FragmentSetupProviderLanguagesBinding
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.AppContextUtils.getApiProviderLangSettings
import com.lagradost.cloudstream3.utils.SubtitleHelper.getNameNextToFlagEmoji
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar

class SetupFragmentProviderLanguage : Fragment() {
    var binding: FragmentSetupProviderLanguagesBinding? = null

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val localBinding = FragmentSetupProviderLanguagesBinding.inflate(inflater, container, false)
        binding = localBinding
        return localBinding.root
        //return inflater.inflate(R.layout.fragment_setup_provider_languages, container, false)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fixPaddingStatusbar(binding?.setupRoot)

        safe {
            val ctx = context ?: return@safe

            val settingsManager = PreferenceManager.getDefaultSharedPreferences(ctx)

            val arrayAdapter =
                ArrayAdapter<String>(ctx, R.layout.sort_bottom_single_choice)

            val currentLangTags = ctx.getApiProviderLangSettings()

            val languagesTagName = synchronized(APIHolder.apis) {
                listOf( Pair(AllLanguagesName, getString(R.string.all_languages_preference)) ) +
                APIHolder.apis.map { Pair(it.lang, getNameNextToFlagEmoji(it.lang) ?: it.lang) }
                    .toSet().sortedBy { it.second.substringAfter("\u00a0").lowercase() } // name ignoring flag emoji
            }

            val currentIndexList = currentLangTags.map { langTag ->
                languagesTagName.indexOfFirst { lang -> lang.first == langTag }
            }.filter { it > -1 }

            arrayAdapter.addAll(languagesTagName.map { it.second })
            binding?.apply {
                listview1.adapter = arrayAdapter
                listview1.choiceMode = AbsListView.CHOICE_MODE_MULTIPLE
                currentIndexList.forEach {
                    listview1.setItemChecked(it, true)
                }

                listview1.setOnItemClickListener { _, _, _, _ ->
                    val selectedLanguages = mutableSetOf<String>()
                    listview1.checkedItemPositions?.forEach { key, value ->
                        if (value) selectedLanguages.add(languagesTagName[key].first)
                    }
                    settingsManager.edit().putStringSet(
                        ctx.getString(R.string.provider_lang_key),
                        selectedLanguages.toSet()
                    ).apply()
                }

                nextBtt.setOnClickListener {
                    findNavController().navigate(R.id.navigation_setup_provider_languages_to_navigation_setup_media)
                }

                prevBtt.setOnClickListener {
                    findNavController().popBackStack()
                }
            }
        }
    }
}