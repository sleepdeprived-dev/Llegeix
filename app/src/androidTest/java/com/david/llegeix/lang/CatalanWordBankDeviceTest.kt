package com.david.llegeix.lang

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The Viccionari, the thesaurus and the inflected forms reach the phone through the shared resources. */
@RunWith(AndroidJUnit4::class)
class CatalanWordBankDeviceTest {

    @Test
    fun loadsOnTheDevice() {
        val bank = CatalanWordBank.get()
        val finestra = bank.lookup("finestra")
        assertNotNull(finestra)
        assertTrue(finestra!!.definitions.isNotEmpty())
        assertTrue(finestra.senses.isNotEmpty())
        assertTrue("finestra" in bank.suggest("finest"))
        assertEquals("parlar", bank.verbEntry("parlava")?.form?.infinitive)
    }
}
