package com.lagradost.cloudstream3.ui.revamp.compose.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Central design tokens and defaults for Cloneflix components.
 * Eliminates magic numbers across Jetpack Compose UI.
 */
object CloneflixTokens {
    // Focus & Motion Tokens
    const val FOCUS_SCALE_FACTOR = 1.05f
    const val FOCUS_SCALE_FACTOR_LARGE = 1.08f
    const val FOCUS_SCALE_FACTOR_SUBTLE = 1.02f

    // Alpha / Opacity Tokens
    const val ALPHA_MUTED = 0.3f
    const val ALPHA_SUBTLE = 0.4f
    const val ALPHA_MEDIUM = 0.6f
    const val ALPHA_HIGH = 0.7f
    const val ALPHA_NEAR_OPAQUE = 0.9f

    // Common Radii
    val RadiusPill: Dp = 999.dp
    val RadiusCard: Dp = 4.dp
    val RadiusCardMedium: Dp = 8.dp
    val RadiusCardLarge: Dp = 12.dp
}

/**
 * Defaults and dimension formulas for Cloneflix Movie Cards.
 */
object CloneflixMovieCardDefaults {
    val WidthMedium: Dp = 218.dp
    val WidthSmall: Dp = 128.dp
    val WidthTop10Medium: Dp = 215.dp
    val WidthTop10Small: Dp = 160.dp
    val WidthEpisodeMedium: Dp = 160.dp
    val WidthEpisodeSmall: Dp = 128.dp
    val WidthMoreLikeThis: Dp = 236.dp
    val WidthTrailer: Dp = 236.dp
    val WidthPlayerPreview: Dp = 245.dp
    val WidthContinueWatchingMedium: Dp = 218.dp
    val WidthContinueWatchingSmall: Dp = 160.dp

    val HeightMedium: Dp = 123.dp
    val HeightSmall: Dp = 72.dp
    val HeightTop10Medium: Dp = 154.dp
    val HeightTop10Small: Dp = 115.dp
    val HeightEpisodeMedium: Dp = 90.dp
    val HeightEpisodeSmall: Dp = 72.dp
    val HeightMoreLikeThis: Dp = 132.dp
    val HeightTrailer: Dp = 204.dp
    val HeightTrailerThumbnail: Dp = 132.dp
    val HeightPlayerPreview: Dp = 152.dp
    val HeightContinueWatchingMedium: Dp = 133.dp
    val HeightContinueWatchingSmall: Dp = 98.dp

    val RankBoxWidth: Dp = 90.dp
    val RankFontSize: TextUnit = 110.sp
    val RankLineHeight: TextUnit = 110.sp
    val RankShadowOffsetX: Dp = 10.dp
    val RankForegroundOffsetX: Dp = 8.dp

    val PlayButtonOverlaySize: Dp = 36.dp
    val PlayIconOverlaySize: Dp = 16.dp
    val EpisodeItemPlayButtonSize: Dp = 32.dp
    val EpisodeItemPlayIconSize: Dp = 14.dp

    val EpisodeThumbnailWidth: Dp = 128.dp
    val EpisodeThumbnailHeight: Dp = 72.dp

    val ExpandedPreviewHeaderHeight: Dp = 260.dp
    val ActionButtonSize: Dp = 40.dp

    fun cardWidth(type: CloneflixMovieCardType, size: CloneflixMovieCardSize): Dp = when (type) {
        CloneflixMovieCardType.TOP10 -> if (size == CloneflixMovieCardSize.MEDIUM) WidthTop10Medium else WidthTop10Small
        CloneflixMovieCardType.EPISODE -> if (size == CloneflixMovieCardSize.SMALL) WidthEpisodeSmall else WidthEpisodeMedium
        CloneflixMovieCardType.MORE_LIKE_THIS,
        CloneflixMovieCardType.MORE_LIKE_THIS_WITH_PLAY -> WidthMoreLikeThis
        CloneflixMovieCardType.TRAILER -> WidthTrailer
        CloneflixMovieCardType.PLAYER_PREVIEW -> WidthPlayerPreview
        CloneflixMovieCardType.CONTINUE_WATCHING -> if (size == CloneflixMovieCardSize.MEDIUM) WidthContinueWatchingMedium else WidthContinueWatchingSmall
        CloneflixMovieCardType.DEFAULT -> if (size == CloneflixMovieCardSize.MEDIUM) WidthMedium else WidthSmall
    }

    fun cardHeight(type: CloneflixMovieCardType, size: CloneflixMovieCardSize): Dp = when (type) {
        CloneflixMovieCardType.TOP10 -> if (size == CloneflixMovieCardSize.MEDIUM) HeightTop10Medium else HeightTop10Small
        CloneflixMovieCardType.EPISODE -> if (size == CloneflixMovieCardSize.SMALL) HeightEpisodeSmall else HeightEpisodeMedium
        CloneflixMovieCardType.MORE_LIKE_THIS,
        CloneflixMovieCardType.MORE_LIKE_THIS_WITH_PLAY -> HeightMoreLikeThis
        CloneflixMovieCardType.TRAILER -> HeightTrailer
        CloneflixMovieCardType.PLAYER_PREVIEW -> HeightPlayerPreview
        CloneflixMovieCardType.CONTINUE_WATCHING -> if (size == CloneflixMovieCardSize.MEDIUM) HeightContinueWatchingMedium else HeightContinueWatchingSmall
        CloneflixMovieCardType.DEFAULT -> if (size == CloneflixMovieCardSize.MEDIUM) HeightMedium else HeightSmall
    }
}

/**
 * Defaults for Video Player and Media Control Overlays.
 */
object CloneflixPlayerDefaults {
    val ControlButtonSize: Dp = 48.dp
    val ControlButtonSizeLarge: Dp = 64.dp
    val ControlIconSize: Dp = 24.dp
    val ControlIconSizeLarge: Dp = 32.dp
    val BottomBarHeight: Dp = 80.dp
    val TopBarHeight: Dp = 64.dp
    val ProgressThumbSize: Dp = 12.dp
    val ProgressTrackHeight: Dp = 4.dp
    val VolumeSliderWidth: Dp = 140.dp
    val SpeedItemWidth: Dp = 72.dp
}

/**
 * Defaults for Episodes and Drawer Panels.
 */
object CloneflixDrawerDefaults {
    val DrawerWidth: Dp = 380.dp
    val EpisodeItemCornerRadius: Dp = 6.dp
    val EpisodeNumberWidth: Dp = 32.dp
}
