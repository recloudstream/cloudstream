package com.lagradost.cloudstream4.compose
import androidx.compose.runtime.Composable

// https://github.com/adrielcafe/voyager
interface Screen {
    @Composable
    fun Content()
}