package com.lagradost.cloudstream4.compose
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// https://github.com/adrielcafe/voyager
interface Screen {
    @Composable
    fun Content()
}

/** Use to sync status bar color with top app bar. */
interface HasStatusBarColor {
    @Composable
    fun statusBarColor(): Color = MaterialTheme.colorScheme.surface
}