package com.david.llegeix.data.flashcards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageSizingTest {

    @Test
    fun `a small picture is read whole and kept at its size`() {
        assertEquals(1, ImageSizing.sampleSize(800, 600))
        assertEquals(ImageSizing.Size(800, 600), ImageSizing.fit(800, 600))
    }

    @Test
    fun `a camera photo is shrunk while decoding but never below the target`() {
        // 4000 / 2 = 2000 and / 4 = 1000, which would be under 1024.
        assertEquals(2, ImageSizing.sampleSize(4000, 3000))
        // 12 MP and 48 MP photos.
        assertEquals(4, ImageSizing.sampleSize(3000, 4096))
        assertEquals(8, ImageSizing.sampleSize(8192, 6144))
    }

    @Test
    fun `whatever the sample, the decoded side still covers the target`() {
        for (edge in listOf(1024, 1500, 2048, 3000, 4032, 8000, 12000)) {
            val sample = ImageSizing.sampleSize(edge, edge / 2)
            assertTrue("$edge / $sample", edge / sample >= ImageSizing.MAX_EDGE)
        }
    }

    @Test
    fun `the longest side is fitted to the target and the shape kept`() {
        assertEquals(ImageSizing.Size(1024, 768), ImageSizing.fit(4000, 3000))
        assertEquals(ImageSizing.Size(768, 1024), ImageSizing.fit(3000, 4000))
        assertEquals(ImageSizing.Size(1024, 1024), ImageSizing.fit(2048, 2048))
    }

    @Test
    fun `a sliver never collapses to nothing`() {
        assertEquals(ImageSizing.Size(1024, 1), ImageSizing.fit(10000, 3))
    }
}
