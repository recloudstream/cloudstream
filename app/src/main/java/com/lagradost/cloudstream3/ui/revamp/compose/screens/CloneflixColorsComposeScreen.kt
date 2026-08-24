package com.lagradost.cloudstream3.ui.revamp.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixColorSwatch
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixHeader
import com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixTheme
import com.lagradost.cloudstream3.ui.revamp.theme.CloneflixColors

@Composable
fun CloneflixColorsComposeScreen(
    onColorClick: (CloneflixColors.ColorToken) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val tokens = CloneflixColors.getAllTokens()
    val sections = tokens.groupBy { it.category }
    val typography = CloneflixTheme.typography
    val colors = CloneflixTheme.colors
    val dimens = CloneflixTheme.dimens

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = dimens.spacing2Xl)
    ) {
        item {
            Spacer(modifier = Modifier.height(dimens.spacing2Xl))
            CloneflixHeader(
                title = "Colors",
                subtitle = "33+ Tokens • Primary, Secondary, Greys, Transparent",
                iconRes = R.drawable.cloneflix_ic_colors
            )
            Spacer(modifier = Modifier.height(dimens.spacing2Xl))
        }

        sections.forEach { (categoryName, categoryTokens) ->
            item {
                Text(
                    text = categoryName.uppercase(),
                    style = typography.sectionHeader,
                    color = colors.primary,
                    modifier = Modifier.padding(top = dimens.spacingM, bottom = dimens.spacingL)
                )
            }

            items(categoryTokens) { token ->
                CloneflixColorSwatch(
                    token = token,
                    onClick = { onColorClick(token) }
                )
                Spacer(modifier = Modifier.height(dimens.spacingM))
            }
        }

        item {
            Spacer(modifier = Modifier.height(dimens.spacing3Xl))
        }
    }
}

@Preview(name = "Phone Preview", showBackground = true, backgroundColor = 0xFF141414, device = Devices.PHONE)
@Preview(name = "TV Preview", showBackground = true, backgroundColor = 0xFF141414, device = Devices.TV_720p)
@Composable
private fun CloneflixColorsScreenPreview() {
    CloneflixTheme {
        CloneflixColorsComposeScreen()
    }
}
