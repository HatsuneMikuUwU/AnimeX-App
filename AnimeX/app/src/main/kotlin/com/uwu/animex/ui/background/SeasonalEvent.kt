package com.uwu.animex.ui.background

import java.util.Calendar

private val MonthNames =
    listOf(
        "Januari",
        "Februari",
        "Maret",
        "April",
        "Mei",
        "Juni",
        "Juli",
        "Agustus",
        "September",
        "Oktober",
        "November",
        "Desember",
    )

enum class SeasonalEvent(
    val label: String,
    val background: BackgroundType,
    private val startMonth: Int,
    private val startDay: Int,
    val endMonth: Int,
    val endDay: Int,
) {
    HALLOWEEN("Halloween", BackgroundType.HALLOWEEN, 10, 24, 11, 1),
    NEW_YEAR("Tahun Baru", BackgroundType.FIREWORKS, 12, 31, 1, 1),
    WINTER_HOLIDAYS("Liburan Natal", BackgroundType.SNOW, 12, 20, 1, 7),
    ;

    val endLabel: String
        get() = "$endDay ${MonthNames[endMonth - 1]}"

    private fun isRunningOn(key: Int): Boolean {
        val start = startMonth * 100 + startDay
        val end = endMonth * 100 + endDay
        return if (start <= end) key in start..end else key >= start || key <= end
    }

    companion object {
        fun on(calendar: Calendar = Calendar.getInstance()): SeasonalEvent? {
            val key = (calendar.get(Calendar.MONTH) + 1) * 100 + calendar.get(Calendar.DAY_OF_MONTH)
            return entries.firstOrNull { it.isRunningOn(key) }
        }
    }
}
