package com.lagradost.cloudstream3.ui.settings
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.lagradost.cloudstream4.compose.createComposeView
import com.mihon.presentation.settings.SearchableSettings

/** Empty glue code to connect the old navigation graph to Compose */
class SettingsProviders2 : Fragment(), SearchableSettings by SettingsProvidersScreen() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = createComposeView(inflater, container, savedInstanceState)
}