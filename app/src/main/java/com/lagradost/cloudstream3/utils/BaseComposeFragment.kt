package com.lagradost.cloudstream3.utils

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.lagradost.cloudstream4.compose.Screen
import com.lagradost.cloudstream4.compose.createComposeView

/** Insets belong to the lab activity; extension screens need only a Compose host. */
abstract class BaseComposeFragment : Fragment(), Screen {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        createComposeView(inflater, container, savedInstanceState)
}
