package com.uwu.animex.data.nekopoi.extractor

/**
 * Port dari `JsUnpacker` CloudStream (https://github.com/cylonu87/JsUnpacker): membuka JS "P.A.C.K.E.R."
 * (`eval(function(p,a,c,k,e,d){...}('payload',radix,count,'a|b|c'.split('|')...`).
 */
internal object JsUnpacker {
    private val packedBlock = Regex("""eval\(function\(p,a,c,k,e,.*\)\)""")
    private val args = Regex("""(?s)\}\s*\('(.*)',\s*(.*?),\s*(\d+),\s*'(.*?)'\.split\('\|'\)""")
    private val word = Regex("""\b[a-zA-Z0-9_]+\b""")

    private const val ALPHABET_62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val ALPHABET_95 =
        " !\"#$%&\\'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\\\]^_`abcdefghijklmnopqrstuvwxyz{|}~"

    /** Semua blok packed di [text] yang berhasil dibuka (kosong bila tidak ada). */
    fun unpackAll(text: String): List<String> {
        val blocks = packedBlock.findAll(text).map { it.value }.toList()
        val candidates = if (blocks.isEmpty() && "p,a,c,k,e," in text) listOf(text) else blocks
        return candidates.mapNotNull { runCatching { unpack(it) }.getOrNull() }
    }

    /** Teks asli + hasil buka semua blok packed, dipisah baris; tempat mencari `file:`/`sources:`. */
    fun expand(text: String): String {
        val unpacked = unpackAll(text)
        return if (unpacked.isEmpty()) text else text + "\n" + unpacked.joinToString("\n")
    }

    fun unpack(js: String): String? {
        val m = args.find(js) ?: return null
        val payload = m.groupValues[1].replace("\\'", "'")
        val radix = m.groupValues[2].toIntOrNull() ?: 36
        val count = m.groupValues[3].toIntOrNull() ?: 0
        val symtab = m.groupValues[4].split("|")
        if (symtab.size != count) return null

        val alphabet =
            when {
                radix <= 36 -> null
                radix < 62 -> ALPHABET_62.substring(0, radix)
                radix == 62 -> ALPHABET_62
                radix in 63..94 -> ALPHABET_95.substring(0, radix)
                radix == 95 -> ALPHABET_95
                else -> return null
            }
        val dictionary = alphabet?.withIndex()?.associate { it.value to it.index }

        fun unbase(s: String): Int? {
            if (dictionary == null) return s.toIntOrNull(radix)
            var ret = 0
            var mul = 1
            for (ch in s.reversed()) {
                ret += mul * (dictionary[ch] ?: return null)
                mul *= radix
            }
            return ret
        }

        val out = StringBuilder()
        var last = 0
        for (w in word.findAll(payload)) {
            out.append(payload, last, w.range.first)
            val idx = unbase(w.value)
            val replacement = idx?.let { symtab.getOrNull(it) }?.takeIf { it.isNotEmpty() }
            out.append(replacement ?: w.value)
            last = w.range.last + 1
        }
        out.append(payload, last, payload.length)
        return out.toString()
    }
}
