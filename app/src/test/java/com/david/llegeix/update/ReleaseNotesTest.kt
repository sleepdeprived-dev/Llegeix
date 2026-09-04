package com.david.llegeix.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Release notes written as markdown, shown as prose. */
class ReleaseNotesTest {

    @Test
    fun `takes the marks off and leaves the sentences`() {
        val plain = ReleaseNotes.plain(
            """
            Llegeix v3.4 — una cosa nova.

            ## Per què

            Perquè **calia**, i el `botó` no hi era.
            """.trimIndent(),
        )

        assertEquals(
            """
            Llegeix v3.4 — una cosa nova.

            Per què

            Perquè calia, i el botó no hi era.
            """.trimIndent(),
            plain,
        )
    }

    @Test
    fun `drops tables and rules, which are drawings rather than sentences`() {
        val plain = ReleaseNotes.plain(
            """
            Abans.

            | File | For |
            | --- | --- |
            | `a.apk` | phones |

            ---

            Després.
            """.trimIndent(),
        )

        assertFalse(plain, "|" in plain)
        assertTrue(plain, plain.startsWith("Abans."))
        assertTrue(plain, plain.endsWith("Després."))
        // The gap the table left does not become a hole in the middle.
        assertFalse(plain, "\n\n\n" in plain)
    }

    @Test
    fun `puts a wrapped paragraph back together`() {
        // Notes are written wrapped for a terminal. Kept as written, every line
        // breaks a second time against the edge of a phone-sized card and the
        // paragraph comes out as a ragged column of half-lines.
        val plain = ReleaseNotes.plain(
            """
            Mantenir premuda una paraula no és, en aquesta
            aplicació, l'alternativa rara i deliberada a tocar-la:
            és com es fa servir l'aplicació.

            I un altre paràgraf.
            """.trimIndent(),
        )

        assertEquals(
            "Mantenir premuda una paraula no és, en aquesta aplicació, " +
                "l'alternativa rara i deliberada a tocar-la: és com es fa servir " +
                "l'aplicació.\n\nI un altre paràgraf.",
            plain,
        )
    }

    @Test
    fun `keeps a heading and a bullet on their own`() {
        val plain = ReleaseNotes.plain(
            """
            ## Què ha canviat

            - **La pulsació és més curta.** 320 ms en comptes
              dels 500 del sistema.
            - I una altra cosa.
            """.trimIndent(),
        )

        assertEquals(
            "Què ha canviat\n\n" +
                "- La pulsació és més curta. 320 ms en comptes dels 500 del sistema.\n\n" +
                "- I una altra cosa.",
            plain,
        )
    }

    @Test
    fun `leaves ordinary prose alone`() {
        val prose = "Una frase.\n\nI una altra, amb 3 * 4 dins."
        assertEquals(prose, ReleaseNotes.plain(prose))
    }

    @Test
    fun `survives an empty body`() {
        assertEquals("", ReleaseNotes.plain(""))
        assertEquals("", ReleaseNotes.plain("\n\n\n"))
    }
}
