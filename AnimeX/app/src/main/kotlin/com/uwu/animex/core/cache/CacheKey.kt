package com.uwu.animex.core.cache

internal fun canonicalCacheKey(
    path: String,
    params: Map<String, String>,
): String =
    buildString {
        append(path.length).append(':').append(path)
        params.toSortedMap().forEach { (key, value) ->
            append(key.length).append(':').append(key)
            append(value.length).append(':').append(value)
        }
    }
