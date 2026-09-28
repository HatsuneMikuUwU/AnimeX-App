package com.uwu.animex.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Logo MyAnimeList monokrom: kotak membulat dengan huruf "MAL" berlubang,
 * sehingga warnanya ikut tint (aktif/non-aktif) di navigation bar.
 */
val MalLogoIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "MalLogo",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
        // kotak membulat
        moveTo(4f, 5f)
        horizontalLineTo(20f)
        quadTo(23f, 5f, 23f, 8f)
        verticalLineTo(16f)
        quadTo(23f, 19f, 20f, 19f)
        horizontalLineTo(4f)
        quadTo(1f, 19f, 1f, 16f)
        verticalLineTo(8f)
        quadTo(1f, 5f, 4f, 5f)
        close()

        // M
        moveTo(3.2f, 15.5f)
        lineTo(3.2f, 8.5f)
        lineTo(4.9f, 8.5f)
        lineTo(5.9f, 12f)
        lineTo(6.9f, 8.5f)
        lineTo(8.6f, 8.5f)
        lineTo(8.6f, 15.5f)
        lineTo(7.3f, 15.5f)
        lineTo(7.3f, 10.8f)
        lineTo(6.4f, 14f)
        lineTo(5.4f, 14f)
        lineTo(4.5f, 10.8f)
        lineTo(4.5f, 15.5f)
        close()

        // A (luar)
        moveTo(9.6f, 15.5f)
        lineTo(11.5f, 8.5f)
        lineTo(12.9f, 8.5f)
        lineTo(14.8f, 15.5f)
        lineTo(13.5f, 15.5f)
        lineTo(13.1f, 14f)
        lineTo(11.3f, 14f)
        lineTo(10.9f, 15.5f)
        close()
        // A (lubang)
        moveTo(11.6f, 12.8f)
        lineTo(12.8f, 12.8f)
        lineTo(12.2f, 10.2f)
        close()

        // L
        moveTo(15.8f, 8.5f)
        lineTo(17.1f, 8.5f)
        lineTo(17.1f, 14.2f)
        lineTo(20.4f, 14.2f)
        lineTo(20.4f, 15.5f)
        lineTo(15.8f, 15.5f)
        close()
    }.build()
}
