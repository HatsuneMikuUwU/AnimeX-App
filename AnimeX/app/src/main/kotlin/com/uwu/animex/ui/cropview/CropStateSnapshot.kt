package com.image.cropview

import java.io.Serializable

public data class CropStateSnapshot(
    val topLeftXRatio: Float,
    val topLeftYRatio: Float,
    val widthRatio: Float,
    val heightRatio: Float,
    val zoomScale: Float,
    val zoomOffsetXRatio: Float,
    val zoomOffsetYRatio: Float,
    val bitmapWidth: Int,
    val bitmapHeight: Int,
) : Serializable
