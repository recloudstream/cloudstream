package com.mihon.presentation.settings

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.lagradost.cloudstream4.compose.Screen
import com.mihon.presentation.LocalBackPress

interface SearchableSettings : Screen {
    @Composable
    @ReadOnlyComposable
    fun getTitleRes(): String

    @Composable
    fun getPreferences(): List<Preference>

    @Composable
    fun RowScope.AppBarAction() {
    }

    @Composable
    override fun Content() {
        val handleBack = LocalBackPress.current
        PreferenceScaffold(
            title = getTitleRes(),
            onBackPressed = if (handleBack != null) handleBack::invoke else null,
            actions = { AppBarAction() },
            itemsProvider = { getPreferences() },
        )
    }

    companion object {
        // HACK: for the background blipping thingy.
        // The title of the target PreferenceItem
        // Set before showing the destination screen and reset after
        // See BasePreferenceWidget.highlightBackground
        var highlightKey: String? = null
    }
}
