package com.lagradost.cloudstream3.ui.revamp.typography

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.lagradost.cloudstream3.databinding.RevampFragmentTypographyBinding
import com.lagradost.cloudstream3.ui.revamp.theme.CloneflixTypography

class CloneflixTypographyFragment : Fragment() {

    private var _binding: RevampFragmentTypographyBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = RevampFragmentTypographyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val regularStyles = listOf(
            CloneflixTypography.StyleLevel.REGULAR_LARGE_TITLE,
            CloneflixTypography.StyleLevel.REGULAR_TITLE1,
            CloneflixTypography.StyleLevel.REGULAR_TITLE2,
            CloneflixTypography.StyleLevel.REGULAR_TITLE3,
            CloneflixTypography.StyleLevel.REGULAR_TITLE4,
            CloneflixTypography.StyleLevel.REGULAR_HEADLINE1,
            CloneflixTypography.StyleLevel.REGULAR_HEADLINE2,
            CloneflixTypography.StyleLevel.REGULAR_BODY,
            CloneflixTypography.StyleLevel.REGULAR_SMALL_BODY,
            CloneflixTypography.StyleLevel.REGULAR_CAPTION1,
            CloneflixTypography.StyleLevel.REGULAR_CAPTION2
        )
        binding.rvRegularStyles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRegularStyles.adapter = CloneflixTypographyAdapter(regularStyles)

        val mediumStyles = listOf(
            CloneflixTypography.StyleLevel.MEDIUM_LARGE_TITLE,
            CloneflixTypography.StyleLevel.MEDIUM_TITLE1,
            CloneflixTypography.StyleLevel.MEDIUM_TITLE2,
            CloneflixTypography.StyleLevel.MEDIUM_TITLE3,
            CloneflixTypography.StyleLevel.MEDIUM_TITLE4,
            CloneflixTypography.StyleLevel.MEDIUM_HEADLINE1,
            CloneflixTypography.StyleLevel.MEDIUM_HEADLINE2,
            CloneflixTypography.StyleLevel.MEDIUM_BODY,
            CloneflixTypography.StyleLevel.MEDIUM_SMALL_BODY,
            CloneflixTypography.StyleLevel.MEDIUM_CAPTION1,
            CloneflixTypography.StyleLevel.MEDIUM_CAPTION2
        )
        binding.rvMediumStyles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMediumStyles.adapter = CloneflixTypographyAdapter(mediumStyles)

        val boldStyles = listOf(
            CloneflixTypography.StyleLevel.BOLD_LARGE_TITLE,
            CloneflixTypography.StyleLevel.BOLD_TITLE1,
            CloneflixTypography.StyleLevel.BOLD_TITLE2
        )
        binding.rvBoldStyles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvBoldStyles.adapter = CloneflixTypographyAdapter(boldStyles)

        val displayStyles = listOf(
            CloneflixTypography.StyleLevel.LOGO_BEBAS,
            CloneflixTypography.StyleLevel.HEADER_DISPLAY
        )
        binding.rvDisplayStyles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDisplayStyles.adapter = CloneflixTypographyAdapter(displayStyles)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = CloneflixTypographyFragment()
    }
}
