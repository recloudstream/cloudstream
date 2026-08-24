package com.lagradost.cloudstream3.ui.revamp.about

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.lagradost.cloudstream3.databinding.RevampFragmentAboutBinding

class CloneflixAboutFragment : Fragment() {

    private var _binding: RevampFragmentAboutBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = RevampFragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.aboutCard.btnConnectX.setOnClickListener {
            openUrl("https://x.com/ivannaheraskina")
        }

        binding.aboutCard.btnFeedback.setOnClickListener {
            Toast.makeText(
                requireContext(),
                "Thank you for supporting Cloneflix Design System 2024!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(requireContext(), "Could not open $url", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = CloneflixAboutFragment()
    }
}
