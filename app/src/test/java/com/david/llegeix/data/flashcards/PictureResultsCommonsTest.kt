package com.david.llegeix.data.flashcards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading Wikimedia Commons' answer.
 *
 * Commons is the larger half of the photo search and the more awkward to read:
 * its credits are fragments of HTML meant for a web page, and its originals are
 * routinely twenty megapixels, so both the markup and the size have to be dealt
 * with before a card ever sees them.
 */
class PictureResultsCommonsTest {

    private val answer = """
        {"query":{"pages":[
          {"title":"File:Pa de pagès.jpg",
           "categories":[{"title":"Category:Breads of Catalonia"}],
           "imageinfo":[{
             "thumburl":"https://upload.wikimedia.org/w/thumb/a/ab/Pa.jpg/330px-Pa.jpg",
             "url":"https://upload.wikimedia.org/w/a/ab/Pa.jpg",
             "extmetadata":{
               "Artist":{"value":"<a href=\"/wiki/User:Someone\">Someone &amp; Co</a>"},
               "LicenseShortName":{"value":"CC BY-SA 4.0"}}}]}
        ]}}
    """.trimIndent()

    @Test
    fun `a file becomes a picture with its credit`() {
        val hits = PictureResults.parseCommons(answer)
        assertEquals(1, hits.size)
        val hit = hits.first()
        assertEquals(PictureSource.PHOTOS, hit.source)
        assertEquals("Someone & Co · Wikimedia Commons · CC BY-SA 4.0", hit.credit)
    }

    /**
     * The grid gets the small copy and the card gets a larger one, both made by
     * Commons — never the original, which can be tens of megabytes.
     */
    @Test
    fun `the full size is another thumbnail, not the original`() {
        val hit = PictureResults.parseCommons(answer).first()
        assertTrue("the grid uses the small copy", hit.thumbnailUrl.contains("330px-"))
        assertTrue("and the card a bigger one", hit.fullUrl.contains("960px-"))
        assertTrue("neither is the original", !hit.fullUrl.endsWith("/Pa.jpg"))
    }

    @Test
    fun `markup and entities come out of a credit`() {
        assertEquals("Evan-Amos", PictureResults.stripMarkup("<a href=\"x\">Evan-Amos</a>"))
        assertEquals("A & B", PictureResults.stripMarkup("A &amp; B"))
        assertEquals("", PictureResults.stripMarkup(null))
    }

    @Test
    fun `what is unknown is left out of a credit rather than written as a blank`() {
        assertEquals("Wikimedia Commons", PictureResults.commonsCredit("", ""))
        assertEquals("Wikimedia Commons · CC0", PictureResults.commonsCredit("", "CC0"))
    }

    @Test
    fun `an answer that is not an answer is no pictures rather than a crash`() {
        assertTrue(PictureResults.parseCommons("not json").isEmpty())
        assertTrue(PictureResults.parseCommons("""{"query":{}}""").isEmpty())
    }

    /** A Commons file is read against the same blocklist an Openverse photo is. */
    @Test
    fun `a blocked category keeps a file out`() {
        val blocked = answer.replace(
            """{"title":"Category:Breads of Catalonia"}""",
            """{"title":"Category:Nude women"}""",
        )
        assertTrue(PictureResults.parseCommons(blocked).isEmpty())
    }
}
