package com.lagradost.cloudstream3.ui.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentWelcomeBinding
import com.lagradost.cloudstream3.syncproviders.AccountManager

class WelcomeFragment : Fragment() {

    private var _binding: FragmentWelcomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWelcomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Check if already signed in, maybe skip to next step (handled by MainActivity mostly)
        if (AccountManager.firebaseApi.auth.currentUser != null) {
            findNavController().navigate(R.id.action_navigation_welcome_to_navigation_setup_language)
            return
        }

        binding.welcomeGetStarted.setOnClickListener {
            // Get Started means we want them to sign up
            findNavController().navigate(R.id.action_navigation_welcome_to_navigation_login)
        }

        binding.welcomeSignIn.setOnClickListener {
            findNavController().navigate(R.id.action_navigation_welcome_to_navigation_login)
        }

        binding.welcomeSkip.setOnClickListener {
            findNavController().navigate(R.id.action_navigation_welcome_to_navigation_setup_language)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
