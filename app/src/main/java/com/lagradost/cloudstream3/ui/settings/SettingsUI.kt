package com.lagradost.cloudstream3.ui.settings

import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ArrayAdapter
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.preference.SeekBarPreference
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getActivity
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.databinding.BottomAppFontDialogBinding
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.BasePreferenceFragmentCompat
import com.lagradost.cloudstream3.ui.clear
import com.lagradost.cloudstream3.ui.home.HomeChildItemAdapter
import com.lagradost.cloudstream3.ui.home.ParentItemAdapter
import com.lagradost.cloudstream3.ui.search.SearchAdapter
import com.lagradost.cloudstream3.ui.search.SearchResultBuilder
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.updateTv
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.getPref
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.hideOn
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setPaddingBottom
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setToolBarScrollFlags
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setUpToolbar
import com.lagradost.cloudstream3.utils.AppFontManager
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showMultiDialog
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import kotlinx.coroutines.launch

class SettingsUI : BasePreferenceFragmentCompat() {
    private fun updateAppFontSummary() {
        context?.let {
            getPref(R.string.app_font_key)?.summary = AppFontManager.getSummary(it)
        }
    }

    private fun showAppFontDialog() {
        val activity = activity ?: return
        val binding = BottomAppFontDialogBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(activity)
        val defaultValue = getString(R.string.app_font_default)
        val customOption = getString(R.string.app_font_custom_option)
        val currentFont = AppFontManager.getSelectedFont(activity)
        val suggestedFonts = AppFontManager.getSuggestedFonts(activity)
        val suggestions = listOf(defaultValue, customOption) + suggestedFonts

        dialog.setContentView(binding.root)

        binding.text1.text = getString(R.string.app_font_picker_title)
        val previewCache = mutableMapOf<String, Typeface?>()
        val adapter = object : ArrayAdapter<String>(
            activity,
            R.layout.app_font_dropdown_item,
            suggestions
        ) {
            private fun applyPreview(textView: TextView, fontName: String) {
                val isSpecial = fontName == defaultValue || fontName == customOption
                val typeface = if (isSpecial) {
                    Typeface.DEFAULT
                } else {
                    previewCache.getOrPut(fontName) {
                        AppFontManager.getPreviewTypeface(activity, fontName)
                    } ?: Typeface.DEFAULT
                }
                textView.typeface = typeface
            }

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view as? TextView)?.let { applyPreview(it, getItem(position).orEmpty()) }
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                (view as? TextView)?.let { applyPreview(it, getItem(position).orEmpty()) }
                return view
            }
        }

        binding.appFontSuggestions.setAdapter(adapter)

        val initialSuggestion = when {
            currentFont == null -> defaultValue
            suggestedFonts.any { it.equals(currentFont, true) } -> currentFont
            else -> customOption
        }
        val initialInput = currentFont.orEmpty()
        binding.appFontSuggestions.setText(initialSuggestion, false)
        binding.appFontInput.setText(if (initialSuggestion == defaultValue) "" else initialInput)

        fun updateCustomState(isCustom: Boolean) {
            binding.appFontInputLayout.isEnabled = isCustom
            binding.appFontInputLayout.alpha = if (isCustom) 1f else 0.7f
        }
        updateCustomState(initialSuggestion == customOption)

        binding.appFontSuggestions.setOnItemClickListener { _, _, position, _ ->
            val selected = suggestions.getOrNull(position) ?: return@setOnItemClickListener
            when (selected) {
                defaultValue -> {
                    updateCustomState(false)
                    binding.appFontInput.setText("")
                }
                customOption -> {
                    updateCustomState(true)
                    if (binding.appFontInput.text.isNullOrBlank()) {
                        binding.appFontInput.setText(currentFont.orEmpty())
                    }
                    binding.appFontInput.requestFocus()
                }
                else -> {
                    updateCustomState(false)
                    binding.appFontInput.setText(selected)
                }
            }
        }
        binding.applyBtt.setOnClickListener {
            val typedFont = binding.appFontInput.text?.toString()?.trim().orEmpty()
            val selectedSuggestion = binding.appFontSuggestions.text?.toString()?.trim().orEmpty()
            val selectedFont = when {
                selectedSuggestion == defaultValue -> null
                selectedSuggestion == customOption -> typedFont.takeIf { it.isNotEmpty() }
                selectedSuggestion.isNotEmpty() -> selectedSuggestion
                typedFont.isNotEmpty() -> typedFont
                else -> null
            }

            binding.applyBtt.isEnabled = false
            binding.cancelBtt.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val result = AppFontManager.setSelectedFont(activity, selectedFont)
                binding.applyBtt.isEnabled = true
                binding.cancelBtt.isEnabled = true
                result.onSuccess {
                    updateAppFontSummary()
                    AppFontManager.refresh(activity)
                    dialog.dismiss()
                }.onFailure {
                    showToast(activity, it.message ?: getString(R.string.app_font_invalid))
                }
            }
        }
        binding.cancelBtt.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnShowListener {
            AppFontManager.applyToViewTree(binding.root)
        }
        dialog.show()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpToolbar(R.string.category_ui)
        setPaddingBottom()
        setToolBarScrollFlags()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        hideKeyboard()
        setPreferencesFromResource(R.xml.settings_ui, rootKey)
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(requireContext())
        updateAppFontSummary()

        (getPref(R.string.overscan_key)?.hideOn(PHONE or EMULATOR) as? SeekBarPreference)?.setOnPreferenceChangeListener { pref, newValue ->
            val padding = (newValue as? Int)?.toPx ?: return@setOnPreferenceChangeListener true
            (pref.context.getActivity() as? MainActivity)?.binding?.homeRoot?.setPadding(padding, padding, padding, padding)
            return@setOnPreferenceChangeListener true
        }

        getPref(R.string.bottom_title_key)?.setOnPreferenceChangeListener { _, _ ->
            HomeChildItemAdapter.sharedPool.clear()
            ParentItemAdapter.sharedPool.clear()
            SearchAdapter.sharedPool.clear()
            true
        }

        getPref(R.string.poster_size_key)?.setOnPreferenceChangeListener { _, newValue ->
            HomeChildItemAdapter.sharedPool.clear()
            ParentItemAdapter.sharedPool.clear()
            SearchAdapter.sharedPool.clear()
            context?.let { HomeChildItemAdapter.updatePosterSize(it, newValue as? Int) }
            true
        }

        getPref(R.string.poster_ui_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.poster_ui_options)
            val keys = resources.getStringArray(R.array.poster_ui_options_values)
            val prefValues = keys.map {
                settingsManager.getBoolean(it, true)
            }.mapIndexedNotNull { index, b ->
                if (b) {
                    index
                } else null
            }

            activity?.showMultiDialog(
                prefNames.toList(),
                prefValues,
                getString(R.string.poster_ui_settings),
                {}
            ) { list ->
                settingsManager.edit {
                    for ((i, key) in keys.withIndex()) {
                        putBoolean(key, list.contains(i))
                    }
                }
                SearchResultBuilder.updateCache(it.context)
            }

            return@setOnPreferenceClickListener true
        }

        getPref(R.string.app_layout_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.app_layout)
            val prefValues = resources.getIntArray(R.array.app_layout_values)

            val currentLayout =
                settingsManager.getInt(getString(R.string.app_layout_key), -1)

            activity?.showBottomDialog(
                items = prefNames.toList(),
                selectedIndex = prefValues.indexOf(currentLayout),
                name = getString(R.string.app_layout),
                showApply = true,
                dismissCallback = {},
                callback = {
                    try {
                        settingsManager.edit {
                            putInt(getString(R.string.app_layout_key), prefValues[it])
                        }
                        context?.updateTv()
                        activity?.recreate()
                    } catch (e: Exception) {
                        logError(e)
                    }
                }
            )
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.app_font_key)?.setOnPreferenceClickListener {
            showAppFontDialog()
            true
        }

        getPref(R.string.app_theme_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.themes_names).toMutableList()
            val prefValues = resources.getStringArray(R.array.themes_names_values).toMutableList()
            val removeIncompatible = { text: String ->
                val toRemove = prefValues
                    .mapIndexed { idx, s -> if (s.startsWith(text)) idx else null }
                    .filterNotNull()
                var offset = 0
                toRemove.forEach { idx ->
                    prefNames.removeAt(idx - offset)
                    prefValues.removeAt(idx - offset)
                    offset += 1
                }
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) { // remove monet on android 11 and less
                removeIncompatible("Monet")
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) { // Remove system on android 9 and less
                removeIncompatible("System")
            }

            val currentLayout =
                settingsManager.getString(getString(R.string.app_theme_key), prefValues.first())

            activity?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(currentLayout),
                getString(R.string.app_theme_settings),
                true,
                {}
            ) {
                try {
                    settingsManager.edit {
                        putString(getString(R.string.app_theme_key), prefValues[it])
                    }
                    activity?.recreate()
                } catch (e: Exception) {
                    logError(e)
                }
            }
            return@setOnPreferenceClickListener true
        }
        getPref(R.string.primary_color_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.themes_overlay_names).toMutableList()
            val prefValues =
                resources.getStringArray(R.array.themes_overlay_names_values).toMutableList()

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) { // remove monet on android 11 and less
                val toRemove = prefValues
                    .mapIndexed { idx, s -> if (s.startsWith("Monet")) idx else null }
                    .filterNotNull()
                var offset = 0
                toRemove.forEach { idx ->
                    prefNames.removeAt(idx - offset)
                    prefValues.removeAt(idx - offset)
                    offset += 1
                }
            }

            val currentLayout =
                settingsManager.getString(getString(R.string.primary_color_key), prefValues.first())

            activity?.showDialog(
                prefNames.toList(),
                prefValues.indexOf(currentLayout),
                getString(R.string.primary_color_settings),
                true,
                {}
            ) {
                try {
                    settingsManager.edit {
                        putString(getString(R.string.primary_color_key), prefValues[it])
                    }
                    activity?.recreate()
                } catch (e: Exception) {
                    logError(e)
                }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.pref_filter_search_quality_key)?.setOnPreferenceClickListener {
            val names = enumValues<SearchQuality>().sorted().map { it.name }
            val currentList = settingsManager.getStringSet(
                getString(R.string.pref_filter_search_quality_key),
                setOf()
            )?.map {
                it.toInt()
            } ?: listOf()

            activity?.showMultiDialog(
                names,
                currentList,
                getString(R.string.pref_filter_search_quality),
                {}
            ) { selectedList ->
                settingsManager.edit {
                    putStringSet(
                        getString(R.string.pref_filter_search_quality_key),
                        selectedList.map { it.toString() }.toMutableSet()
                    )
                }
            }

            return@setOnPreferenceClickListener true
        }

        getPref(R.string.confirm_exit_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.confirm_exit)
            val prefValues = resources.getIntArray(R.array.confirm_exit_values)
            val confirmExit = settingsManager.getInt(getString(R.string.confirm_exit_key), -1)

            activity?.showBottomDialog(
                items = prefNames.toList(),
                selectedIndex = prefValues.indexOf(confirmExit),
                name = getString(R.string.confirm_before_exiting_title),
                showApply = true,
                dismissCallback = {},
                callback = { selectedOption ->
                    settingsManager.edit {
                        putInt(getString(R.string.confirm_exit_key), prefValues[selectedOption])
                    }
                }
            )
            return@setOnPreferenceClickListener true
        }
    }
}
