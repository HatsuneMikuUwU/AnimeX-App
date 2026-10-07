package com.image.cropview

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.core.graphics.scale
import kotlin.math.abs

public fun DrawScope.drawBitmap(
    bitmap: Bitmap,
    canvasSize: CanvasSize,
) {
    val targetWidth = canvasSize.width.toInt()
    val targetHeight = canvasSize.height.toInt()

    if (bitmap.width == targetWidth && bitmap.height == targetHeight) {
        drawImage(bitmap.asImageBitmap())
        return
    }

    if (canvasSize.width <= 0 || canvasSize.height <= 0) {
        return
    }

    val scaleX = targetWidth.toFloat() / bitmap.width
    val scaleY = targetHeight.toFloat() / bitmap.height

    val matrix =
        Matrix().apply {
            postScale(scaleX, scaleY)
        }

    val scaledBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

    try {
        drawImage(scaledBitmap.asImageBitmap())
    } catch (e: IllegalArgumentException) {
    } finally {
        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }
    }
}

public fun DrawScope.drawBitmap2(
    bitmap: Bitmap,
    canvasSize: CanvasSize,
) {
    val aspectRatio = bitmap.width.toFloat() / bitmap.height
    val targetWidth: Int
    val targetHeight: Int

    if (canvasSize.width / canvasSize.height > aspectRatio) {
        targetHeight = canvasSize.height.toInt()
        targetWidth = (targetHeight * aspectRatio).toInt()
    } else {
        targetWidth = canvasSize.width.toInt()
        targetHeight = (targetWidth / aspectRatio).toInt()
    }

    val scaledBitmap =
        if (bitmap.width != targetWidth || bitmap.height != targetHeight) {
            bitmap.scale(targetWidth, targetHeight, false)
        } else {
            bitmap
        }

    drawImage(scaledBitmap.asImageBitmap())
}

public fun DrawScope.drawCropRectangleView(
    guideLineColor: Color,
    guideLineWidth: Dp = 2.dp,
    iRect: IRect,
) {
    drawRect(
        color = guideLineColor,
        topLeft = iRect.topLeft,
        size = iRect.size,
        style = Stroke(guideLineWidth.toPx()),
    )
}

public fun DrawScope.drawGuideLines(
    noOfGuideLines: Int = 2,
    guideLineColor: Color,
    guideLineWidth: Dp = 2.dp,
    iRect: IRect,
) {
    val verticalGuidelineDiff = iRect.verticalGuidelineDiff(noOfGuideLines)
    val horizontalGuidelineDiff = iRect.horizontalGuidelineDiff(noOfGuideLines)
    val topLeft = iRect.topLeft

    for (i in 1..noOfGuideLines) {
        drawLine(
            color = guideLineColor,
            start = Offset(topLeft.x, topLeft.y + (verticalGuidelineDiff * i)),
            end = Offset(topLeft.x + iRect.size.width, topLeft.y + (verticalGuidelineDiff * i)),
            strokeWidth = guideLineWidth.toPx(),
        )

        drawLine(
            color = guideLineColor,
            start = Offset(topLeft.x + (horizontalGuidelineDiff * i), iRect.topLeft.y),
            end = Offset(topLeft.x + (horizontalGuidelineDiff * i), topLeft.y + iRect.size.height),
            strokeWidth = guideLineWidth.toPx(),
        )
    }
}

public fun DrawScope.drawCircularEdges(
    edgeCircleSize: Dp = 8.dp,
    guideLineColor: Color,
    iRect: IRect,
) {
    val topLeft = iRect.topLeft
    drawCircle(
        color = guideLineColor,
        center = topLeft,
        radius = edgeCircleSize.toPx(),
    )

    drawCircle(
        color = guideLineColor,
        center = Offset((topLeft.x + iRect.size.width), topLeft.y),
        radius = edgeCircleSize.toPx(),
    )

    drawCircle(
        color = guideLineColor,
        center = Offset(topLeft.x, (topLeft.y + iRect.size.height)),
        radius = edgeCircleSize.toPx(),
    )

    drawCircle(
        color = guideLineColor,
        center = Offset((topLeft.x + iRect.size.width), (topLeft.y + iRect.size.height)),
        radius = edgeCircleSize.toPx(),
    )
}

