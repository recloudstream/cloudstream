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
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.databinding.FragmentSetupMediaBinding
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar


class SetupFragmentMedia : Fragment() {
    var binding: FragmentSetupMediaBinding? = null

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val localBinding = FragmentSetupMediaBinding.inflate(inflater, container, false)
        binding = localBinding
        return localBinding.root
        //return inflater.inflate(R.layout.fragment_setup_media, container, false)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        safe {
            fixPaddingStatusbar(binding?.setupRoot)

            val ctx = context ?: return@safe
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(ctx)

            val arrayAdapter =
                ArrayAdapter<String>(ctx, R.layout.sort_bottom_single_choice)

            val names = enumValues<TvType>().sorted().map { it.name }
            val selected = mutableListOf<Int>()

            arrayAdapter.addAll(names)
            binding?.apply {
                listview1.let {
                    it.adapter = arrayAdapter
                    it.choiceMode = AbsListView.CHOICE_MODE_MULTIPLE

                    it.setOnItemClickListener { _, _, _, _ ->
                        it.checkedItemPositions?.forEach { key, value ->
                            if (value) {
                                selected.add(key)
                            } else {
                                selected.remove(key)
                            }
                        }
                        val prefValues = selected.mapNotNull { pos ->
                            val item =
                                it.getItemAtPosition(pos)?.toString() ?: return@mapNotNull null
                            val itemVal = TvType.valueOf(item)
                            itemVal.ordinal.toString()
                        }.toSet()
                        settingsManager.edit()
                            .putStringSet(getString(R.string.prefer_media_type_key), prefValues)
                            .apply()

                        // Regenerate set homepage
                        DataStoreHelper.currentHomePage = null
                    }
                }

                nextBtt.setOnClickListener {
                    findNavController().navigate(R.id.navigation_setup_media_to_navigation_setup_layout)
                }

                prevBtt.setOnClickListener {
                    findNavController().popBackStack()
                }
            }
        }
    }
}