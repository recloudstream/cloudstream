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
public val extension: ImageVector
  get() {
    if (_extension != null) {
      return _extension!!
    }
    _extension =
      ImageVector.Builder(
          name = "extension",
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
            moveTo(8.8f, 21f)
            horizontalLineTo(5f)
            quadTo(4.18f, 21f, 3.59f, 20.41f)
            reflectiveQuadTo(3f, 19f)
            verticalLineTo(15.2f)
            quadToRelative(1.2f, 0f, 2.1f, -0.76f)
            reflectiveQuadTo(6f, 12.5f)
            reflectiveQuadTo(5.1f, 10.56f)
            reflectiveQuadTo(3f, 9.8f)
            verticalLineTo(6f)
            quadTo(3f, 5.18f, 3.59f, 4.59f)
            reflectiveQuadTo(5f, 4f)
            horizontalLineTo(9f)
            quadTo(9f, 2.95f, 9.73f, 2.22f)
            reflectiveQuadTo(11.5f, 1.5f)
            reflectiveQuadToRelative(1.78f, 0.72f)
            reflectiveQuadTo(14f, 4f)
            horizontalLineToRelative(4f)
            quadToRelative(0.82f, 0f, 1.41f, 0.59f)
            quadTo(20f, 5.18f, 20f, 6f)
            verticalLineToRelative(4f)
            quadToRelative(1.05f, 0f, 1.78f, 0.72f)
            reflectiveQuadTo(22.5f, 12.5f)
            reflectiveQuadToRelative(-0.72f, 1.77f)
            reflectiveQuadTo(20f, 15f)
            verticalLineToRelative(4f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(18f, 21f)
            horizontalLineTo(14.2f)
            quadToRelative(0f, -1.25f, -0.79f, -2.13f)
            reflectiveQuadTo(11.5f, 18f)
            reflectiveQuadTo(9.59f, 18.88f)
            reflectiveQuadTo(8.8f, 21f)
            close()
            moveTo(5f, 19f)
            horizontalLineTo(7.13f)
            quadToRelative(0.6f, -1.65f, 1.93f, -2.32f)
            reflectiveQuadTo(11.5f, 16f)
            reflectiveQuadToRelative(2.45f, 0.68f)
            reflectiveQuadTo(15.88f, 19f)
            horizontalLineTo(18f)
            verticalLineTo(13f)
            horizontalLineToRelative(2f)
            quadToRelative(0.2f, 0f, 0.35f, -0.15f)
            reflectiveQuadTo(20.5f, 12.5f)
            reflectiveQuadTo(20.35f, 12.15f)
            reflectiveQuadTo(20f, 12f)
            horizontalLineTo(18f)
            verticalLineTo(6f)
            horizontalLineTo(12f)
            verticalLineTo(4f)
            quadTo(12f, 3.8f, 11.85f, 3.65f)
            reflectiveQuadTo(11.5f, 3.5f)
            reflectiveQuadTo(11.15f, 3.65f)
            reflectiveQuadTo(11f, 4f)
            verticalLineTo(6f)
            horizontalLineTo(5f)
            verticalLineTo(8.2f)
            quadTo(6.35f, 8.7f, 7.18f, 9.88f)
            reflectiveQuadTo(8f, 12.5f)
            quadToRelative(0f, 1.42f, -0.82f, 2.6f)
            reflectiveQuadTo(5f, 16.8f)
            verticalLineTo(19f)
            close()
            moveToRelative(6.5f, -6.5f)
            close()
          }
        }
        .build()
    return _extension!!
  }

private var _extension: ImageVector? = null
