package com.david.llegeix.data.flashcards

import com.david.llegeix.data.flashcards.NextDue.Unit.DAYS
import com.david.llegeix.data.flashcards.NextDue.Unit.HOURS
import com.david.llegeix.data.flashcards.NextDue.Unit.MINUTES
import com.david.llegeix.data.flashcards.NextDue.Wait
import org.junit.Assert.assertEquals
import org.junit.Test

class NextDueTest {

    private val now = 1_700_000_000_000L
    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    private fun wait(after: Long) = NextDue.waitUntil(now + after, now)

    @Test
    fun `a wrong answer's ten minutes read as minutes`() {
        assertEquals(Wait(MINUTES, 10), wait(10 * minute))
    }

    @Test
    fun `under a minute still reads as one minute, never zero`() {
        assertEquals(Wait(MINUTES, 1), wait(20_000))
        assertEquals(Wait(MINUTES, 1), wait(0))
        assertEquals(Wait(MINUTES, 1), wait(-5 * minute))
    }

    @Test
    fun `minutes round up`() {
        assertEquals(Wait(MINUTES, 3), wait(2 * minute + 1))
    }

    @Test
    fun `an hour or more reads as hours`() {
        assertEquals(Wait(HOURS, 1), wait(hour))
        assertEquals(Wait(HOURS, 3), wait(3 * hour + 10 * minute))
        assertEquals(Wait(HOURS, 23), wait(23 * hour))
    }

    @Test
    fun `up to two days is still counted in hours`() {
        assertEquals(Wait(HOURS, 36), wait(36 * hour))
    }

    @Test
    fun `two days or more reads as days`() {
        assertEquals(Wait(DAYS, 2), wait(2 * day))
        assertEquals(Wait(DAYS, 3), wait(3 * day))
        assertEquals(Wait(DAYS, 35), wait(35 * day - hour))
    }
}
