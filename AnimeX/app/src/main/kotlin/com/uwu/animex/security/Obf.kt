package com.uwu.animex.security

import android.util.Base64

/**
 * Lightweight string obfuscation.
 *
 * This does NOT make a string "unrecoverable" — anything that ships
 * on-device can eventually be dumped from memory at runtime by a
 * determined attacker (Frida, memory dump, etc). What it DOES do is
 * remove plaintext strings (endpoints, keys) from the dex's string
 * pool, so a quick `strings`/jadx pass on the raw APK no longer hands
 * over the values for free. Combined with the R8 rules that no longer
 * blanket-keep the `data` package, static analysis gets meaningfully
 * more expensive.
 *
 * Usage: obfuscate a literal once via [encode] (e.g. from a scratch
 * script or the companion `main` below), then hardcode the returned
 * cipher text and call [Obf.d] at the call site instead of the raw
 * string literal.
 */
object Obf {
    // Rotate this per-build if you want; it only needs to differ from
    // an all-zero/obvious key. It is itself embedded, so this is
    // obfuscation, not encryption — see class doc.
    private val KEY = byteArrayOf(0x4d, 0x69, 0x6b, 0x75, 0x21, 0x41, 0x58, 0x39)

    /** Decode a string previously produced by [encode]. */
    fun d(cipherB64: String): String {
        val bytes = Base64.decode(cipherB64, Base64.NO_WRAP)
        val out = ByteArray(bytes.size)
        for (i in bytes.indices) out[i] = (bytes[i].toInt() xor KEY[i % KEY.size].toInt()).toByte()
        return String(out, Charsets.UTF_8)
    }

    /** Dev-time helper to produce the cipher text for a literal. */
    fun encode(plain: String): String {
        val bytes = plain.toByteArray(Charsets.UTF_8)
        val out = ByteArray(bytes.size)
        for (i in bytes.indices) out[i] = (bytes[i].toInt() xor KEY[i % KEY.size].toInt()).toByte()
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }
}
