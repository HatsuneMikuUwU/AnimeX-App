package com.uwu.animex.core.security

object Secrets {
    val malClientId: String by lazy { read(NativeCore.SECRET_MAL) }
    val anilistClientId: String by lazy { read(NativeCore.SECRET_ANILIST) }
    val apiGateUrl: String by lazy { read(NativeCore.SECRET_GATE) }
    val apiBaseUrl: String by lazy { read(NativeCore.SECRET_BASE) }

    private fun read(id: Int): String =
        try {
            NativeCore.secret(id)
        } catch (_: UnsatisfiedLinkError) {
            ""
        } catch (_: ExceptionInInitializerError) {
            ""
        }
}
