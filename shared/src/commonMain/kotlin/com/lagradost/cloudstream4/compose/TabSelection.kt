package com.lagradost.cloudstream4.compose


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.lagradost.cloudstream4.theme.CloudStreamPreviewTheme
import com.mihon.material.padding

@Composable
fun HorizontalTab(
    pagerState: PagerState,
    size: Int,
    name : @Composable (Int) -> String,
    containerColor: Color,
    edgePadding: Dp = 0.dp,
    fillWidth : Boolean = true,
) {
    val currentPage = pagerState.currentPage

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val tabWidth =
            if (size > 0 && fillWidth) {
                (maxWidth - edgePadding * 2.0f) / size
            } else {
                Dp.Unspecified
            }

        SecondaryScrollableTabRow(
            currentPage,
            edgePadding = edgePadding,
            containerColor = containerColor,
            indicator = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .zIndex(-1.0f)
                        .tabIndicatorOffset(currentPage, matchContentSize = false)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(MaterialTheme.padding.extraSmall)
                            .circle()
                            .background(MaterialTheme.colorScheme.onBackground)
                    )
                }
            }, divider = {}
        ) {
            for (index in 0 until size) {
                val selected = index == currentPage
                Tab(
                    modifier = Modifier
                        .height(40.dp)
                        .widthIn(min = tabWidth)
                        .circle(),
                    selected = selected, onClick = {
                        pagerState.requestScrollToPage(index)
                    }, text = {
                        Text(
                            name(index), color = if (selected) {
                                MaterialTheme.colorScheme.background
                            } else {
                                MaterialTheme.colorScheme.onBackground
                            }
                        )
                    })
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun Preview() {
    CloudStreamPreviewTheme {
        val pager = rememberPagerState { 10 }
        HorizontalTab(pager, 10, { "a$it" }, Color.Transparent)
    }
}