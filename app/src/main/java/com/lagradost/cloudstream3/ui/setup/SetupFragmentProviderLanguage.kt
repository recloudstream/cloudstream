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
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.APIHolder.getApiProviderLangSettings
import com.lagradost.cloudstream3.AllLanguagesName
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import kotlinx.android.synthetic.main.fragment_setup_media.*

class SetupFragmentProviderLanguage : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_setup_provider_languages, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        context?.fixPaddingStatusbar(setup_root)

        with(context) {
            if (this == null) return
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)

            val arrayAdapter =
                ArrayAdapter<String>(this, R.layout.sort_bottom_single_choice)

            val current = this.getApiProviderLangSettings()
            val langs = APIHolder.apis.map { it.lang }.toSet()
                .sortedBy { SubtitleHelper.fromTwoLettersToLanguage(it) } + AllLanguagesName

            val currentList =
                current.map { langs.indexOf(it) }.filter { it != -1 } // TODO LOOK INTO

            val languageNames = langs.map {
                if (it == AllLanguagesName) {
                    getString(R.string.all_languages_preference)
                } else {
                    val emoji = SubtitleHelper.getFlagFromIso(it)
                    val name = SubtitleHelper.fromTwoLettersToLanguage(it)
                    "$emoji $name"
                }
            }

            arrayAdapter.addAll(languageNames)

            listview1?.adapter = arrayAdapter
            listview1?.choiceMode = AbsListView.CHOICE_MODE_MULTIPLE
            currentList.forEach {
                listview1.setItemChecked(it, true)
            }

            listview1?.setOnItemClickListener { _, _, _, _ ->
                val currentLanguages = mutableListOf<String>()
                listview1?.checkedItemPositions?.forEach { key, value ->
                    if (value) currentLanguages.add(langs[key])
                }
                settingsManager.edit().putStringSet(
                    this.getString(R.string.provider_lang_key),
                    currentLanguages.toSet()
                ).apply()
            }

            next_btt?.setOnClickListener {
                findNavController().navigate(R.id.navigation_setup_provider_languages_to_navigation_setup_media)
            }

            prev_btt?.setOnClickListener {
                findNavController().popBackStack()
            }
        }
    }


}