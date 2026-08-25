package com.lagradost.cloudstream3.ui.result.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.ui.result.compose.MovieRecommendationRow
import com.lagradost.cloudstream3.ui.result.compose.theme.MovieDetailsTheme

@Composable
fun RecommendationRowView(
    row: MovieRecommendationRow,
    onCardClick: (MovieCardItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = MovieDetailsTheme.dimens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.spacing2Xl, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingM)
    ) {
        row.items.forEach { cardItem ->
            Box(modifier = Modifier.weight(1f)) {
                MovieDetailsMovieCard(
                    title = cardItem.title,
                    type = MovieCardType.POSTER,
                    size = MovieCardSize.MEDIUM,
                    badge = cardItem.badge,
                    posterUrl = cardItem.posterUrl,
                    showLogo = false,
                    showBottomTitle = true,
                    onClick = { onCardClick(cardItem) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (row.items.size < 6) {
            repeat(6 - row.items.size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
