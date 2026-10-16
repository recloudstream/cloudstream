package com.mihon.presentation.settings

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component1
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component2
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import com.mihon.material.AppBar
import com.mihon.material.Scaffold

@Composable
fun PreferenceScaffold(
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
    onBackPressed: (() -> Unit)? = null,
    itemsProvider: @Composable () -> List<Preference>,
) {
    val (top, bottom) = remember { FocusRequester.createRefs() }

    // A readable width, centred, rather than rows across a whole wide window
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Scaffold(
            modifier = Modifier.widthIn(max = PreferenceMaxWidth),
            // TODO @WindowInsets remove this when we have converted everything to compose
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                AppBar(
                    modifier = Modifier.focusRequester(top).focusProperties {
                        down = bottom
                    },
                    title = title,
                    navigateUp = onBackPressed,
                    actions = actions,
                    scrollBehavior = it,
                )
            },
            content = { contentPadding ->
                PreferenceScreen(
                    modifier = Modifier.focusRequester(bottom).focusProperties {
                        up = top
                    },
                    items = itemsProvider(),
                    contentPadding = contentPadding,
                )
            },
        )
    }
}

/** How wide a settings page grows */
val PreferenceMaxWidth = 880.dp
