package com.lagradost.cloudstream4.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.profile_bg_blue
import com.lagradost.cloudstream4.generated.resources.profile_bg_dark_blue
import com.lagradost.cloudstream4.generated.resources.profile_bg_orange
import com.lagradost.cloudstream4.generated.resources.profile_bg_pink
import com.lagradost.cloudstream4.generated.resources.profile_bg_purple
import com.lagradost.cloudstream4.generated.resources.profile_bg_red
import com.lagradost.cloudstream4.generated.resources.profile_bg_teal
import com.lagradost.cloudstream4.generated.resources.profile_picture_desc
import com.lagradost.cloudstream4.ui.theme.CloudStreamTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

enum class ProfileImage {
    DARK_BLUE, BLUE, ORANGE, PINK, PURPLE, RED, TEAL;
}

@Composable
private fun ProfileImage.toRes(): DrawableResource = when (this) {
    ProfileImage.DARK_BLUE -> Res.drawable.profile_bg_dark_blue
    ProfileImage.BLUE -> Res.drawable.profile_bg_blue
    ProfileImage.ORANGE -> Res.drawable.profile_bg_orange
    ProfileImage.PINK -> Res.drawable.profile_bg_pink
    ProfileImage.PURPLE -> Res.drawable.profile_bg_purple
    ProfileImage.RED -> Res.drawable.profile_bg_red
    ProfileImage.TEAL -> Res.drawable.profile_bg_teal
}

/**
 * Circular profile picture component.
 *
 * Shows [AsyncImage] from [profilePictureUrl] if not null,
 * otherwise falls back to the local [profileImage] background.
 *
 * @param profileImage Local background image fallback
 * @param size Diameter of the circle, default 50.dp
 * @param profilePictureUrl Optional remote URL to load via Coil
 */
@Composable
fun ProfilePicture(
    profileImage: ProfileImage,
    size: Dp = 50.dp,
    profilePictureUrl: String? = null,
) {
    val colors = CloudStreamTheme.colors
    Box(
        modifier = Modifier
            .size(size)
            .border(2.dp, colors.onBackground.copy(alpha = 0.2f), CircleShape)
            .clip(CircleShape),
    ) {
        if (profilePictureUrl != null) {
            AsyncImage(
                model = profilePictureUrl,
                contentDescription = stringResource(Res.string.profile_picture_desc),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Image(
                painter = painterResource(profileImage.toRes()),
                contentDescription = stringResource(Res.string.profile_picture_desc),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Preview(name = "Dark Blue", showBackground = true, backgroundColor = 0xFF111111)
@Composable
private fun ProfilePictureDarkBluePreview() {
    CloudStreamTheme {
        ProfilePicture(
            profileImage = ProfileImage.DARK_BLUE,
            size = 50.dp,
        )
    }
}
