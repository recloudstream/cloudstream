package com.lagradost.cloudstream4.compose

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
fun LazyGridState.IsScrolling(up : Runnable, down : Runnable) {
    LaunchedEffect(lastScrolledBackward) {
        if(lastScrolledBackward) up.run()
    }
    LaunchedEffect(lastScrolledForward) {
        if(lastScrolledForward) down.run()
    }
}
@Suppress("FrequentlyChangingValue")
@Composable
fun LazyListState.IsScrolling(up : Runnable, down : Runnable) {
    LaunchedEffect(firstVisibleItemIndex) {
        if(lastScrolledBackward) up.run()
    }
    LaunchedEffect(firstVisibleItemIndex) {
        if(lastScrolledForward) down.run()
    }
}