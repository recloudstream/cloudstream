package com.lagradost.cloudstream3.ui.revamp.colors

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.lagradost.cloudstream3.databinding.RevampFragmentColorsBinding
import com.lagradost.cloudstream3.ui.revamp.theme.CloneflixColors

class CloneflixColorsFragment : Fragment() {

    private var _binding: RevampFragmentColorsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = RevampFragmentColorsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tokens = CloneflixColors.getAllTokens()
        val sections = tokens.groupBy { it.category }

        binding.rvColorSections.layoutManager = LinearLayoutManager(requireContext())
        binding.rvColorSections.adapter = CloneflixColorsAdapter(sections)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = CloneflixColorsFragment()
    }
}
