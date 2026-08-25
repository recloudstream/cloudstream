package com.lagradost.cloudstream3.ui.revamp.compose.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixTheme
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey100
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey200
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey350
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey600
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey700
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey800
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey850
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryBlack
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryRed
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryWhite

enum class AvatarFaceStyle {
    SMILEY,
    WINK,
    ANGRY,
    KIDS,
    MONSTER_GREY,
    MONSTER_BLUE,
    MONSTER_RED,
    MONSTER_YELLOW,
    CHICKEN,
    ZOMBIE,
    PANDA,
    COOL,
    SURPRISED,
    GEOMETRIC
}

data class CloneflixAvatarModel(
    val id: String,
    val name: String,
    val topColor: Color,
    val bottomColor: Color,
    val faceStyle: AvatarFaceStyle = AvatarFaceStyle.SMILEY,
    val accentColor: Color = PrimaryWhite
)

object CloneflixOfficialAvatars {
    val Red = CloneflixAvatarModel("red", "Red Classic", Color(0xFFE50914), Color(0xFF8B0000), AvatarFaceStyle.SMILEY)
    val Green = CloneflixAvatarModel("green", "Green Classic", Color(0xFF2ECC71), Color(0xFF1B8246), AvatarFaceStyle.SMILEY)
    val DarkBlue = CloneflixAvatarModel("dark blue", "Dark Blue", Color(0xFF1E3799), Color(0xFF0C2461), AvatarFaceStyle.SMILEY)
    val Blue = CloneflixAvatarModel("blue", "Blue Classic", Color(0xFF3867D6), Color(0xFF1B3B6F), AvatarFaceStyle.SMILEY)
    val Purple = CloneflixAvatarModel("purple", "Purple", Color(0xFF8854D0), Color(0xFF5B2C98), AvatarFaceStyle.SMILEY)
    val Pink = CloneflixAvatarModel("pink", "Pink Classic", Color(0xFFFD79A8), Color(0xFFD63031), AvatarFaceStyle.SMILEY)
    val Yellow = CloneflixAvatarModel("yellow", "Yellow Gold", Color(0xFFF1C40F), Color(0xFFD35400), AvatarFaceStyle.SMILEY)
    val Kids = CloneflixAvatarModel("kids", "Kids", Color(0xFFF39C12), Color(0xFFE67E22), AvatarFaceStyle.KIDS)
    val AngryMan = CloneflixAvatarModel("angryman", "Angry Man", Color(0xFFE74C3C), Color(0xFF7B1113), AvatarFaceStyle.ANGRY)
    val FluffyGrey = CloneflixAvatarModel("fluffygrey", "Fluffy Grey", Color(0xFF7F8C8D), Color(0xFF34495E), AvatarFaceStyle.MONSTER_GREY)
    val FluffyBlue = CloneflixAvatarModel("fluffyblue", "Fluffy Blue", Color(0xFF00A8FF), Color(0xFF0097E6), AvatarFaceStyle.MONSTER_BLUE)
    val FluffyRed = CloneflixAvatarModel("fluffyred", "Fluffy Red", Color(0xFFEA2027), Color(0xFFB53471), AvatarFaceStyle.MONSTER_RED)
    val FluffyYellow = CloneflixAvatarModel("fluffyyellow", "Fluffy Yellow", Color(0xFFFFC312), Color(0xFFF79F1F), AvatarFaceStyle.MONSTER_YELLOW)
    val Chicken = CloneflixAvatarModel("chicken", "Chicken", Color(0xFFEE5253), Color(0xFFEA2027), AvatarFaceStyle.CHICKEN)
    val Zombie = CloneflixAvatarModel("zombi", "Zombie", Color(0xFF10AC84), Color(0xFF1DD1A1), AvatarFaceStyle.ZOMBIE)
    val Panda = CloneflixAvatarModel("panda", "Panda", Color(0xFFECEFF1), Color(0xFFCFD8DC), AvatarFaceStyle.PANDA)

