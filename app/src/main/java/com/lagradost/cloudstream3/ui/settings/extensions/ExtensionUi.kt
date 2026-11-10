package com.lagradost.cloudstream3.ui.settings.extensions

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream4.compose.BlackTextField

const val SHAREABLE_REPO_SEPARATOR = " : "

/** Search belongs to the extension manager, not the deleted app-wide settings screen. */
@Composable
fun ExtensionSearch(state: TextFieldState) {
    BlackTextField(
        value = state.text.toString(),
        placeHolder = stringResource(R.string.search_hint),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        onValueChange = { value -> state.edit { replace(0, length, value) } }
    )
}

fun TvType.toStringRes() = when (this) {
    TvType.TvSeries -> R.string.tv_series_singular
    TvType.Anime -> R.string.anime_singular
    TvType.OVA -> R.string.ova_singular
    TvType.AnimeMovie, TvType.Movie -> R.string.movies_singular
    TvType.Cartoon -> R.string.cartoons_singular
    TvType.Documentary -> R.string.documentaries_singular
    TvType.Torrent -> R.string.torrent_singular
    TvType.AsianDrama -> R.string.asian_drama_singular
    TvType.Live -> R.string.live_singular
    TvType.Others -> R.string.other_singular
    TvType.NSFW -> R.string.nsfw_singular
    TvType.Music -> R.string.music_singular
    TvType.AudioBook -> R.string.audio_book_singular
    TvType.CustomMedia -> R.string.custom_media_singular
    TvType.Audio -> R.string.audio_singular
    TvType.Podcast -> R.string.podcast_singular
    TvType.Video -> R.string.video_singular
}
