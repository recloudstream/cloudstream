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
public val tune: ImageVector
  get() {
    if (_tune != null) {
      return _tune!!
    }
    _tune =
      ImageVector.Builder(
          name = "tune",
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
            moveTo(11f, 21f)
            verticalLineTo(15f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(8f)
            verticalLineToRelative(2f)
            horizontalLineTo(13f)
            verticalLineToRelative(2f)
            horizontalLineTo(11f)
            close()
            moveTo(3f, 19f)
            verticalLineTo(17f)
            horizontalLineTo(9f)
            verticalLineToRelative(2f)
            horizontalLineTo(3f)
            close()
            moveTo(7f, 15f)
            verticalLineTo(13f)
            horizontalLineTo(3f)
            verticalLineTo(11f)
            horizontalLineTo(7f)
            verticalLineTo(9f)
            horizontalLineTo(9f)
            verticalLineToRelative(6f)
            horizontalLineTo(7f)
            close()
            moveToRelative(4f, -2f)
            verticalLineTo(11f)
            horizontalLineTo(21f)
            verticalLineToRelative(2f)
            horizontalLineTo(11f)
            close()
            moveTo(15f, 9f)
            verticalLineTo(3f)
            horizontalLineToRelative(2f)
            verticalLineTo(5f)
            horizontalLineToRelative(4f)
            verticalLineTo(7f)
            horizontalLineTo(17f)
            verticalLineTo(9f)
            horizontalLineTo(15f)
            close()
            moveTo(3f, 7f)
            verticalLineTo(5f)
            horizontalLineTo(13f)
            verticalLineTo(7f)
            horizontalLineTo(3f)
            close()
          }
        }
        .build()
    return _tune!!
  }

private var _tune: ImageVector? = null
