package com.uwu.animex.data.history

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ProfileHistoryItem(
    val key: String,
    val mediaId: Int?,
    val title: String,
    val action: String,
    val time: String,
    val group: String,
    val url: String? = null,
)

data class ProfileHistoryState(
    val items: List<ProfileHistoryItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false,
)

const val HISTORY_TODAY = "Hari ini"
const val HISTORY_YESTERDAY = "Kemarin"
const val HISTORY_UNKNOWN = "Lainnya"

fun historyGroup(
    millis: Long,
    now: Long = System.currentTimeMillis(),
): String {
    val days = dayDiff(millis, now)
    return when {
        days <= 0 -> HISTORY_TODAY
        days == 1 -> HISTORY_YESTERDAY
        days < 6 -> SimpleDateFormat(
            "EEEE",
            Locale.getDefault(),
        ).format(Date(millis)).replaceFirstChar { it.uppercase() }
        days < 14 -> "Minggu lalu"
        days < 21 -> "2 minggu lalu"
        days < 28 -> "3 minggu lalu"
        days < 35 -> "Bulan lalu"
        days < 60 -> "2 bulan lalu"
        else -> "Lebih lama"
    }
}

private fun dayDiff(
    millis: Long,
    now: Long,
): Int {
    fun startOfDay(t: Long): Long =
        Calendar
            .getInstance()
            .apply {
                timeInMillis = t
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
    return ((startOfDay(now) - startOfDay(millis) + 12L * 3_600_000L) / 86_400_000L).toInt()
}

fun malHistoryGroup(time: String): String {
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

fun malHistoryTimeId(time: String): String {
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

private val ID_MONTH_ABBR = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")

fun historyTimeId(
    millis: Long,
    now: Long = System.currentTimeMillis(),
): String {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    val clock = "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    return when (val days = dayDiff(millis, now)) {
        in Int.MIN_VALUE..0 -> "Hari ini, $clock"
        1 -> "Kemarin, $clock"
        else -> {
            val year = cal.get(Calendar.YEAR)
            val sameYear = year == Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.YEAR)
            val date = "${cal.get(Calendar.DAY_OF_MONTH)} ${ID_MONTH_ABBR[cal.get(Calendar.MONTH)]}"
            "${if (sameYear || days < 0) date else "$date $year"}, $clock"
        }
    }
}
