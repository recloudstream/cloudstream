package com.lagradost.cloudstream3.ui.revamp.icons

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.lagradost.cloudstream3.ui.revamp.compose.screens.CloneflixIconsLabelsComposeScreen
import com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixTheme

class CloneflixIconsLabelsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                CloneflixTheme {
                    CloneflixIconsLabelsComposeScreen()
                }
            }
        }
    }

    companion object {
        fun newInstance() = CloneflixIconsLabelsFragment()
    }
}
