package com.lagradost.cloudstream3.ui.result.compose.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.R

object MovieDetailsTokens {
    val ShapeCardSmall = RoundedCornerShape(4.dp)
    val ShapeCardMedium = RoundedCornerShape(8.dp)
    val ShapeCardLarge = RoundedCornerShape(12.dp)

    val FastFocusAnimationSpec = tween<Float>(durationMillis = 150, easing = FastOutSlowInEasing)

    const val FOCUS_SCALE_FACTOR = 1.05f
    const val FOCUS_SCALE_FACTOR_LARGE = 1.10f

    const val ALPHA_HIGH = 0.85f
    const val ALPHA_MEDIUM = 0.60f
    const val ALPHA_LOW = 0.35f
}

enum class MovieCardSize {
    MEDIUM,
    SMALL
}

enum class MovieCardType {
    DEFAULT,
    POSTER,
    MORE_LIKE_THIS,
    MORE_LIKE_THIS_WITH_PLAY,
    EPISODE,
    TRAILER,
    PLAYER_PREVIEW,
    TOP10,
    CONTINUE_WATCHING
}

enum class MovieBadgeType(val stringRes: Int) {
    TOP_10(R.string.badge_top_10),
    RECENTLY_ADDED(R.string.badge_recently_added),
    NEW_SEASON(R.string.badge_new_season),
    NEW_EPISODES(R.string.badge_new_episodes),
    LEAVING_SOON(R.string.badge_leaving_soon),
    MUST_WATCH(R.string.badge_must_watch)
}

@Immutable
data class MovieCardItem(
    val title: String,
    val type: MovieCardType = MovieCardType.DEFAULT,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val progress: Float? = null,
    val badge: MovieBadgeType? = null,
    val matchScore: String? = null,
    val maturityRating: String? = null,
    val duration: String? = null,
    val quality: String? = null,
    val top10Rank: Int? = null,
    val showLogo: Boolean = false,
    val showBottomTitle: Boolean = false,
    val synopsis: String? = null,
    val rawItem: Any? = null
)

object MovieCardDefaults {
    const val CardAspectRatioDefault = 16f / 9f
    const val CardAspectRatioPoster = 2f / 3f

    val ActionButtonSize = 36.dp

    fun cardWidth(type: MovieCardType, size: MovieCardSize): Dp = when (type) {
        MovieCardType.POSTER -> when (size) {
            MovieCardSize.MEDIUM -> 140.dp
            MovieCardSize.SMALL -> 110.dp
        }
        else -> when (size) {
            MovieCardSize.MEDIUM -> 218.dp
            MovieCardSize.SMALL -> 128.dp
        }
    }
}
