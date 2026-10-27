package com.uwu.animex.sync.mal

import com.uwu.animex.data.history.HISTORY_TODAY
import com.uwu.animex.data.history.HISTORY_YESTERDAY
import com.uwu.animex.data.history.historyGroup
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

internal fun malHistoryGroup(time: String): String {
    val t = time.trim()
    if (t.startsWith("Today", ignoreCase = true)) return HISTORY_TODAY
    if (t.startsWith("Yesterday", ignoreCase = true)) return HISTORY_YESTERDAY
    val year = Calendar.getInstance().get(Calendar.YEAR)
    val patterns = listOf("MMM d, yyyy h:mm a" to t, "MMM d, yyyy, h:mm a" to t, "yyyy MMM d, h:mm a" to "$year $t")
    for ((pattern, text) in patterns) {
        val parsed = runCatching {
            SimpleDateFormat(
                pattern,
                Locale.US,
            ).apply { isLenient = false }.parse(text)
        }.getOrNull()
        if (parsed != null) return historyGroup(parsed.time)
    }

    return HISTORY_TODAY
}

private val ID_MONTHS =
    mapOf(
        "jan" to "Jan",
        "feb" to "Feb",
        "mar" to "Mar",
        "apr" to "Apr",
        "may" to "Mei",
        "jun" to "Jun",
        "jul" to "Jul",
        "aug" to "Agu",
        "sep" to "Sep",
        "oct" to "Okt",
        "nov" to "Nov",
        "dec" to "Des",
    )

private val ID_AGO = Regex("""^(\d+)\s+(second|minute|hour|day|week|month|year)s?\s+ago""", RegexOption.IGNORE_CASE)

private val CLOCK_12H = Regex("""(\d{1,2}):(\d{2})\s*([AaPp])[Mm]""")

private val DATE_PART = Regex("""^([A-Za-z]{3})[a-z]*\.?\s+(\d{1,2})(?:,?\s+(\d{4}))?,?\s*(.*)$""")

internal fun malHistoryTimeId(time: String): String {
    val t = time.trim()
    ID_AGO.find(t)?.let { m ->
        val unit =
            when (m.groupValues[2].lowercase()) {
                "second" -> "detik"
                "minute" -> "menit"
                "hour" -> "jam"
                "day" -> "hari"
                "week" -> "minggu"
                "month" -> "bulan"
                else -> "tahun"
            }
        return "${m.groupValues[1]} $unit yang lalu"
    }

    fun clock(s: String) =
        CLOCK_12H.replace(s) { m ->
            val pm = m.groupValues[3].equals("p", ignoreCase = true)
            val h = m.groupValues[1].toInt() % 12 + if (pm) 12 else 0
            "%02d:%s".format(h, m.groupValues[2])
        }
    return when {
        t.startsWith("Today", ignoreCase = true) -> "Hari ini" + clock(t.substring(5))
        t.startsWith("Yesterday", ignoreCase = true) -> "Kemarin" + clock(t.substring(9))
        else ->
            DATE_PART.find(t)?.let { m ->
                val mon = ID_MONTHS[m.groupValues[1].lowercase()] ?: return t
                val year = m.groupValues[3].let { if (it.isEmpty()) "" else " $it" }
                val rest = clock(m.groupValues[4]).let { if (it.isBlank()) "" else ", $it" }
                "${m.groupValues[2]} $mon$year$rest"
            } ?: clock(t)
    }
}
