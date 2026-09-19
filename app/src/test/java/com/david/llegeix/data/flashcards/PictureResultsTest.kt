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
        val url = PictureResults.openverseSearchUrl("apple tree")
        assertEquals(
            "https://api.openverse.org/v1/images/?q=apple+tree&page_size=20&mature=false&category=photograph",
            url,
        )
        assertTrue(PictureResults.openverseSearchUrl("a&b=c").contains("q=a%26b%3Dc&"))
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
    fun `no more than a row's worth is offered`() {
        val json = (1..40).joinToString(",", "[", "]") { """{"_id": $it}""" }
        assertEquals(PictureResults.LIMIT, PictureResults.parseArasaac(json).size)
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
