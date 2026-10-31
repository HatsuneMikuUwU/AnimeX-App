package com.uwu.animex.data.nekopoi

/** Minimal P.A.C.K.E.R. (`eval(function(p,a,c,k,e,d)...`) unpacker, radix up to 62. */
internal object PackerUnpacker {
    private const val ALPHABET_62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private val args =
        Regex("""(?s)\}\s*\('(.*)',\s*(\d+),\s*(\d+),\s*'(.*?)'\.split\('\|'\)""")
    private val word = Regex("""\b[a-zA-Z0-9_]+\b""")

    /** Returns the unpacked script, or [source] unchanged when it is not packed. */
    fun unpack(source: String): String {
        val m = args.find(source) ?: return source
        val payload = m.groupValues[1].replace("\\'", "'")
        val radix = m.groupValues[2].toIntOrNull() ?: return source
        val symbols = m.groupValues[4].split("|")
        if (radix !in 2..62) return source
        return word.replace(payload) { w ->
            val idx = unbase(w.value, radix)
            symbols.getOrNull(idx ?: -1)?.takeIf { it.isNotEmpty() } ?: w.value
        }
    }

    private fun unbase(
        s: String,
        radix: Int,
    ): Int? {
        if (radix <= 36) return s.toIntOrNull(radix)
        var out = 0
        for (c in s) {
            val d = ALPHABET_62.indexOf(c)
            if (d < 0 || d >= radix) return null
            out = out * radix + d
        }
        return out
    }
}