    val Popular = listOf(
        Red, Green, DarkBlue, Blue,
        Purple, Pink, Yellow, Kids,
        AngryMan, FluffyGrey, FluffyBlue, FluffyRed,
        FluffyYellow, Chicken, Zombie, Panda
    )

    private val Palette = listOf(
        Pair(Color(0xFFE50914), Color(0xFF9E0000)),
        Pair(Color(0xFF0984E3), Color(0xFF1B3B6F)),
        Pair(Color(0xFF00B894), Color(0xFF006266)),
        Pair(Color(0xFF6C5CE7), Color(0xFF4834D4)),
        Pair(Color(0xFFFD79A8), Color(0xFFE84393)),
        Pair(Color(0xFFFDCB6E), Color(0xFFE17055)),
        Pair(Color(0xFF00CEC9), Color(0xFF0984E3)),
        Pair(Color(0xFFE17055), Color(0xFFD63031)),
        Pair(Color(0xFF2C3E50), Color(0xFF1E272E)),
        Pair(Color(0xFFFF7675), Color(0xFFD63031)),
        Pair(Color(0xFF55EFC4), Color(0xFF00B894)),
        Pair(Color(0xFFA29BFE), Color(0xFF6C5CE7))
    )

    fun getAvatarByIndex(index: Int): CloneflixAvatarModel {
        val numStr = String.format("%02d", index.coerceAtLeast(1))
        val colorPair = Palette[(index - 1) % Palette.size]
        val styles = AvatarFaceStyle.values()
        val faceStyle = styles[(index - 1) % styles.size]
        return CloneflixAvatarModel(
            id = numStr,
            name = numStr,
            topColor = colorPair.first,
            bottomColor = colorPair.second,
            faceStyle = faceStyle
        )
    }

    fun getAllOthers(count: Int = 36): List<CloneflixAvatarModel> {
        return (1..count).map { getAvatarByIndex(it) }
    }
}

@Composable
fun CloneflixAvatarGraphic(
    avatar: CloneflixAvatarModel,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(avatar.topColor, avatar.bottomColor),
                startY = 0f,
                endY = h
            ),
            size = Size(w, h),
            cornerRadius = CornerRadius(w * 0.08f, h * 0.08f)
        )

        when (avatar.faceStyle) {
            AvatarFaceStyle.SMILEY -> drawSmileyFace(w, h, PrimaryWhite)
            AvatarFaceStyle.WINK -> drawWinkFace(w, h, PrimaryWhite)
            AvatarFaceStyle.ANGRY -> drawAngryFace(w, h, PrimaryWhite)
            AvatarFaceStyle.KIDS -> drawKidsFace(w, h, PrimaryWhite)
            AvatarFaceStyle.MONSTER_GREY -> drawMonsterGrey(w, h)
            AvatarFaceStyle.MONSTER_BLUE -> drawMonsterBlue(w, h)
            AvatarFaceStyle.MONSTER_RED -> drawMonsterRed(w, h)
            AvatarFaceStyle.MONSTER_YELLOW -> drawMonsterYellow(w, h)
            AvatarFaceStyle.CHICKEN -> drawChickenFace(w, h)
            AvatarFaceStyle.ZOMBIE -> drawZombieFace(w, h)
            AvatarFaceStyle.PANDA -> drawPandaFace(w, h)
            AvatarFaceStyle.COOL -> drawCoolFace(w, h, PrimaryWhite)
            AvatarFaceStyle.SURPRISED -> drawSurprisedFace(w, h, PrimaryWhite)
            AvatarFaceStyle.GEOMETRIC -> drawGeometricFace(w, h, avatar.id)
        }
    }
}

