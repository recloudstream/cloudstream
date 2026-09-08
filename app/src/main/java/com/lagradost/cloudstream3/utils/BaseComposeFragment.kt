package com.lagradost.cloudstream3.utils

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLandscape
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding
import com.lagradost.cloudstream4.compose.Screen
import com.lagradost.cloudstream4.compose.createComposeView

abstract class BaseComposeFragment : Fragment(), Screen {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = createComposeView(inflater, container, savedInstanceState)

    override fun onConfigurationChanged(newConfig: Configuration) {
        this.view?.let { view ->
            fixSystemBarsPadding(
                view,
                padLeft = isLayout(TV or EMULATOR),
                padBottom = isLandscape()
            )
        }
        super.onConfigurationChanged(newConfig)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        fixSystemBarsPadding(
            view,
            padLeft = isLayout(TV or EMULATOR),
            padBottom = isLandscape()
        )
        super.onViewCreated(view, savedInstanceState)
    }
}