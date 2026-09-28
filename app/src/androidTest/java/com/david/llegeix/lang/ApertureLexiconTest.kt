package com.david.llegeix.lang

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** The aperture list reaches the phone through the shared resources. */
@RunWith(AndroidJUnit4::class)
class ApertureLexiconTest {

    @Test
    fun loadsOnTheDevice() {
        val lexicon = ApertureLexicon.get()
        assertEquals("ɛ", lexicon.apertureOf("terra"))
        // And through the inflection it strips: terra → terres.
        assertEquals("ɛ", lexicon.apertureOf("terres"))
    }
}
