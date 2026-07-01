package com.lagradost.cloudstream4.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
public val palette: ImageVector
  get() {
    if (_palette != null) {
      return _palette!!
    }
    _palette =
      ImageVector.Builder(
          name = "palette",
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
            moveTo(12f, 22f)
            quadTo(9.95f, 22f, 8.13f, 21.21f)
            quadTo(6.3f, 20.43f, 4.94f, 19.06f)
            quadTo(3.58f, 17.7f, 2.79f, 15.88f)
            reflectiveQuadTo(2f, 12f)
            quadTo(2f, 9.92f, 2.81f, 8.1f)
            quadTo(3.63f, 6.27f, 5.01f, 4.93f)
            quadTo(6.4f, 3.57f, 8.25f, 2.79f)
            reflectiveQuadTo(12.2f, 2f)
            quadToRelative(2f, 0f, 3.78f, 0.69f)
            reflectiveQuadToRelative(3.11f, 1.9f)
            reflectiveQuadToRelative(2.13f, 2.88f)
            quadTo(22f, 9.13f, 22f, 11.05f)
            quadToRelative(0f, 2.88f, -1.75f, 4.41f)
            reflectiveQuadTo(16f, 17f)
            horizontalLineTo(14.15f)
            quadToRelative(-0.22f, 0f, -0.31f, 0.13f)
            reflectiveQuadTo(13.75f, 17.4f)
            quadToRelative(0f, 0.3f, 0.38f, 0.86f)
            reflectiveQuadToRelative(0.38f, 1.29f)
            quadToRelative(0f, 1.25f, -0.69f, 1.85f)
            reflectiveQuadTo(12f, 22f)
            close()
            moveTo(12f, 12f)
            close()
            moveTo(7.58f, 12.58f)
            quadTo(8f, 12.15f, 8f, 11.5f)
            reflectiveQuadTo(7.58f, 10.43f)
            reflectiveQuadTo(6.5f, 10f)
            reflectiveQuadTo(5.43f, 10.43f)
            reflectiveQuadTo(5f, 11.5f)
            reflectiveQuadToRelative(0.43f, 1.07f)
            reflectiveQuadTo(6.5f, 13f)
            reflectiveQuadTo(7.58f, 12.58f)
            close()
            moveToRelative(3f, -4f)
            quadTo(11f, 8.15f, 11f, 7.5f)
            reflectiveQuadTo(10.58f, 6.43f)
            reflectiveQuadTo(9.5f, 6f)
            reflectiveQuadTo(8.43f, 6.43f)
            reflectiveQuadTo(8f, 7.5f)
            reflectiveQuadTo(8.43f, 8.57f)
            reflectiveQuadTo(9.5f, 9f)
            reflectiveQuadTo(10.58f, 8.57f)
            close()
            moveToRelative(5f, 0f)
            quadTo(16f, 8.15f, 16f, 7.5f)
            reflectiveQuadTo(15.58f, 6.43f)
            reflectiveQuadTo(14.5f, 6f)
            reflectiveQuadTo(13.43f, 6.43f)
            reflectiveQuadTo(13f, 7.5f)
            reflectiveQuadToRelative(0.43f, 1.07f)
            reflectiveQuadTo(14.5f, 9f)
            reflectiveQuadTo(15.58f, 8.57f)
            close()
            moveToRelative(3f, 4f)
            quadTo(19f, 12.15f, 19f, 11.5f)
            reflectiveQuadTo(18.58f, 10.43f)
            reflectiveQuadTo(17.5f, 10f)
            reflectiveQuadToRelative(-1.07f, 0.42f)
            reflectiveQuadTo(16f, 11.5f)
            reflectiveQuadToRelative(0.43f, 1.07f)
            reflectiveQuadTo(17.5f, 13f)
            reflectiveQuadToRelative(1.07f, -0.43f)
            close()
            moveTo(12f, 20f)
            quadToRelative(0.23f, 0f, 0.36f, -0.13f)
            reflectiveQuadTo(12.5f, 19.55f)
            quadToRelative(0f, -0.35f, -0.38f, -0.82f)
            reflectiveQuadTo(11.75f, 17.3f)
            quadToRelative(0f, -1.05f, 0.73f, -1.68f)
            reflectiveQuadTo(14.25f, 15f)
            horizontalLineTo(16f)
            quadToRelative(1.65f, 0f, 2.82f, -0.96f)
            reflectiveQuadTo(20f, 11.05f)
            quadTo(20f, 8.02f, 17.69f, 6.01f)
            reflectiveQuadTo(12.2f, 4f)
            quadTo(8.8f, 4f, 6.4f, 6.32f)
            reflectiveQuadTo(4f, 12f)
            quadToRelative(0f, 3.32f, 2.34f, 5.66f)
            reflectiveQuadTo(12f, 20f)
            close()
          }
        }
        .build()
    return _palette!!
  }

private var _palette: ImageVector? = null