private fun DrawScope.drawSmileyFace(w: Float, h: Float, color: Color) {
    val eyeRadius = w * 0.065f
    val eyeY = h * 0.38f
    val leftEyeX = w * 0.35f
    val rightEyeX = w * 0.65f

    drawCircle(color = color, radius = eyeRadius, center = Offset(leftEyeX, eyeY))
    drawCircle(color = color, radius = eyeRadius, center = Offset(rightEyeX, eyeY))

    val mouthPath = Path().apply {
        moveTo(w * 0.30f, h * 0.56f)
        quadraticTo(w * 0.50f, h * 0.74f, w * 0.70f, h * 0.56f)
    }
    drawPath(
        path = mouthPath,
        color = color,
        style = Stroke(width = w * 0.065f, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawWinkFace(w: Float, h: Float, color: Color) {
    val eyeRadius = w * 0.065f
    val eyeY = h * 0.38f

    drawCircle(color = color, radius = eyeRadius, center = Offset(w * 0.35f, eyeY))

    val winkPath = Path().apply {
        moveTo(w * 0.60f, eyeY)
        quadraticTo(w * 0.67f, eyeY - h * 0.04f, w * 0.74f, eyeY)
    }
    drawPath(path = winkPath, color = color, style = Stroke(width = w * 0.06f, cap = StrokeCap.Round))

    val mouthPath = Path().apply {
        moveTo(w * 0.34f, h * 0.58f)
        quadraticTo(w * 0.52f, h * 0.72f, w * 0.72f, h * 0.52f)
    }
    drawPath(path = mouthPath, color = color, style = Stroke(width = w * 0.065f, cap = StrokeCap.Round))
}

private fun DrawScope.drawAngryFace(w: Float, h: Float, color: Color) {
    val leftBrow = Path().apply {
        moveTo(w * 0.22f, h * 0.28f)
        lineTo(w * 0.44f, h * 0.36f)
    }
    val rightBrow = Path().apply {
        moveTo(w * 0.78f, h * 0.28f)
        lineTo(w * 0.56f, h * 0.36f)
    }
    drawPath(leftBrow, color = color, style = Stroke(width = w * 0.07f, cap = StrokeCap.Round))
    drawPath(rightBrow, color = color, style = Stroke(width = w * 0.07f, cap = StrokeCap.Round))

    drawCircle(color = color, radius = w * 0.055f, center = Offset(w * 0.34f, h * 0.42f))
    drawCircle(color = color, radius = w * 0.055f, center = Offset(w * 0.66f, h * 0.42f))

    val mouthPath = Path().apply {
        moveTo(w * 0.32f, h * 0.68f)
        quadraticTo(w * 0.50f, h * 0.56f, w * 0.68f, h * 0.68f)
    }
    drawPath(path = mouthPath, color = color, style = Stroke(width = w * 0.065f, cap = StrokeCap.Round))
}

private fun DrawScope.drawKidsFace(w: Float, h: Float, color: Color) {
    drawCircle(color = color, radius = w * 0.10f, center = Offset(w * 0.35f, h * 0.38f))
    drawCircle(color = color, radius = w * 0.10f, center = Offset(w * 0.65f, h * 0.38f))
    drawCircle(color = PrimaryBlack, radius = w * 0.05f, center = Offset(w * 0.35f, h * 0.38f))
    drawCircle(color = PrimaryBlack, radius = w * 0.05f, center = Offset(w * 0.65f, h * 0.38f))
    drawCircle(color = PrimaryWhite, radius = w * 0.02f, center = Offset(w * 0.37f, h * 0.36f))
    drawCircle(color = PrimaryWhite, radius = w * 0.02f, center = Offset(w * 0.67f, h * 0.36f))

    val mouthPath = Path().apply {
        moveTo(w * 0.28f, h * 0.58f)
        quadraticTo(w * 0.50f, h * 0.78f, w * 0.72f, h * 0.58f)
    }
    drawPath(path = mouthPath, color = color, style = Stroke(width = w * 0.065f, cap = StrokeCap.Round))
}

private fun DrawScope.drawMonsterGrey(w: Float, h: Float) {
    drawCircle(color = PrimaryWhite, radius = w * 0.08f, center = Offset(w * 0.25f, h * 0.20f))
    drawCircle(color = PrimaryWhite, radius = w * 0.08f, center = Offset(w * 0.75f, h * 0.20f))

    drawCircle(color = PrimaryWhite, radius = w * 0.07f, center = Offset(w * 0.35f, h * 0.40f))
    drawCircle(color = PrimaryWhite, radius = w * 0.07f, center = Offset(w * 0.65f, h * 0.40f))
    drawCircle(color = PrimaryBlack, radius = w * 0.035f, center = Offset(w * 0.35f, h * 0.40f))
    drawCircle(color = PrimaryBlack, radius = w * 0.035f, center = Offset(w * 0.65f, h * 0.40f))

    val mouthPath = Path().apply {
        moveTo(w * 0.30f, h * 0.60f)
        lineTo(w * 0.70f, h * 0.60f)
        lineTo(w * 0.65f, h * 0.70f)
        lineTo(w * 0.35f, h * 0.70f)
        close()
    }
    drawPath(mouthPath, color = PrimaryWhite)
}

private fun DrawScope.drawMonsterBlue(w: Float, h: Float) {
    drawCircle(color = PrimaryWhite, radius = w * 0.13f, center = Offset(w * 0.50f, h * 0.36f))
    drawCircle(color = Color(0xFF0984E3), radius = w * 0.07f, center = Offset(w * 0.50f, h * 0.36f))
    drawCircle(color = PrimaryBlack, radius = w * 0.04f, center = Offset(w * 0.50f, h * 0.36f))
    drawCircle(color = PrimaryWhite, radius = w * 0.018f, center = Offset(w * 0.52f, h * 0.34f))

    val mouthPath = Path().apply {
        moveTo(w * 0.32f, h * 0.60f)
        quadraticTo(w * 0.50f, h * 0.76f, w * 0.68f, h * 0.60f)
    }
    drawPath(path = mouthPath, color = PrimaryWhite, style = Stroke(width = w * 0.06f, cap = StrokeCap.Round))

    val fang = Path().apply {
        moveTo(w * 0.40f, h * 0.60f)
        lineTo(w * 0.43f, h * 0.68f)
        lineTo(w * 0.46f, h * 0.60f)
        close()
    }
    drawPath(fang, color = PrimaryWhite)
}

private fun DrawScope.drawMonsterRed(w: Float, h: Float) {
    val leftEye = Path().apply {
        moveTo(w * 0.28f, h * 0.36f)
        lineTo(w * 0.44f, h * 0.40f)
        lineTo(w * 0.32f, h * 0.44f)
        close()
    }
    val rightEye = Path().apply {
        moveTo(w * 0.72f, h * 0.36f)
        lineTo(w * 0.56f, h * 0.40f)
        lineTo(w * 0.68f, h * 0.44f)
        close()
    }
    drawPath(leftEye, color = Color(0xFFFFD32A))
    drawPath(rightEye, color = Color(0xFFFFD32A))

    val mouth = Path().apply {
        moveTo(w * 0.26f, h * 0.56f)
        quadraticTo(w * 0.50f, h * 0.78f, w * 0.74f, h * 0.56f)
        quadraticTo(w * 0.50f, h * 0.64f, w * 0.26f, h * 0.56f)
        close()
    }
    drawPath(mouth, color = PrimaryWhite)
}

private fun DrawScope.drawMonsterYellow(w: Float, h: Float) {
    drawSmileyFace(w, h, Color(0xFF2C3E50))
}

private fun DrawScope.drawChickenFace(w: Float, h: Float) {
    drawCircle(color = Color(0xFFFF4757), radius = w * 0.09f, center = Offset(w * 0.50f, h * 0.16f))
    drawCircle(color = Color(0xFFFF4757), radius = w * 0.07f, center = Offset(w * 0.38f, h * 0.19f))
    drawCircle(color = Color(0xFFFF4757), radius = w * 0.07f, center = Offset(w * 0.62f, h * 0.19f))

    drawCircle(color = PrimaryWhite, radius = w * 0.06f, center = Offset(w * 0.35f, h * 0.40f))
    drawCircle(color = PrimaryWhite, radius = w * 0.06f, center = Offset(w * 0.65f, h * 0.40f))
    drawCircle(color = PrimaryBlack, radius = w * 0.03f, center = Offset(w * 0.35f, h * 0.40f))
    drawCircle(color = PrimaryBlack, radius = w * 0.03f, center = Offset(w * 0.65f, h * 0.40f))

    val beak = Path().apply {
        moveTo(w * 0.42f, h * 0.48f)
        lineTo(w * 0.58f, h * 0.48f)
        lineTo(w * 0.50f, h * 0.66f)
        close()
    }
    drawPath(beak, color = Color(0xFFFFA502))
}

private fun DrawScope.drawZombieFace(w: Float, h: Float) {
    drawCircle(color = PrimaryWhite, radius = w * 0.08f, center = Offset(w * 0.35f, h * 0.38f))
    drawCircle(color = PrimaryBlack, radius = w * 0.03f, center = Offset(w * 0.35f, h * 0.38f))

    val xPath = Path().apply {
        moveTo(w * 0.60f, h * 0.34f); lineTo(w * 0.70f, h * 0.42f)
        moveTo(w * 0.70f, h * 0.34f); lineTo(w * 0.60f, h * 0.42f)
    }
    drawPath(xPath, color = PrimaryWhite, style = Stroke(width = w * 0.04f, cap = StrokeCap.Round))

    val mouthLine = Path().apply {
        moveTo(w * 0.30f, h * 0.62f); lineTo(w * 0.70f, h * 0.62f)
        moveTo(w * 0.38f, h * 0.56f); lineTo(w * 0.38f, h * 0.68f)
        moveTo(w * 0.50f, h * 0.56f); lineTo(w * 0.50f, h * 0.68f)
        moveTo(w * 0.62f, h * 0.56f); lineTo(w * 0.62f, h * 0.68f)
    }
    drawPath(mouthLine, color = PrimaryWhite, style = Stroke(width = w * 0.04f, cap = StrokeCap.Round))
}

private fun DrawScope.drawPandaFace(w: Float, h: Float) {
    drawCircle(color = Color(0xFF2F3640), radius = w * 0.11f, center = Offset(w * 0.22f, h * 0.22f))
    drawCircle(color = Color(0xFF2F3640), radius = w * 0.11f, center = Offset(w * 0.78f, h * 0.22f))

    drawCircle(color = Color(0xFF2F3640), radius = w * 0.09f, center = Offset(w * 0.36f, h * 0.44f))
    drawCircle(color = Color(0xFF2F3640), radius = w * 0.64f, center = Offset(w * 0.64f, h * 0.44f))
    drawCircle(color = PrimaryWhite, radius = w * 0.035f, center = Offset(w * 0.36f, h * 0.43f))
    drawCircle(color = PrimaryWhite, radius = w * 0.035f, center = Offset(w * 0.64f, h * 0.43f))

    drawCircle(color = Color(0xFF2F3640), radius = w * 0.04f, center = Offset(w * 0.50f, h * 0.56f))
    val mouthPath = Path().apply {
        moveTo(w * 0.42f, h * 0.64f)
        quadraticTo(w * 0.50f, h * 0.70f, w * 0.58f, h * 0.64f)
    }
    drawPath(path = mouthPath, color = Color(0xFF2F3640), style = Stroke(width = w * 0.04f, cap = StrokeCap.Round))
}

private fun DrawScope.drawCoolFace(w: Float, h: Float, color: Color) {
    val glasses = Path().apply {
        moveTo(w * 0.22f, h * 0.36f)
        lineTo(w * 0.78f, h * 0.36f)
        lineTo(w * 0.74f, h * 0.48f)
        lineTo(w * 0.54f, h * 0.48f)
        lineTo(w * 0.50f, h * 0.40f)
        lineTo(w * 0.46f, h * 0.48f)
        lineTo(w * 0.26f, h * 0.48f)
        close()
    }
    drawPath(glasses, color = PrimaryBlack)

    val mouthPath = Path().apply {
        moveTo(w * 0.34f, h * 0.62f)
        quadraticTo(w * 0.50f, h * 0.76f, w * 0.68f, h * 0.58f)
    }
    drawPath(path = mouthPath, color = color, style = Stroke(width = w * 0.06f, cap = StrokeCap.Round))
}

private fun DrawScope.drawSurprisedFace(w: Float, h: Float, color: Color) {
    drawCircle(color = color, radius = w * 0.065f, center = Offset(w * 0.35f, h * 0.36f))
    drawCircle(color = color, radius = w * 0.065f, center = Offset(w * 0.65f, h * 0.36f))
    drawCircle(color = color, radius = w * 0.08f, center = Offset(w * 0.50f, h * 0.62f))
}

private fun DrawScope.drawGeometricFace(w: Float, h: Float, text: String) {
    drawSmileyFace(w, h, PrimaryWhite)
}

@Composable
fun CloneflixSmallAvatar(
    avatar: CloneflixAvatarModel,
    modifier: Modifier = Modifier,
    showMenuArrow: Boolean = false,
    size: Dp = 32.dp,
    onClick: (() -> Unit)? = null,
    isExpanded: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "SmallAvatarArrowRotation"
    )

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        label = "SmallAvatarScale"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(4.dp))
            .then(
                if (isFocused) Modifier.border(BorderStroke(2.dp, PrimaryWhite), RoundedCornerShape(4.dp))
                else Modifier
            )
            .then(
                if (onClick != null) {
                    Modifier
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.Button,
                            onClick = onClick
                        )
                        .focusable(interactionSource = interactionSource)
                } else {
                    Modifier.focusable(interactionSource = interactionSource)
                }
            )
            .padding(if (showMenuArrow) 4.dp else 0.dp)
            .semantics {
                role = Role.Button
                contentDescription = "User profile ${avatar.name}${if (showMenuArrow) ", menu dropdown" else ""}"
            }
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(3.dp))
        ) {
            CloneflixAvatarGraphic(avatar = avatar)
        }

        if (showMenuArrow) {
            Icon(
                painter = painterResource(id = R.drawable.cloneflix_ic_arrow_down),
                contentDescription = null,
                tint = if (isFocused) PrimaryWhite else Grey100,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(rotationAngle)
            )
        }
    }
}

