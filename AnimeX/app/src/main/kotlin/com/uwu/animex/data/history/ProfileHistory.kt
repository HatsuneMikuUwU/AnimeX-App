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

/** Mengelompokkan waktu seperti riwayat di DailyAL: hari ini, kemarin, nama hari, lalu minggu/bulan lalu. */
fun historyGroup(
    millis: Long,
    now: Long = System.currentTimeMillis(),
): String {
    val days = dayDiff(millis, now)
    return when {
        days <= 0 -> HISTORY_TODAY
        days == 1 -> HISTORY_YESTERDAY
        days < 6 -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(millis)).replaceFirstChar { it.uppercase() }
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
        Calendar.getInstance().apply {
            timeInMillis = t
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    return ((startOfDay(now) - startOfDay(millis) + 12L * 3_600_000L) / 86_400_000L).toInt()
}

/**
 * Waktu di halaman history MAL: "Today, 4:12 AM", "Yesterday, 4:12 AM", "Oct 22, 4:12 AM"
 * atau "Oct 21, 2025 1:00 PM". Gagal parse (mis. "4 hours ago") -> [HISTORY_TODAY], seperti DailyAL.
 */
fun malHistoryGroup(time: String): String {
    val t = time.trim()
    if (t.startsWith("Today", ignoreCase = true)) return HISTORY_TODAY
    if (t.startsWith("Yesterday", ignoreCase = true)) return HISTORY_YESTERDAY
    val year = Calendar.getInstance().get(Calendar.YEAR)
    val patterns = listOf("MMM d, yyyy h:mm a" to t, "MMM d, yyyy, h:mm a" to t, "yyyy MMM d, h:mm a" to "$year $t")
    for ((pattern, text) in patterns) {
        val parsed = runCatching { SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(text) }.getOrNull()
        if (parsed != null) return historyGroup(parsed.time)
    }
    // Sama seperti DailyAL: waktu yang tidak bisa di-parse (mis. "4 hours ago") dianggap hari ini.
    return HISTORY_TODAY
}
