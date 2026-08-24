package com.lagradost.cloudstream3.ui.revamp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.lagradost.cloudstream3.databinding.RevampFragmentDesignSystemBinding
import com.lagradost.cloudstream3.ui.revamp.about.CloneflixAboutFragment
import com.lagradost.cloudstream3.ui.revamp.colors.CloneflixColorsFragment
import com.lagradost.cloudstream3.ui.revamp.components.CloneflixComponentsFragment
import com.lagradost.cloudstream3.ui.revamp.icons.CloneflixIconsLabelsFragment
import com.lagradost.cloudstream3.ui.revamp.typography.CloneflixTypographyFragment

class RevampDesignSystemFragment : Fragment() {

    private var _binding: RevampFragmentDesignSystemBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = RevampFragmentDesignSystemBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val fragments = listOf(
            CloneflixAboutFragment.newInstance() to "About",
            CloneflixTypographyFragment.newInstance() to "Typography",
            CloneflixColorsFragment.newInstance() to "Colors",
            CloneflixComponentsFragment.newInstance() to "Components",
            CloneflixIconsLabelsFragment.newInstance() to "Icons & Labels"
        )

        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = fragments.size
            override fun createFragment(position: Int): Fragment = fragments[position].first
        }

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = fragments[position].second
        }.attach()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = RevampDesignSystemFragment()
    }
}
