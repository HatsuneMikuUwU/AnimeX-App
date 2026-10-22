package com.uwu.animex.sync.providers

import androidx.core.text.HtmlCompat
import com.uwu.animex.data.history.ProfileHistoryItem
import com.uwu.animex.data.history.malHistoryGroup
import com.uwu.animex.data.history.malHistoryTimeId

internal object MalHistoryParser {
    private val OPTIONS = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    private val ROW = Regex("<tr[^>]*>(.*?)</tr>", OPTIONS)
    private val CELL = Regex("<td[^>]*borderClass[^>]*>(.*?)</td>", OPTIONS)
    private val LINK = Regex("<a\\s[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", OPTIONS)
    private val ID = Regex("""(?:[?&]id=|/anime/)(\d+)""")
    private val TAG = Regex("<[^>]+>")
    private val SPACES = Regex("\\s+")

    private const val MAX_ITEMS = 100

    fun parse(html: String): List<ProfileHistoryItem> {
        val start = html.indexOf("history_content_wrapper")
        val body = if (start >= 0) html.substring(start) else html
        val out = ArrayList<ProfileHistoryItem>()
        val seen = HashSet<String>()
        for (row in ROW.findAll(body)) {
            val raw = row.groupValues[1]
            if (raw.contains("normal_header", ignoreCase = true)) continue
            val cells = CELL.findAll(raw).map { it.groupValues[1] }.toList()
            if (cells.size < 2) continue
            val link = LINK.find(cells[0]) ?: continue
            val href = link.groupValues[1].replace("&amp;", "&")
            if (!href.contains("anime", ignoreCase = true)) continue
            val id = ID.find(href)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            val title = clean(link.groupValues[2]).ifBlank { continue }
            val action = clean(cells[0].replace(LINK, " "))
            val time = clean(cells[1])
            val key = "$id|$time|$action"
            if (!seen.add(key)) continue
            out.add(
                ProfileHistoryItem(
                    key = key,
                    mediaId = id,
                    title = title,
                    action = action.ifBlank { "Diperbarui" },
                    time = malHistoryTimeId(time),
                    group = malHistoryGroup(time),
                    url = "https://myanimelist.net/anime/$id",
                ),
            )
            if (out.size >= MAX_ITEMS) break
        }
        return out
    }

    private fun clean(fragment: String): String =
        HtmlCompat
            .fromHtml(fragment.replace(TAG, " "), HtmlCompat.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace('\u00A0', ' ')
            .replace(SPACES, " ")
            .trim()
}
