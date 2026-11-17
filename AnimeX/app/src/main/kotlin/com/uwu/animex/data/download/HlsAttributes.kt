package com.uwu.animex.data.download

internal fun hlsAttribute(
    line: String,
    name: String,
): String? =
    Regex("(?:^|[,:])" + Regex.escape(name) + "=(\"[^\"]*\"|[^,]*)")
        .find(line)
        ?.groupValues
        ?.get(1)
        ?.trim('"')
