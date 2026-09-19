package com.david.llegeix.data.flashcards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestedTest {

    @Test
    fun `an empty field takes a suggestion`() {
        val field = Suggested().offer("pâine")
        assertEquals("pâine", field.text)
        assertTrue(field.isSuggestion)
    }

    @Test
    fun `a suggestion is replaced as the word changes`() {
        val field = Suggested().offer("ˈpa").offer("ˈpan")
        assertEquals("ˈpan", field.text)
        assertTrue(field.isSuggestion)
    }

    @Test
    fun `typing makes the field the reader's and no suggestion overwrites it`() {
        val field = Suggested().offer("pâine").typed("pâinea").offer("franzelă")
        assertEquals("pâinea", field.text)
        assertFalse(field.isSuggestion)
    }

    @Test
    fun `an approximate suggestion says so until the reader edits it`() {
        val guessed = Suggested().offer("ˈtɛrə", approximate = true)
        assertTrue(guessed.isApproximate)
        val corrected = guessed.typed("ˈtɛrə ")
        assertFalse("the reader's own transcription is not a guess", corrected.isApproximate)
    }

    @Test
    fun `clearing the field hands it back to the suggestions`() {
        val field = Suggested().typed("mine").typed("").offer("pâine")
        assertEquals("pâine", field.text)
        assertTrue(field.isSuggestion)
    }

    @Test
    fun `an empty suggestion clears only a suggestion`() {
        assertEquals("", Suggested().offer("ˈpa").offer("").text)
        assertEquals("mine", Suggested().typed("mine").offer("").text)
    }

    @Test
    fun `a saved value is the reader's`() {
        val field = Suggested.owned("pâine").offer("altceva")
        assertEquals("pâine", field.text)
        assertFalse(field.isSuggestion)
    }

    @Test
    fun `typing the same text changes nothing`() {
        val field = Suggested().offer("ˈpa", approximate = true)
        assertEquals(field, field.typed("ˈpa"))
    }

    @Test
    fun `brackets and slashes come off a pasted transcription`() {
        assertEquals("ˈpeðɾə", tidyIpa(" [ˈpeðɾə] "))
        assertEquals("ˈtɛrə", tidyIpa("/ˈtɛrə/"))
        assertEquals("ˈpa", tidyIpa("ˈpa"))
        assertEquals("", tidyIpa("  "))
        assertEquals("[ˈpa", tidyIpa("[ˈpa"))
    }

    @Test
    fun `a lower-case word gets a lower-case meaning`() {
        assertEquals("planeta pământ", matchLeadingCase("terra", "Planeta pământ"))
        assertEquals("ștrand", matchLeadingCase("piscina", "Ștrand"))
    }

    @Test
    fun `a name keeps the capital the translator gave it`() {
        assertEquals("Barcelona", matchLeadingCase("Barcelona", "Barcelona"))
        assertEquals("Spania", matchLeadingCase("Espanya", "Spania"))
    }

    @Test
    fun `an abbreviation is left as it came`() {
        assertEquals("UE", matchLeadingCase("ue", "UE"))
    }

    @Test
    fun `nothing to match against changes nothing`() {
        assertEquals("pâine", matchLeadingCase("", "pâine"))
        assertEquals("", matchLeadingCase("pa", ""))
    }
}
