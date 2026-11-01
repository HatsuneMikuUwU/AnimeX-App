package com.uwu.animex.sync

import java.security.MessageDigest

/** PKCE (RFC 7636) helpers. Pure Kotlin so they can be unit-tested without Android. */
object Pkce {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    /** code_challenge for method S256: BASE64URL(SHA256(ASCII(verifier))) without padding. */
    fun s256Challenge(verifier: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return base64UrlNoPad(hash)
    }

    internal fun base64UrlNoPad(bytes: ByteArray): String {
        val sb = StringBuilder((bytes.size * 4 + 2) / 3)
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xff
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xff else -1
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xff else -1
            sb.append(ALPHABET[b0 shr 2])
            sb.append(ALPHABET[((b0 and 0x03) shl 4) or (if (b1 >= 0) b1 shr 4 else 0)])
            if (b1 >= 0) sb.append(ALPHABET[((b1 and 0x0f) shl 2) or (if (b2 >= 0) b2 shr 6 else 0)])
            if (b2 >= 0) sb.append(ALPHABET[b2 and 0x3f])
            i += 3
        }
        return sb.toString()
    }
}
