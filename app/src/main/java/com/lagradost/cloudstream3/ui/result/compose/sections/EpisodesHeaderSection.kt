package com.lagradost.cloudstream3.ui.result.compose.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.result.compose.components.AiringScheduleBanner
import com.lagradost.cloudstream3.ui.result.compose.components.SeasonDropdown
import com.lagradost.cloudstream3.ui.result.compose.model.AiringScheduleUiState
import com.lagradost.cloudstream3.ui.result.compose.theme.MovieDetailsTheme

@Composable
fun EpisodesHeaderSection(
    modifier: Modifier = Modifier,
    seasonOptions: List<String>,
    selectedSeasonText: String,
    onSeasonSelect: ((Int) -> Unit)?,
    onSeasonTextChange: (String) -> Unit,
    dubOptions: List<String>,
    selectedDubText: String,
    onDubSelect: ((Int) -> Unit)?,
    onDubTextChange: (String) -> Unit,
    rangeOptions: List<String>,
    selectedRangeText: String,
    onRangeSelect: ((Int) -> Unit)?,
    onRangeTextChange: (String) -> Unit,
    airingSchedule: AiringScheduleUiState? = null
) {
    val colors = MovieDetailsTheme.colors
    val typography = MovieDetailsTheme.typography
    val dimens = MovieDetailsTheme.dimens

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.spacing2Xl)
            .padding(top = dimens.spacing2Xl, bottom = dimens.spacingL),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingM)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingL),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.episodes),
                style = typography.boldTitle2,
                fontSize = 24.sp,
                color = colors.textPrimary
            )

            if (seasonOptions.size > 1) {
                SeasonDropdown(
                    options = seasonOptions,
                    selectedOption = selectedSeasonText,
                    onOptionSelected = { seasonStr ->
                        onSeasonTextChange(seasonStr)
                        val idx = seasonOptions.indexOf(seasonStr)
                        if (idx >= 0) onSeasonSelect?.invoke(idx)
                    },
                    width = 180.dp
                )
            }

            if (dubOptions.size > 1) {
                SeasonDropdown(
                    options = dubOptions,
                    selectedOption = selectedDubText,
                    onOptionSelected = { dubStr ->
                        onDubTextChange(dubStr)
                        val idx = dubOptions.indexOf(dubStr)
                        if (idx >= 0) onDubSelect?.invoke(idx)
                    },
                    width = 140.dp
                )
            }

            if (rangeOptions.size > 1) {
                SeasonDropdown(
                    options = rangeOptions,
                    selectedOption = selectedRangeText,
                    onOptionSelected = { rangeStr ->
                        onRangeTextChange(rangeStr)
                        val idx = rangeOptions.indexOf(rangeStr)
                        if (idx >= 0) onRangeSelect?.invoke(idx)
                    },
                    width = 140.dp
                )
            }
        }

        if (airingSchedule?.hasAiringInfo == true) {
            AiringScheduleBanner(airingSchedule = airingSchedule)
        }
    }
}
