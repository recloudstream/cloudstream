package com.lagradost.cloudstream3.ui.revamp

import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.ui.revamp.theme.CloneflixColors
import com.lagradost.cloudstream3.ui.revamp.theme.CloneflixDimens
import com.lagradost.cloudstream3.ui.revamp.theme.CloneflixTypography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloneflixDesignSystemTest {

    @Test
    fun testColorTokensCountAndIntegrity() {
        val tokens = CloneflixColors.getAllTokens()
        assertTrue(tokens.isNotEmpty())
        assertTrue(tokens.size >= 30)

        val primaryRed = tokens.first { it.name == "Primary / Red" }
        assertEquals("#E50914", primaryRed.hex)

        val categories = tokens.map { it.category }.distinct()
        assertTrue(categories.contains("Primary"))
        assertTrue(categories.contains("Secondary"))
        assertTrue(categories.contains("Neutral Greys"))
        assertTrue(categories.contains("Transparent White"))
        assertTrue(categories.contains("Transparent Black"))
    }

    @Test
    fun testTypographyHierarchy() {
        val levels = CloneflixTypography.StyleLevel.values()
        assertTrue(levels.size >= 25)

        val regularLarge = CloneflixTypography.StyleLevel.REGULAR_LARGE_TITLE
        assertEquals(50f, regularLarge.sizeSp)
        assertEquals(64f, regularLarge.lineHeightSp)

        val boldLarge = CloneflixTypography.StyleLevel.BOLD_LARGE_TITLE
        assertEquals(55f, boldLarge.sizeSp)
        assertEquals(70f, boldLarge.lineHeightSp)
    }

    @Test
    fun testDimensTokens() {
        assertEquals(16f, CloneflixDimens.RADIUS_L)
        assertEquals(80, CloneflixDimens.HEADER_ICON_SIZE)
        assertEquals(1.4f, CloneflixDimens.HEADER_ICON_STROKE_WIDTH)
        assertEquals(48, CloneflixDimens.BUTTON_HEIGHT_LARGE)
        assertEquals(40, CloneflixDimens.BUTTON_HEIGHT_MEDIUM)
        assertEquals(32, CloneflixDimens.BUTTON_HEIGHT_SMALL)
    }

    @Test
    fun testMovieCardDefaultsAndTokens() {
        assertEquals(1.05f, com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixTokens.FOCUS_SCALE_FACTOR)
        assertEquals(
            218.dp,
            com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixMovieCardDefaults.cardWidth(
                com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixMovieCardType.DEFAULT,
                com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixMovieCardSize.MEDIUM
            )
        )
        assertEquals(
            128.dp,
            com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixMovieCardDefaults.cardWidth(
                com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixMovieCardType.DEFAULT,
                com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixMovieCardSize.SMALL
            )
        )
        assertTrue(com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixBadgeType.TOP_10.stringRes != 0)
        assertTrue(com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixBadgeType.RECENTLY_ADDED.stringRes != 0)
        assertTrue(com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixBadgeType.NEW_SEASON.stringRes != 0)
    }
}
