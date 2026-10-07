package com.image.cropview

public enum class CropType {
    FREE_STYLE,
    SQUARE,
    PROFILE_CIRCLE,
    RATIO_3_2,
    RATIO_4_3,
    RATIO_16_9,
    RATIO_9_16,
    ;

    public fun aspectRatio(): Float? =
        when (this) {
            RATIO_3_2 -> 3f / 2f
            RATIO_4_3 -> 4f / 3f
            RATIO_16_9 -> 16f / 9f
            RATIO_9_16 -> 9f / 16f
            else -> null
        }
}
