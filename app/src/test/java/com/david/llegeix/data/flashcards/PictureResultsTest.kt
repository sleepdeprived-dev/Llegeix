package com.david.llegeix.data.flashcards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PictureResultsTest {

    @Test
    fun `a Catalan word is asked of ARASAAC in Catalan, safely encoded`() {
        assertEquals(
            "https://api.arasaac.org/v1/pictograms/ca/search/pa",
            PictureResults.arasaacSearchUrl(" pa "),
        )
        assertEquals(
            "https://api.arasaac.org/v1/pictograms/ca/search/cama%20de%20pa",
            PictureResults.arasaacSearchUrl("cama de pa"),
        )
        assertEquals(
            "https://api.arasaac.org/v1/pictograms/ca/search/caf%C3%A8",
            PictureResults.arasaacSearchUrl("cafè"),
        )
        assertTrue(
            "a slash cannot reach a different path",
            "/search/a%2Fb" in PictureResults.arasaacSearchUrl("a/b"),
        )
    }

    @Test
    fun `Openverse is asked for a page of safe results`() {
        // No category filter any more: it restricted the answer to what
        // Openverse files as a photograph, which for a vocabulary card ruled
        // out most of the illustrations that are the clearest pictures of all.
        val url = PictureResults.openverseSearchUrl("apple tree")
        assertEquals(
            "https://api.openverse.org/v1/images/?q=apple+tree&page_size=30&mature=false",
            url,
        )
        assertTrue(PictureResults.openverseSearchUrl("a&b=c").contains("q=a%26b%3Dc&"))
    }

    @Test
    fun `Commons is asked for bitmaps, with the categories that let them be checked`() {
        val url = PictureResults.commonsSearchUrl("apple tree")
        assertTrue("only real pictures", url.contains("filetype%3Abitmap+apple+tree"))
        assertTrue("files, not articles", url.contains("gsrnamespace=6"))
        assertTrue("with a thumbnail already made", url.contains("iiurlwidth=320"))
        assertTrue("and what it is filed under", url.contains("categories"))
    }

    @Test
    fun `ARASAAC pictograms become pictures, drawn at two sizes`() {
        val json = """[
            {"_id": 2494, "sex": false, "violence": false, "keywords": [{"keyword": "pa"}]},
            {"_id": 10230}
        ]"""
        val hits = PictureResults.parseArasaac(json)
        assertEquals(listOf("2494", "10230"), hits.map { it.id })
        val first = hits.first()
        assertEquals(PictureSource.PICTOGRAMS, first.source)
        assertEquals("https://static.arasaac.org/pictograms/2494/2494_300.png", first.thumbnailUrl)
        assertEquals("https://static.arasaac.org/pictograms/2494/2494_500.png", first.fullUrl)
        assertEquals(PictureResults.ARASAAC_CREDIT, first.credit)
    }

    @Test
    fun `pictograms marked as sexual or violent are never offered`() {
        val json = """[
            {"_id": 1, "sex": true},
            {"_id": 2, "violence": true},
            {"_id": 3}
        ]"""
        assertEquals(listOf("3"), PictureResults.parseArasaac(json).map { it.id })
    }

    @Test
    fun `a broken or odd answer is no pictures rather than a crash`() {
        assertEquals(emptyList<PictureHit>(), PictureResults.parseArasaac("not json"))
        assertEquals(emptyList<PictureHit>(), PictureResults.parseArasaac("""{"error":"x"}"""))
        assertEquals(emptyList<PictureHit>(), PictureResults.parseArasaac("""[{"_id": -1}, {}]"""))
        assertEquals(emptyList<PictureHit>(), PictureResults.parseOpenverse("<html>"))
        assertEquals(emptyList<PictureHit>(), PictureResults.parseOpenverse("""{"detail":"rate"}"""))
    }

    @Test
    fun `no more than a grid's worth is offered`() {
        val json = (1..60).joinToString(",", "[", "]") { """{"_id": $it}""" }
        assertEquals(PictureResults.LIMIT, PictureResults.parseArasaac(json).size)
    }

    @Test
    fun `Global Symbols is asked in Catalan`() {
        val url = PictureResults.globalSymbolsSearchUrl(" pa ")
        assertTrue("in Catalan, as ARASAAC is", url.contains("language=cat"))
        assertTrue(url.contains("query=pa"))
    }

    @Test
    fun `Global Symbols pictograms keep the set they came from`() {
        val json = """[
            {"text":"pa","picto":{"id":90177,"symbolset_id":17,
             "image_url":"https://globalsymbols.com/u/17_90177.png"}},
            {"text":"pa","picto":{"id":3363,"symbolset_id":13,
             "image_url":"https://globalsymbols.com/u/13_3363.svg"}},
            {"text":"pa","picto":{"id":9,"symbolset_id":99,
             "image_url":"http://insecure.example/x.png"}}
        ]"""
        val hits = PictureResults.parseGlobalSymbols(json, mapOf(17 to "ARASAAC · CC BY-NC-SA 4.0"))
        assertEquals("the SVG and the insecure one are left out", 1, hits.size)
        assertEquals(PictureSource.PICTOGRAMS, hits.single().source)
        assertEquals("ARASAAC · CC BY-NC-SA 4.0 · Global Symbols", hits.single().credit)
    }

    @Test
    fun `a Global Symbols pictogram is matted like any other pictogram`() {
        assertTrue(PictureResults.isPictogram("Mulberry Symbols · Global Symbols"))
        assertTrue(PictureResults.isPictogram(PictureResults.ARASAAC_CREDIT))
        assertTrue("a photograph is not", !PictureResults.isPictogram("Ana · Flickr · CC BY"))
        assertTrue(!PictureResults.isPictogram(null))
    }

    @Test
    fun `the symbol set index becomes credits, and a broken one becomes none`() {
        val json = """[
            {"id":13,"name":"Mulberry Symbols","licence":{"name":"Creative Commons BY SA 4.0"}},
            {"id":99,"name":"Nameless"}
        ]"""
        val sets = PictureResults.parseGlobalSymbolsSets(json)
        assertEquals("Mulberry Symbols · Creative Commons BY SA 4.0", sets[13])
        assertEquals("Nameless", sets[99])
        assertTrue(PictureResults.parseGlobalSymbolsSets("nope").isEmpty())
    }

    @Test
    fun `an answer that is not an answer is no pictograms rather than a crash`() {
        assertTrue(PictureResults.parseGlobalSymbols("<html>").isEmpty())
        assertTrue(PictureResults.parseGlobalSymbols("""[{"text":"x"}]""").isEmpty())
    }

    @Test
    fun `Openverse photos keep their maker and licence`() {
        val json = """{"result_count": 2, "results": [
            {"id": "ae9d", "url": "https://live.staticflickr.com/1/a_b.jpg",
             "thumbnail": "https://api.openverse.org/v1/images/ae9d/thumb/",
             "creator": "astronomy_blog", "source": "flickr",
             "license": "by-nc-sa", "license_version": "2.0", "mature": false},
            {"id": "bad", "url": "http://insecure.example/x.jpg"},
            {"id": "adult", "url": "https://x/y.jpg", "mature": true}
        ]}"""
        val hits = PictureResults.parseOpenverse(json)
        assertEquals("only the https, non-mature photo", listOf("ae9d"), hits.map { it.id })
        val photo = hits.single()
        assertEquals(PictureSource.PHOTOS, photo.source)
        assertEquals("https://api.openverse.org/v1/images/ae9d/thumb/", photo.thumbnailUrl)
        assertEquals("https://live.staticflickr.com/1/a_b.jpg", photo.fullUrl)
        assertEquals("astronomy_blog · Flickr · CC BY-NC-SA 2.0", photo.credit)
    }

    @Test
    fun `a photo is dropped for a word in its title or tags even when nothing flagged it`() {
        val json = """{"results": [
            {"id": "ok", "url": "https://a/bread.jpg", "title": "Fresh bread",
             "tags": [{"name": "bakery"}, {"name": "loaf"}]},
            {"id": "tagged", "url": "https://a/x.jpg", "title": "Beach",
             "tags": [{"name": "summer"}, {"name": "nude"}]},
            {"id": "titled", "url": "https://a/y.jpg", "title": "Erotic still life"}
        ]}"""
        assertEquals(listOf("ok"), PictureResults.parseOpenverse(json).map { it.id })
    }

    @Test
    fun `a photo with no thumbnail is shown from the picture itself`() {
        val json = """{"results": [{"id": "x", "url": "https://a/b.jpg"}]}"""
        assertEquals("https://a/b.jpg", PictureResults.parseOpenverse(json).single().thumbnailUrl)
    }

    @Test
    fun `licences read as people write them`() {
        assertEquals("Ana · Wikimedia · CC0", PictureResults.openverseCredit("Ana", "wikimedia", "cc0", "1.0"))
        assertEquals("Public domain", PictureResults.openverseCredit("", "", "pdm", ""))
        assertEquals("Joan · CC BY", PictureResults.openverseCredit("Joan", "", "by", ""))
    }
}
