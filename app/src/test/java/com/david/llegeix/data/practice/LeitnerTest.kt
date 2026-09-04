package com.david.llegeix.data.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class LeitnerTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `a word answered correctly moves up a box`() {
        assertEquals(1, Leitner.answer(box = 0, correct = true, now = now).box)
        assertEquals(3, Leitner.answer(box = 2, correct = true, now = now).box)
    }

    @Test
    fun `the last box does not overflow`() {
        val last = Leitner.answer(box = Leitner.LAST_BOX, correct = true, now = now)
        assertEquals(Leitner.LAST_BOX, last.box)
    }

    @Test
    fun `each box waits longer than the one below it`() {
        val waits = (0..Leitner.LAST_BOX).map { box ->
            Leitner.answer(box = box, correct = true, now = now).dueAt - now
        }
        // Compared pairwise rather than against fixed numbers, so tuning the
        // intervals does not break the test that says they must grow.
        waits.zipWithNext { shorter, longer ->
            assertTrue("box waits grow: $shorter then $longer", longer >= shorter)
        }
    }

    @Test
    fun `a word answered wrongly drops to the first box`() {
        assertEquals(0, Leitner.answer(box = 4, correct = false, now = now).box)
    }

    @Test
    fun `a word answered wrongly comes back within the same sitting`() {
        val wrong = Leitner.answer(box = 4, correct = false, now = now)
        val wait = wrong.dueAt - now
        assertTrue("comes back soon, not tomorrow", wait < TimeUnit.HOURS.toMillis(1))
        assertTrue("but not instantly", wait > TimeUnit.MINUTES.toMillis(1))
    }

    @Test
    fun `progress runs from nothing to full across the boxes`() {
        assertEquals(0f, Leitner.progressOf(0), 0.001f)
        assertEquals(1f, Leitner.progressOf(Leitner.LAST_BOX), 0.001f)
    }

    @Test
    fun `progress survives a box outside the range`() {
        assertEquals(0f, Leitner.progressOf(-3), 0.001f)
        assertEquals(1f, Leitner.progressOf(Leitner.LAST_BOX + 10), 0.001f)
    }
}
