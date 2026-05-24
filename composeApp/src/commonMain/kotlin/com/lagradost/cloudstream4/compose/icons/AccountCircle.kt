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
public val account_circle: ImageVector
  get() {
    if (_account_circle != null) {
      return _account_circle!!
    }
    _account_circle =
      ImageVector.Builder(
          name = "account_circle",
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
            moveTo(5.85f, 17.1f)
            quadTo(7.13f, 16.13f, 8.7f, 15.56f)
            reflectiveQuadTo(12f, 15f)
            reflectiveQuadToRelative(3.3f, 0.56f)
            reflectiveQuadToRelative(2.85f, 1.54f)
            quadToRelative(0.88f, -1.03f, 1.36f, -2.33f)
            reflectiveQuadTo(20f, 12f)
            quadTo(20f, 8.67f, 17.66f, 6.34f)
            reflectiveQuadTo(12f, 4f)
            quadTo(8.68f, 4f, 6.34f, 6.34f)
            reflectiveQuadTo(4f, 12f)
            quadToRelative(0f, 1.47f, 0.49f, 2.78f)
            quadToRelative(0.49f, 1.3f, 1.36f, 2.33f)
            close()
            moveTo(9.51f, 11.99f)
            quadTo(8.5f, 10.98f, 8.5f, 9.5f)
            quadTo(8.5f, 8.02f, 9.51f, 7.01f)
            reflectiveQuadTo(12f, 6f)
            reflectiveQuadToRelative(2.49f, 1.01f)
            reflectiveQuadTo(15.5f, 9.5f)
            reflectiveQuadToRelative(-1.01f, 2.49f)
            reflectiveQuadTo(12f, 13f)
            quadTo(10.53f, 13f, 9.51f, 11.99f)
            close()
            moveTo(12f, 22f)
            quadTo(9.93f, 22f, 8.1f, 21.21f)
            quadTo(6.28f, 20.43f, 4.93f, 19.08f)
            quadTo(3.58f, 17.73f, 2.79f, 15.9f)
            reflectiveQuadTo(2f, 12f)
            quadTo(2f, 9.92f, 2.79f, 8.1f)
            quadTo(3.58f, 6.27f, 4.93f, 4.93f)
            quadTo(6.28f, 3.57f, 8.1f, 2.79f)
            quadTo(9.93f, 2f, 12f, 2f)
            reflectiveQuadToRelative(3.9f, 0.79f)
            reflectiveQuadToRelative(3.17f, 2.14f)
            quadToRelative(1.35f, 1.35f, 2.14f, 3.17f)
            quadTo(22f, 9.92f, 22f, 12f)
            reflectiveQuadToRelative(-0.79f, 3.9f)
            reflectiveQuadToRelative(-2.14f, 3.17f)
            quadToRelative(-1.35f, 1.35f, -3.17f, 2.14f)
            reflectiveQuadTo(12f, 22f)
            close()
            moveToRelative(2.5f, -2.39f)
            quadToRelative(1.18f, -0.39f, 2.15f, -1.11f)
            quadTo(15.68f, 17.77f, 14.5f, 17.39f)
            reflectiveQuadTo(12f, 17f)
            reflectiveQuadTo(9.5f, 17.39f)
            quadTo(8.33f, 17.77f, 7.35f, 18.5f)
            quadToRelative(0.98f, 0.73f, 2.15f, 1.11f)
            reflectiveQuadTo(12f, 20f)
            reflectiveQuadToRelative(2.5f, -0.39f)
            close()
            moveTo(13.08f, 10.58f)
            quadTo(13.5f, 10.15f, 13.5f, 9.5f)
            reflectiveQuadTo(13.08f, 8.42f)
            reflectiveQuadTo(12f, 8f)
            reflectiveQuadTo(10.93f, 8.42f)
            reflectiveQuadTo(10.5f, 9.5f)
            reflectiveQuadToRelative(0.43f, 1.07f)
            reflectiveQuadTo(12f, 11f)
            reflectiveQuadToRelative(1.08f, -0.43f)
            close()
            moveTo(12f, 9.5f)
            close()
            moveToRelative(0f, 9f)
            close()
          }
        }
        .build()
    return _account_circle!!
  }

private var _account_circle: ImageVector? = null
