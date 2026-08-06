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
public val storage: ImageVector
  get() {
    if (_storage != null) {
      return _storage!!
    }
    _storage =
      ImageVector.Builder(
          name = "storage",
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
            moveTo(3f, 20f)
            verticalLineTo(16f)
            horizontalLineTo(21f)
            verticalLineToRelative(4f)
            horizontalLineTo(3f)
            close()
            moveTo(5f, 19f)
            horizontalLineTo(7f)
            verticalLineTo(17f)
            horizontalLineTo(5f)
            verticalLineToRelative(2f)
            close()
            moveTo(3f, 8f)
            verticalLineTo(4f)
            horizontalLineTo(21f)
            verticalLineTo(8f)
            horizontalLineTo(3f)
            close()
            moveTo(5f, 7f)
            horizontalLineTo(7f)
            verticalLineTo(5f)
            horizontalLineTo(5f)
            verticalLineTo(7f)
            close()
            moveTo(3f, 14f)
            verticalLineTo(10f)
            horizontalLineTo(21f)
            verticalLineToRelative(4f)
            horizontalLineTo(3f)
            close()
            moveTo(5f, 13f)
            horizontalLineTo(7f)
            verticalLineTo(11f)
            horizontalLineTo(5f)
            verticalLineToRelative(2f)
            close()
          }
        }
        .build()
    return _storage!!
  }

private var _storage: ImageVector? = null
