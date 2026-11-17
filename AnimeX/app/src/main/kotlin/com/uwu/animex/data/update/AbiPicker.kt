package com.uwu.animex.data.update

internal fun pickPreferredAbi(supported: Array<String>): String =
    supported.firstNotNullOfOrNull { abi ->
        when {
            abi.startsWith("arm64") -> "arm64-v8a"
            abi.startsWith("armeabi") -> "armeabi-v7a"
            abi == "x86_64" -> "x86_64"
            else -> null
        }
    } ?: "universal"
