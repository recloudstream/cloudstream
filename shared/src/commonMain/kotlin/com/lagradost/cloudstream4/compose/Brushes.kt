package com.lagradost.cloudstream4.compose

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import com.lagradost.cloudstream4.theme.LocalSharedInfiniteTransition

@Composable
fun Modifier.gradientEffect(
    gradientColor: Color,
    animationSpec: InfiniteRepeatableSpec<Float> = infiniteRepeatable(
        animation = tween(
            durationMillis = 2000,
            easing = LinearEasing,
        ),
        repeatMode = RepeatMode.Restart,
    ),
): Modifier {
    val progress by LocalSharedInfiniteTransition.current.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = animationSpec,
        label = "GradientProgress",
    )

    val gradientBrush = rememberTransformableBrush {
        Brush.horizontalGradient(
            0.0f to Color.Transparent,
            0.2f to gradientColor,
            0.8f to gradientColor,
            1.0f to Color.Transparent,
            startX = 0f,
            endX = 1.0f,
        )
    }

    return drawWithContent {
        drawContent()
        gradientBrush.transform {
            val x = progress * 1000.toDp().toPx() * 5.0f
            reset()
            translate(x)
            scale(1000.toDp().toPx(), 1f)
        }
        drawRect(gradientBrush)
    }
}

@Stable
class TransformableBrush(
    private val brush: ShaderBrush
) : ShaderBrush() {

    override val intrinsicSize: Size
        get() = brush.intrinsicSize

    private var internalShader: Shader? = null
    private val localMatrix: Matrix = Matrix()

    override fun createShader(size: Size): Shader {
        return brush.createShader(size).also {
            internalShader = it
        }
    }

    // Allows transforming the brush by modifying the localMatrix
    fun transform(transformer: Matrix.() -> Unit) {
        transformer.invoke(localMatrix)
        transform = localMatrix
        //internalShader?.transform = localMatrix
    }

    override fun toString(): String {
        return "TransformableBrush(brush=$brush)"
    }
}

@Composable
inline fun rememberTransformableBrush(
    crossinline getBrush: @DisallowComposableCalls () -> Brush,
): TransformableBrush {
    return remember {
        val brush = getBrush()
        check(brush is ShaderBrush)
        TransformableBrush(brush = brush)
    }
}