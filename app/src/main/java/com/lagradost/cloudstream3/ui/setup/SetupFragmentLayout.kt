package com.lagradost.cloudstream3.ui.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import kotlinx.android.synthetic.main.fragment_setup_layout.*
import kotlinx.android.synthetic.main.fragment_setup_media.listview1
import kotlinx.android.synthetic.main.fragment_setup_media.next_btt
import kotlinx.android.synthetic.main.fragment_setup_media.prev_btt
import kotlinx.android.synthetic.main.fragment_setup_media.setup_root
import org.acra.ACRA


class SetupFragmentLayout : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_setup_layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        context?.fixPaddingStatusbar(setup_root)

        with(context) {
            if (this == null) return
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)

            val prefNames = resources.getStringArray(R.array.app_layout)
            val prefValues = resources.getIntArray(R.array.app_layout_values)

            val currentLayout =
                settingsManager.getInt(getString(R.string.app_layout_key), -1)

            val arrayAdapter =
                ArrayAdapter<String>(this, R.layout.sort_bottom_single_choice)

            arrayAdapter.addAll(prefNames.toList())
            listview1?.adapter = arrayAdapter
            listview1?.choiceMode = AbsListView.CHOICE_MODE_SINGLE
            listview1?.setItemChecked(
                prefValues.indexOf(currentLayout), true
            )

            listview1?.setOnItemClickListener { _, _, position, _ ->
                settingsManager.edit()
                    .putInt(getString(R.string.app_layout_key), prefValues[position])
                    .apply()
                activity?.recreate()
            }

            acra_switch?.setOnCheckedChangeListener { _, enableCrashReporting ->
                // Use same pref as in settings
                settingsManager.edit().putBoolean(ACRA.PREF_DISABLE_ACRA, !enableCrashReporting)
                    .apply()
                val text =
                    if (enableCrashReporting) R.string.bug_report_settings_off else R.string.bug_report_settings_on
                crash_reporting_text?.text = getText(text)
            }

            val enableCrashReporting = !settingsManager.getBoolean(ACRA.PREF_DISABLE_ACRA, true)
            acra_switch.isChecked = enableCrashReporting
            crash_reporting_text.text =
                getText(
                    if (enableCrashReporting) R.string.bug_report_settings_off else R.string.bug_report_settings_on
                )


            next_btt?.setOnClickListener {
                findNavController().navigate(R.id.navigation_home)
            }

            prev_btt?.setOnClickListener {
                findNavController().popBackStack()
            }
        }
    }


}