package com.david.llegeix.data.flashcards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoRelevanceTest {

    private fun page(index: Int, title: String, description: String, item: String? = null) =
        """{"index":$index,"title":"$title","description":"$description"""" +
            (item?.let { ""","pageprops":{"wikibase_item":"$it"}""" } ?: "") + "}"

    private fun search(vararg pages: String) = """{"query":{"pages":[${pages.joinToString(",")}]}}"""

    @Test
    fun `apple is the fruit, not the company`() {
        val json = search(
            page(2, "Apple Inc.", "American multinational technology company", "Q312"),
            page(1, "Apple", "Edible fruit", "Q89"),
            page(3, "Fiona Apple", "American musician (born 1977)"),
        )
        val concept = PhotoRelevance.concept(json, "apple")!!
        assertEquals("Apple", concept.title)
        assertEquals("fruit", concept.hint)
        assertEquals("Q89", concept.wikidataId)
    }

    @Test
    fun `limes finds the fruit past the disambiguation and the material`() {
        val json = search(
            page(1, "Lime", "Topics referred to by the same term"),
            page(3, "Lime (material)", "Calcium oxides and/or hydroxides"),
            page(4, "Lime (fruit)", "Citrus fruit", "Q13195"),
            page(8, "Limes (Roman Empire)", "Frontier and border defences of the Roman Empire"),
        )
        assertEquals("Lime (fruit)", PhotoRelevance.concept(json, "limes")!!.title)
    }

    @Test
    fun `a word with no vocabulary sense steers nothing`() {
        val json = search(page(1, "Peach Aviation", "Japanese low-cost airline"))
        assertNull(PhotoRelevance.concept(json, "peach"))
    }

    @Test
    fun `underwear is never offered, and people only for words about people`() {
        assertNull(PhotoRelevance.score("peach bra lingerie", "peach", "fruit"))
        val person = PhotoRelevance.score("man in lime shirt portrait", "lime", "fruit")!!
        val fruit = PhotoRelevance.score("Backyard limes Limes fruit", "lime", "fruit")!!
        assertTrue(fruit > person)
        assertTrue(PhotoRelevance.score("smiling woman portrait", "woman", null)!! > 0)
    }

    @Test
    fun `ranking drops the unmentioned from loose sources and puts scenery last`() {
        fun hit(id: String, text: String) =
            PictureHit(PictureSource.PHOTOS, id, "https://x/$id", "https://x/$id", "", context = text, title = text)
        val ranked = PhotoRelevance.rank(
            hits = listOf(
                hit("ov-random", "summer party"),
                hit("commons:village", "Peach village landscape"),
                hit("commons:fruit", "Peaches Prunus persica fruit"),
                hit("ov-bra", "peach bra"),
            ),
            query = "peach",
            hint = "fruit",
            strict = { !it.id.startsWith("commons:") },
        )
        assertEquals(listOf("commons:fruit", "commons:village"), ranked.map { it.id })
    }

    @Test
    fun `the word in a photo's own name counts for more than in its categories`() {
        val named = PhotoRelevance.score("Red apples Category:Fruit", "apple", "fruit", title = "Red apples.jpg")!!
        val filed = PhotoRelevance.score("Bananas Category:Apple and banana", "apple", "fruit", title = "Bananas.jpg")!!
        assertTrue(named > filed)
    }

    @Test
    fun `a fruit is wanted as the fruit, not its tree or blossom`() {
        val fruit = PhotoRelevance.score("Peaches fruit", "peach", "fruit", title = "Peaches fruit.jpg")!!
        val tree = PhotoRelevance.score("Peach tree blossom", "peach", "fruit", title = "Peach tree blossom.jpg")!!
        assertTrue(fruit > tree)
    }

    @Test
    fun `what people filed under the concept goes first`() {
        fun hit(id: String) = PictureHit(PictureSource.PHOTOS, id, "https://x/$id", "https://x/$id", "", "apple", "apple")
        val ranked = PhotoRelevance.rank(listOf(hit("a"), hit("b")), "apple", null, vouched = { it.id == "b" })
        assertEquals("b", ranked.first().id)
    }

    @Test
    fun `plurals are the same word`() {
        assertEquals("lime", PhotoRelevance.stem("limes"))
        assertEquals("peach", PhotoRelevance.stem("peaches"))
        assertEquals("cherry", PhotoRelevance.stem("cherries"))
        assertEquals("glass", PhotoRelevance.stem("glass"))
    }

    @Test
    fun `the commons category is read from wikidata`() {
        val json = """{"claims":{"P373":[{"mainsnak":{"datavalue":{"value":"Limes"}}}]}}"""
        assertEquals("Limes", PhotoRelevance.commonsCategory(json))
        assertNull(PhotoRelevance.commonsCategory("""{"claims":{}}"""))
    }

    @Test
    fun `the iNaturalist taxon and the category come from the concept's claims`() {
        val json = """{"entities":{"Q729":{"claims":{
            "P373":[{"mainsnak":{"datavalue":{"value":"Animalia"}}}],
            "P3151":[{"mainsnak":{"datavalue":{"value":"1"}}}]}}}}"""
        assertEquals("Animalia", PhotoRelevance.commonsCategory(json))
        assertEquals("1", PhotoRelevance.inaturalistTaxon(json))
    }

    @Test
    fun `iNaturalist photos are fetched larger than the square they are listed as`() {
        val json = """{"results":[
            {"taxon":{"preferred_common_name":"Lion"},"photos":[{"id":7,"license_code":"cc-by-nc",
             "attribution":"(c) someone","url":"https://inaturalist-open-data.s3.amazonaws.com/photos/7/square.jpg"}]},
            {"photos":[{"id":8,"license_code":null,"url":"https://x/photos/8/square.jpg"}]}
        ]}"""
        val hits = PictureResults.parseInaturalist(json, "lion")
        assertEquals(1, hits.size)
        assertTrue(hits.single().fullUrl.endsWith("/7/large.jpg"))
        assertTrue(hits.single().thumbnailUrl.endsWith("/7/medium.jpg"))
    }
}