public fun DrawScope.drawSquareBrackets(
    guideLineColor: Color,
    guideLineWidthGiven: Dp = 2.dp,
    iRect: IRect,
) {
    val guideLineWidth = min(guideLineWidthGiven, 2.dp)
    val topLeft = iRect.topLeft
    val iRectWidth = (topLeft.x + iRect.size.width)
    val iRectHeight = (topLeft.y + iRect.size.height)
    val lineLength = 45F
    val strokeWidth = (guideLineWidth.toPx() * 1.3F)
    val halfStrokeWidth = (strokeWidth / 2F)
    val guideLineWidthInPx = guideLineWidth.toPx()

    val yTopLine = (topLeft.y - guideLineWidthInPx)
    val xLeftLine = (topLeft.x - guideLineWidthInPx)
    val xRightLine = (iRectWidth + guideLineWidthInPx)
    val yBottomLine = (iRectHeight + guideLineWidthInPx)
    drawLine(
        start = Offset((xLeftLine - (halfStrokeWidth)), yTopLine),
        end = Offset((xLeftLine + lineLength + halfStrokeWidth), yTopLine),
        color = guideLineColor,
        strokeWidth = strokeWidth,
    )
    drawLine(
        start = Offset(xLeftLine, yTopLine - (halfStrokeWidth)),
        end = Offset(xLeftLine, yTopLine + lineLength + halfStrokeWidth),
        color = guideLineColor,
        strokeWidth = strokeWidth,
    )

    val tpXStart = iRectWidth - abs(lineLength - strokeWidth)
    val tpXEnd = iRectWidth + (halfStrokeWidth + guideLineWidthInPx)
    drawLine(
        start = Offset(tpXStart, yTopLine),
        end = Offset(tpXEnd, yTopLine),
        color = guideLineColor,
        strokeWidth = strokeWidth,
    )
    drawLine(
        start = Offset(xRightLine, yTopLine - (halfStrokeWidth)),
        end = Offset(xRightLine, yTopLine + lineLength + halfStrokeWidth),
        color = guideLineColor,
        strokeWidth = strokeWidth,
    )

    drawLine(
        start = Offset((xLeftLine - (halfStrokeWidth)), yBottomLine),
        end = Offset((xLeftLine + lineLength + halfStrokeWidth), yBottomLine),
        color = guideLineColor,
        strokeWidth = strokeWidth,
    )
    drawLine(
        start = Offset(xLeftLine, iRectHeight - abs(lineLength - strokeWidth)),
        end = Offset(xLeftLine, (iRectHeight + guideLineWidthInPx + halfStrokeWidth)),
        color = guideLineColor,
        strokeWidth = strokeWidth,
    )

    drawLine(
        start = Offset(iRectWidth - abs(lineLength - strokeWidth), yBottomLine),
        end = Offset(iRectWidth + strokeWidth, yBottomLine),
        color = guideLineColor,
        strokeWidth = strokeWidth,
    )
    drawLine(
        start = Offset(xRightLine, iRectHeight - abs(lineLength - strokeWidth)),
        end = Offset(xRightLine, iRectHeight + halfStrokeWidth + guideLineWidthInPx),
        color = guideLineColor,
        strokeWidth = strokeWidth,
    )

    val xHorizontalCenter = (topLeft.x + (iRect.horizontalGuidelineDiff(1)))
    val halfLineLength = (lineLength / 2F) + strokeWidth
    drawLine(
        start = Offset(xHorizontalCenter - halfLineLength, yTopLine),
        end = Offset(xHorizontalCenter + halfLineLength, yTopLine),
        color = guideLineColor,
        strokeWidth = strokeWidth,
    )
    drawLine(
        start = Offset(xHorizontalCenter - halfLineLength, yBottomLine),
        end = Offset(xHorizontalCenter + halfLineLength, yBottomLine),
        color = guideLineColor,
        strokeWidth = strokeWidth,
    )

    val xVerticalCenter = (topLeft.y + (iRect.verticalGuidelineDiff(1)))
    drawLine(
        start = Offset(xLeftLine, xVerticalCenter - halfLineLength),
        end = Offset(xLeftLine, xVerticalCenter + halfLineLength),
        color = guideLineColor,
        strokeWidth = strokeWidth,
    )
    drawLine(
        start = Offset(xRightLine, xVerticalCenter - halfLineLength),
        end = Offset(xRightLine, xVerticalCenter + halfLineLength),
        color = guideLineColor,
        strokeWidth = strokeWidth,
    )
}
