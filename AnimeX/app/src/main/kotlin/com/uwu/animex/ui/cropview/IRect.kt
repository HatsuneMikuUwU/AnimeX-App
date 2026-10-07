package com.image.cropview

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

public data class IRect(
    val topLeft: Offset = Offset(0.0f, 0.0f),
    var size: Size = Size(0.0f, 0.0f),
)

public fun IRect.verticalGuidelineDiff(noOfGuideLines: Int): Float = size.height / (noOfGuideLines + 1)

public fun IRect.horizontalGuidelineDiff(noOfGuideLines: Int): Float = size.width / (noOfGuideLines + 1)
