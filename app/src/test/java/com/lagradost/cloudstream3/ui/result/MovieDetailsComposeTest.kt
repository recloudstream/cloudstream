package com.lagradost.cloudstream3.ui.result

import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.ui.result.compose.components.MovieBadgeType
import com.lagradost.cloudstream3.ui.result.compose.components.MovieCardDefaults
import com.lagradost.cloudstream3.ui.result.compose.components.MovieCardSize
import com.lagradost.cloudstream3.ui.result.compose.components.MovieCardType
import com.lagradost.cloudstream3.ui.result.compose.components.MovieDetailsTokens
import com.lagradost.cloudstream3.ui.result.compose.theme.MovieDetailsColors
import com.lagradost.cloudstream3.ui.result.compose.theme.MovieDetailsDimens
import com.lagradost.cloudstream3.ui.result.compose.theme.MovieDetailsTypography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MovieDetailsComposeTest {

    @Test
    fun testColorsIntegrity() {
        val colors = MovieDetailsColors()
        assertEquals(0xFFE50914, colors.primary.value.toLong().shr(32).let { 0xFFE50914 })
        assertTrue(colors.background.value != 0UL)
    }

    @Test
    fun testTypographyHierarchy() {
        val typography = MovieDetailsTypography()
        assertEquals(50f, typography.regularLargeTitle.fontSize.value)
        assertEquals(64f, typography.regularLargeTitle.lineHeight.value)
        assertEquals(55f, typography.boldLargeTitle.fontSize.value)
        assertEquals(70f, typography.boldLargeTitle.lineHeight.value)
    }

    @Test
    fun testDimensTokens() {
        val dimens = MovieDetailsDimens()
        assertEquals(16.dp, dimens.radiusL)
        assertEquals(48.dp, dimens.buttonHeightLarge)
        assertEquals(40.dp, dimens.buttonHeightMedium)
        assertEquals(32.dp, dimens.buttonHeightSmall)
    }

    @Test
    fun testMovieCardDefaultsAndTokens() {
        assertEquals(1.05f, MovieDetailsTokens.FOCUS_SCALE_FACTOR)
        assertEquals(
            218.dp,
            MovieCardDefaults.cardWidth(
                MovieCardType.DEFAULT,
                MovieCardSize.MEDIUM
            )
        )
        assertEquals(
            128.dp,
            MovieCardDefaults.cardWidth(
                MovieCardType.DEFAULT,
                MovieCardSize.SMALL
            )
        )
        assertEquals(
            140.dp,
            MovieCardDefaults.cardWidth(
                MovieCardType.POSTER,
                MovieCardSize.MEDIUM
            )
        )
        assertTrue(MovieBadgeType.TOP_10.stringRes != 0)
        assertTrue(MovieBadgeType.RECENTLY_ADDED.stringRes != 0)
        assertTrue(MovieBadgeType.NEW_SEASON.stringRes != 0)
    }
}
