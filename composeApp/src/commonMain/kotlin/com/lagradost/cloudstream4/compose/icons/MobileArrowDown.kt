package com.lagradost.cloudstream4.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
public val mobile_arrow_down: ImageVector
  get() {
    if (_mobile_arrow_down != null) {
      return _mobile_arrow_down!!
    }
    _mobile_arrow_down =
      ImageVector.Builder(
          name = "mobile_arrow_down",
          defaultWidth = 24.dp,
          defaultHeight = 24.dp,
          viewportWidth = 24f,
          viewportHeight = 24f,
        )
        .apply {
          path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Bevel,
            strokeLineMiter = 1f,
            pathFillType = PathFillType.Companion.NonZero,
          ) {
            moveTo(7f, 23f)
            quadTo(6.18f, 23f, 5.59f, 22.41f)
            reflectiveQuadTo(5f, 21f)
            verticalLineTo(3f)
            quadTo(5f, 2.17f, 5.59f, 1.59f)
            reflectiveQuadTo(7f, 1f)
            horizontalLineTo(17f)
            quadToRelative(0.82f, 0f, 1.41f, 0.59f)
            reflectiveQuadTo(19f, 3f)
            verticalLineTo(6.1f)
            quadToRelative(0.45f, 0.18f, 0.73f, 0.55f)
            reflectiveQuadTo(20f, 7.5f)
            verticalLineToRelative(2f)
            quadToRelative(0f, 0.47f, -0.27f, 0.85f)
            reflectiveQuadTo(19f, 10.9f)
            verticalLineTo(21f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(17f, 23f)
            horizontalLineTo(7f)
            close()
            moveTo(7f, 21f)
            horizontalLineTo(17f)
            verticalLineTo(3f)
            horizontalLineTo(7f)
            verticalLineTo(21f)
            close()
            moveToRelative(0f, 0f)
            verticalLineTo(3f)
            verticalLineTo(21f)
            close()
            moveToRelative(5f, -5f)
            lineToRelative(4f, -4f)
            lineTo(14.6f, 10.6f)
            lineTo(13f, 12.15f)
            verticalLineTo(8f)
            horizontalLineTo(11f)
            verticalLineToRelative(4.15f)
            lineTo(9.4f, 10.6f)
            lineTo(8f, 12f)
            lineToRelative(4f, 4f)
            close()
          }
        }
        .build()
    return _mobile_arrow_down!!
  }

private var _mobile_arrow_down: ImageVector? = null
