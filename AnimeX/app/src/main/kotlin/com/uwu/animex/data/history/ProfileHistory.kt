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
