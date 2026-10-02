package com.david.llegeix.util

import androidx.compose.runtime.Composable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/*
 * The Mac's versions of what Android's Formatter and DateUtils say on the
 * phone, in the same words: sizes in powers of a thousand, as Android counts
 * them, and times relative to now for the last week, a date after that.
 */

private val catalan = Locale.forLanguageTag("ca")

@Composable
actual fun formatSize(bytes: Long): String = sizeText(bytes)

internal fun sizeText(bytes: Long): String {
    if (bytes <= 0L) return ""
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1000 && unit < SIZE_UNITS.lastIndex) {
        value /= 1000
        unit++
    }
    val number = if (unit == 0 || value >= 10) "%.0f" else "%.1f"
    return "${number.format(catalan, value)} ${SIZE_UNITS[unit]}"
}

private val SIZE_UNITS = listOf("B", "kB", "MB", "GB", "TB")

actual fun formatModified(millis: Long): String = modifiedText(millis, System.currentTimeMillis())

internal fun modifiedText(millis: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    if (millis <= 0L) return ""
    val minutes = (now - millis).coerceAtLeast(0) / 60_000
    val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(day, today)
    return when {
        minutes < 60 -> "Fa $minutes min"
        minutes < 24 * 60 -> "Fa ${minutes / 60} h"
        days <= 1 -> "Ahir"
        days < 7 -> "Fa $days dies"
        day.year == today.year -> day.format(DAY_AND_MONTH)
        else -> day.format(FULL_DATE)
    }
}

/** "4 de setembre": the month's own form takes the "de", as Catalan does. */
private val DAY_AND_MONTH = DateTimeFormatter.ofPattern("d MMMM", catalan)
private val FULL_DATE = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(catalan)