@Composable
fun CloneflixLargeAvatar(
    name: String,
    modifier: Modifier = Modifier,
    avatar: CloneflixAvatarModel? = null,
    isAddProfile: Boolean = false,
    size: Dp = 144.dp,
    isHoverState: Boolean = false,
    onClick: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocusedByInteraction by interactionSource.collectIsFocusedAsState()
    val isFocused = isFocusedByInteraction || isHoverState

    val typography = CloneflixTheme.typography
    val colors = CloneflixTheme.colors

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1.0f,
        label = "LargeAvatarScale"
    )

    val borderWidth = if (isFocused) 2.5.dp else 0.dp
    val borderColor = if (isFocused) PrimaryWhite else Color.Transparent

    val textColor = when {
        isFocused -> PrimaryWhite
        isAddProfile -> Grey200
        else -> colors.textPrimary
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(size + 16.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(vertical = 6.dp)
            .semantics {
                role = Role.Button
                contentDescription = if (isAddProfile) "Add Profile" else "Profile $name"
            }
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(6.dp))
                .border(BorderStroke(borderWidth, borderColor), RoundedCornerShape(6.dp))
                .background(if (isAddProfile) Grey800 else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            if (isAddProfile) {
                Box(
                    modifier = Modifier
                        .size(size * 0.54f)
                        .clip(CircleShape)
                        .background(if (isFocused) Grey700 else Grey850)
                        .border(
                            BorderStroke(
                                1.5.dp,
                                if (isFocused) PrimaryWhite else Grey350
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.cloneflix_ic_plus),
                        contentDescription = null,
                        tint = if (isFocused) PrimaryWhite else Grey200,
                        modifier = Modifier.size(size * 0.30f)
                    )
                }
            } else if (avatar != null) {
                CloneflixAvatarGraphic(avatar = avatar)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = name,
            style = if (isFocused) typography.mediumBody.copy(fontWeight = FontWeight.Bold) else typography.mediumBody,
            fontSize = 16.sp,
            color = textColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun CloneflixUserProfilesRow(
    profiles: List<Pair<String, CloneflixAvatarModel>>,
    selectedProfileName: String,
    onProfileClick: (String, CloneflixAvatarModel) -> Unit,
    onAddProfileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        profiles.forEach { (profileName, avatar) ->
            CloneflixLargeAvatar(
                name = profileName,
                avatar = avatar,
                isAddProfile = false,
                isHoverState = profileName == selectedProfileName,
                onClick = { onProfileClick(profileName, avatar) }
            )
        }

        CloneflixLargeAvatar(
            name = "Add Profile",
            avatar = null,
            isAddProfile = true,
            onClick = onAddProfileClick
        )
    }
}

private val PolygonShape = GenericShape { size, _ ->
    moveTo(size.width / 2f, 0f)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}

@Composable
fun CloneflixProfileMenuDropdown(
    currentProfiles: List<Pair<String, CloneflixAvatarModel>>,
    onProfileSelected: (String) -> Unit,
    onManageProfilesClick: () -> Unit,
    onTransferProfilesClick: () -> Unit,
    onAccountClick: () -> Unit,
    onHelpCenterClick: () -> Unit,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val typography = CloneflixTheme.typography

    Column(
        horizontalAlignment = Alignment.End,
        modifier = modifier.width(224.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(end = 24.dp)
                .size(width = 16.dp, height = 10.dp)
                .clip(PolygonShape)
                .background(Grey800)
                .border(BorderStroke(1.dp, Grey600), PolygonShape)
        )

        Surface(
            color = Color(0xFF141414),
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, Grey700),
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-1).dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                currentProfiles.forEach { (name, avatar) ->
                    ProfileMenuItem(
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            ) {
                                CloneflixAvatarGraphic(avatar = avatar)
                            }
                        },
                        label = name,
                        onClick = { onProfileSelected(name) }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                CloneflixDivider(modifier = Modifier.padding(horizontal = 8.dp))
                Spacer(modifier = Modifier.height(4.dp))

                ProfileMenuItem(
                    iconRes = R.drawable.cloneflix_ic_edit,
                    label = "Manage Profiles",
                    onClick = onManageProfilesClick
                )

                ProfileMenuItem(
                    iconRes = R.drawable.cloneflix_ic_person,
                    label = "Transfer Profiles",
                    onClick = onTransferProfilesClick
                )

                ProfileMenuItem(
                    iconRes = R.drawable.cloneflix_ic_account,
                    label = "Account",
                    onClick = onAccountClick
                )

                ProfileMenuItem(
                    iconRes = R.drawable.cloneflix_ic_help,
                    label = "Help Center",
                    onClick = onHelpCenterClick
                )

                Spacer(modifier = Modifier.height(4.dp))
                CloneflixDivider(modifier = Modifier.padding(horizontal = 8.dp))
                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = onSignOutClick)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sign out of Cloneflix",
                        style = typography.mediumSmallBody,
                        color = PrimaryWhite,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileMenuItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconRes: Int? = null,
    icon: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val typography = CloneflixTheme.typography

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(if (isFocused) Grey800 else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        if (icon != null) {
            icon()
        } else if (iconRes != null) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = if (isFocused) PrimaryWhite else Grey100,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = label,
            style = typography.regularBody,
            fontSize = 13.sp,
            color = if (isFocused) PrimaryWhite else Grey100,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
