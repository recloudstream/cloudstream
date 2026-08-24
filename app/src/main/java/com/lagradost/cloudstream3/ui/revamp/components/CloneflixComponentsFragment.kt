package com.lagradost.cloudstream3.ui.revamp.components

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.lagradost.cloudstream3.databinding.RevampFragmentComponentsBinding

class CloneflixComponentsFragment : Fragment() {

    private var _binding: RevampFragmentComponentsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = RevampFragmentComponentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnDemoPrimary.setOnClickListener {
            Toast.makeText(requireContext(), "Primary Red Button Clicked!", Toast.LENGTH_SHORT).show()
        }

        binding.btnDemoSecondary.setOnClickListener {
            Toast.makeText(requireContext(), "Secondary White Button Clicked!", Toast.LENGTH_SHORT).show()
        }

        binding.btnDemoOutline.setOnClickListener {
            Toast.makeText(requireContext(), "Outline Button Clicked!", Toast.LENGTH_SHORT).show()
        }

        binding.btnDemoGhost.setOnClickListener {
            Toast.makeText(requireContext(), "Ghost Button Clicked!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = CloneflixComponentsFragment()
    }
}
