package com.david.llegeix.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/** The Mac's sizes and dates, in the words the phone uses. */
class MacFormattersTest {

    @Test
    fun sizesInThousands() {
        assertEquals("", sizeText(0))
        assertEquals("950 B", sizeText(950))
        assertEquals("1,3 kB", sizeText(1_306))
        assertEquals("48 MB", sizeText(48_200_000))
    }

    @Test
    fun timesRelativeForAWeekThenDates() {
        val zone = ZoneOffset.UTC
        fun at(text: String) = LocalDateTime.parse(text).toInstant(zone).toEpochMilli()
        val now = at("2026-10-03T12:00")
        assertEquals("Fa 0 min", modifiedText(at("2026-10-03T11:59:30"), now, zone))
        assertEquals("Fa 25 min", modifiedText(at("2026-10-03T11:35"), now, zone))
        assertEquals("Fa 3 h", modifiedText(at("2026-10-03T09:00"), now, zone))
        assertEquals("Ahir", modifiedText(at("2026-10-02T08:00"), now, zone))
        assertEquals("Fa 4 dies", modifiedText(at("2026-09-29T08:00"), now, zone))
        assertEquals("4 de setembre", modifiedText(at("2026-09-04T08:00"), now, zone))
        assertEquals("4 de setembre del 2025", modifiedText(at("2025-09-04T08:00"), now, zone))
    }
}
