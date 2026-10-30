package com.uwu.animex.data.nekopoi.extractor

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Port dari `JwPlayerHelper` CloudStream: ambil tautan stream dari skrip JWPlayer
 * (`sources: [{file: "..."}]`, `file: "..."`, dan kamus `links = {hls2: "...", hls4: "/stream/..."}`).
 */
internal object JwPlayer {
    private val sourcesBlock = Regex("""(?s)"?sources"?\s*:\s*(\[.*?])""")
    private val fileEntry = Regex("""(?i)["']?file["']?\s*:\s*["']([^"']+)["']""")
    private val hlsKey = Regex("""["']?(hls\d*)["']?\s*:\s*["']([^"']+)["']""")
    private val hlsPick = Regex("""(?i)file["']?\s*:\s*((?:links\.hls\d*\s*\|\|\s*)*links\.hls\d*)""")
    private val streamLink =
        Regex("""[:=]\s*["']([^"'\s]+(?:\.m3u8|master\.txt|urlset)[^"'\s]*)""")
    // Menangkap URL multi-quality StreamWish/streamruby yang path-nya mengandung koma + urlset
    private val anyMedia =
        Regex("""(https?:(?:\\?/){2}[^\s"']+?\.(?:m3u8|mp4)[^\s"']*|https?:(?:\\?/){2}[^\s"']*?urlset[^\s"']*)""")

    /** URL stream dalam urutan prioritas, sudah dinormalisasi terhadap [base]. */
    fun links(
        script: String,
        base: String,
    ): List<String> {
        val found = LinkedHashSet<String>()

        sourcesBlock.findAll(script).forEach { block ->
            fileEntry.findAll(block.groupValues[1]).forEach { f -> found += f.groupValues[1] }
        }

        // `file: links.hls4 || links.hls3 || links.hls2` -> pakai urutan yang ditulis situs.
        val dict = hlsKey.findAll(script).associate { it.groupValues[1] to it.groupValues[2] }
        if (dict.isNotEmpty()) {
            val order =
                hlsPick
                    .find(script)
                    ?.groupValues
                    ?.get(1)
                    ?.split("||")
                    ?.map { it.trim().removePrefix("links.") }
                    .orEmpty()
            (order + dict.keys.sortedDescending()).forEach { key -> dict[key]?.let { found += it } }
        }

        if (found.isEmpty()) {
            streamLink.findAll(script).forEach { found += it.groupValues[1] }
        }
        if (found.isEmpty()) {
            fileEntry.findAll(script).forEach { f ->
                val v = f.groupValues[1]
                if (v.startsWith("http") || v.startsWith("/")) found += v
            }
        }
        if (found.isEmpty()) {
            anyMedia.findAll(script).forEach { found += it.groupValues[1] }
        }

        return found
            .map { it.replace("\\/", "/").replace("&amp;", "&") }
            .filter {
                it.isNotBlank() &&
                    !it.endsWith(".vtt") &&
                    !it.endsWith(".srt") &&
                    !it.endsWith(".jpg") &&
                    !it.endsWith(".png") &&
                    !it.contains("thumb", ignoreCase = true)
            }
            .mapNotNull { absolute(it, base) }
            .distinct()
    }

    fun isHls(url: String): Boolean {
        val l = url.lowercase()
        return ".m3u8" in l || "master.txt" in l || "urlset" in l || ("/hls" in l && ".mp4" !in l)
    }

    fun absolute(
        raw: String,
        base: String,
    ): String? =
        when {
            raw.startsWith("//") -> "https:$raw"
            raw.startsWith("http") -> raw
            else -> base.toHttpUrlOrNull()?.resolve(raw)?.toString()
        }
}
