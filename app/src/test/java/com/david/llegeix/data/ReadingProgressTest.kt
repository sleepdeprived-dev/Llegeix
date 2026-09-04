package com.david.llegeix.data

import com.david.llegeix.data.db.dao.ReadingProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic behind every progress bar in the app.
 *
 * Worth testing on its own because it is drawn in six places and read at a
 * glance in all of them: a bar that says a book is finished when it is not is
 * worse than no bar, since nothing about it invites checking.
 */
class ReadingProgressTest {

    private fun progress(page: Int, count: Int?) = ReadingProgress(
        uriString = "content://test/1",
        lastPageIndex = page,
        pageCount = count,
        viewedAt = 0,
    )

    @Test
    fun `a document never opened has no fraction to report`() {
        assertNull(progress(page = 4, count = null).fraction)
    }

    @Test
    fun `a document of no pages has no fraction either`() {
        assertNull(progress(page = 0, count = 0).fraction)
    }

    @Test
    fun `the fraction counts the page you are on, not the ones behind you`() {
        // Page one of ten is a tenth, not nothing: opening a book has to show
        // something, or the bar looks broken on every book just started.
        assertEquals(0.1f, progress(page = 0, count = 10).fraction!!, 0.001f)
        assertEquals(1f, progress(page = 9, count = 10).fraction!!, 0.001f)
    }

    @Test
    fun `a resume position past the end still reports a sane fraction`() {
        // A file can be replaced on disk by a shorter one under the same name.
        assertEquals(1f, progress(page = 400, count = 10).fraction!!, 0.001f)
    }

    @Test
    fun `a book is finished before its very last page`() {
        // Books end in notes and an index; demanding the last page would leave
        // everything actually finished sitting in "continue reading" for ever.
        assertTrue(progress(page = 197, count = 200).isFinished)
        assertFalse(progress(page = 150, count = 200).isFinished)
    }

    @Test
    fun `only a started and unfinished book is worth resuming`() {
        assertFalse("never opened past page one", progress(page = 0, count = 200).isInProgress)
        assertTrue("halfway through", progress(page = 100, count = 200).isInProgress)
        assertFalse("already finished", progress(page = 199, count = 200).isInProgress)
    }

    @Test
    fun `a started document of unknown length still counts as in progress`() {
        // The page count arrives when the document is opened, and the history
        // can be older than that; a book on page 40 is being read either way.
        assertTrue(progress(page = 40, count = null).isInProgress)
    }
}
