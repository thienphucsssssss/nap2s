package vn.twosmc.nap2s.platform.time

import java.text.SimpleDateFormat
import java.time.YearMonth
import java.time.ZoneId

/**
 * Time utilities
 */

private val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")

/**
 * Chuyển epoch millis thành chuỗi ngày giờ: "dd/MM/yyyy HH:mm:ss".
 */
fun Long.timeToString(): String = dateTimeFormat.format(this)

/**
 * Parse chuỗi "MM/yyyy" thành cặp (minEpoch, maxEpoch) — khoảng đầu tháng đến cuối tháng.
 *
 * @param yearMonth Chuỗi dạng "MM/yyyy" (VD: "01/2024", "12/2025")
 * @return Pair(startOfMonth epoch millis, endOfMonth epoch millis)
 */
fun minMaxEpochTimestamp(yearMonth: String): Pair<Long, Long> {
    val parts = yearMonth.split("/")
    val month = parts[0].toInt()
    val year = parts[1].toInt()

    val ym = YearMonth.of(year, month)
    val zone = ZoneId.systemDefault()

    val min = ym.atDay(1)
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()

    val max = ym.plusMonths(1)
        .atDay(1)
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli() - 1

    return Pair(min, max)
}
