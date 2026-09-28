package com.uwu.animex.security

import android.util.Base64

object Obf {
    private val KEY = byteArrayOf(0x4d, 0x69, 0x6b, 0x75, 0x21, 0x41, 0x58, 0x39)

    fun d(cipherB64: String): String {
        val bytes = Base64.decode(cipherB64, Base64.NO_WRAP)
        val out = ByteArray(bytes.size)
        for (i in bytes.indices) out[i] = (bytes[i].toInt() xor KEY[i % KEY.size].toInt()).toByte()
        return String(out, Charsets.UTF_8)
    }

    fun encode(plain: String): String {
        val bytes = plain.toByteArray(Charsets.UTF_8)
        val out = ByteArray(bytes.size)
        for (i in bytes.indices) out[i] = (bytes[i].toInt() xor KEY[i % KEY.size].toInt()).toByte()
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }
}
