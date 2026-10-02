package com.david.llegeix.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the verb reader is allowed to say.
 *
 * The rule these tests are written to is that a wrong answer is worse than no
 * answer. A learner who is told *corria* is a conditional has been taught
 * something false about their own reading, and will not find out; one who is
 * told only that it is a form of *córrer* has lost nothing. So the cases that
 * matter most here are the ones where a plausible shortcut gives the wrong
 * tense.
 */
class CatalanVerbsTest {

    private fun finite(form: String, infinitive: String): List<VerbReading.Finite> =
        CatalanVerbs.analyse(form, infinitive)!!.readings.filterIsInstance<VerbReading.Finite>()

    private fun leading(form: String, infinitive: String): VerbReading =
        CatalanVerbs.analyse(form, infinitive)!!.readings.first()

    // ---- What counts as a verb at all --------------------------------------

    @Test
    fun conjugationIsDecidedByTheInfinitivesEnding() {
        assertEquals(Conjugation.FIRST, CatalanVerbs.conjugationOf("cantar"))
        assertEquals(Conjugation.SECOND, CatalanVerbs.conjugationOf("perdre"))
        assertEquals(Conjugation.SECOND, CatalanVerbs.conjugationOf("témer"))
        assertEquals(Conjugation.THIRD, CatalanVerbs.conjugationOf("dormir"))
        assertNull("a noun is not an infinitive", CatalanVerbs.conjugationOf("taula"))
        assertNull("nor is a fragment", CatalanVerbs.conjugationOf("ar"))
    }

    @Test
    fun aWordThatIsNotAFormOfAnInfinitiveIsNotAnalysed() {
        assertNull(CatalanVerbs.analyse("taules", "taula"))
    }

    // ---- The regular paradigms ---------------------------------------------

    @Test
    fun firstConjugationIsReadEndingByEnding() {
        assertEquals(
            VerbReading.Finite(Mood.INDICATIVE, Tense.IMPERFECT, Person.S2),
            leading("cantaves", "cantar"),
        )
        assertEquals(
            VerbReading.Finite(Mood.SUBJUNCTIVE, Tense.IMPERFECT, Person.P1),
            leading("cantéssim", "cantar"),
        )
        assertEquals(
            VerbReading.Finite(Mood.INDICATIVE, Tense.CONDITIONAL, Person.S1),
            leading("cantaria", "cantar"),
        )
        assertEquals(
            VerbReading.Finite(Mood.INDICATIVE, Tense.FUTURE, Person.P3),
            leading("cantaran", "cantar"),
        )
        assertEquals(
            VerbReading.Finite(Mood.INDICATIVE, Tense.PAST, Person.P3),
            leading("cantaren", "cantar"),
        )
    }

    @Test
    fun thirdConjugationReadsBothItsPresents() {
        assertEquals(
            VerbReading.Finite(Mood.INDICATIVE, Tense.PRESENT, Person.S2),
            leading("serveixes", "servir"),
        )
        assertEquals(
            VerbReading.Finite(Mood.INDICATIVE, Tense.PRESENT, Person.S2),
            leading("dorms", "dormir"),
        )
        assertEquals(
            VerbReading.Finite(Mood.INDICATIVE, Tense.CONDITIONAL, Person.S1),
            leading("dormiria", "dormir"),
        )
    }

    @Test
    fun secondConjugationBuildsItsFutureOnTheInfinitive() {
        assertEquals(
            VerbReading.Finite(Mood.INDICATIVE, Tense.FUTURE, Person.S1),
            leading("beuré", "beure"),
        )
        assertEquals(
            VerbReading.Finite(Mood.INDICATIVE, Tense.FUTURE, Person.P1),
            leading("beurem", "beure"),
        )
    }

    /**
     * The case the generated paradigm exists for.
     *
     * By its ending alone *corria* looks exactly like the conditional of a verb
     * whose stem ends in *-r*, which *córrer*'s does. Only knowing what the
     * paradigm of *córrer* actually is tells the two apart — and the accent
     * moves off the stem on the way, which is why the comparison ignores
     * accents.
     */
    @Test
    fun aStemEndingInRDoesNotTurnAnImperfectIntoAConditional() {
        assertEquals(
            VerbReading.Finite(Mood.INDICATIVE, Tense.IMPERFECT, Person.S1),
            leading("corria", "córrer"),
        )
        assertEquals(
            VerbReading.Finite(Mood.INDICATIVE, Tense.CONDITIONAL, Person.S1),
            leading("correria", "córrer"),
        )
    }

    /**
     * A stem that changes its spelling is still read, by its ending.
     *
     * *menjar* writes its present as *menges*, not *menjes*, so nothing the
     * generator produces will match — and the ending is untouched by the
     * change, which is the whole reason the ending table is there.
     */
    @Test
    fun aSpellingChangeInTheStemDoesNotLoseTheForm() {
        assertEquals(
            VerbReading.Finite(Mood.INDICATIVE, Tense.PRESENT, Person.S2),
            leading("menges", "menjar"),
        )
    }

    // ---- The forms with no person in them ----------------------------------

    @Test
    fun nonFiniteFormsAreNamed() {
        assertEquals(
            VerbReading.NonFinite(NonFiniteForm.INFINITIVE),
            leading("cantar", "cantar"),
        )
        assertEquals(
            VerbReading.NonFinite(NonFiniteForm.GERUND),
            leading("cantant", "cantar"),
        )
        assertEquals(
            VerbReading.NonFinite(NonFiniteForm.PARTICIPLE),
            leading("dormida", "dormir"),
        )
        assertTrue(CatalanVerbs.analyse("cantar", "cantar")!!.isInfinitive)
        assertTrue(!CatalanVerbs.analyse("cantava", "cantar")!!.isInfinitive)
    }

    // ---- Forms that really are two things ----------------------------------

    /**
     * *canta* is the present and it is an order, and the card says both.
     *
     * The indicative leads, because that is overwhelmingly what a reader has
     * just met on a page.
     */
    @Test
    fun anAmbiguousFormKeepsBothReadingsWithTheIndicativeFirst() {
        val readings = finite("canta", "cantar")
        assertEquals(
            VerbReading.Finite(Mood.INDICATIVE, Tense.PRESENT, Person.S3),
            readings.first(),
        )
        assertTrue(
            "and the imperative is still offered",
            VerbReading.Finite(Mood.IMPERATIVE, null, Person.S2) in readings,
        )
    }

    // ---- The six written out by hand ---------------------------------------

    /**
     * *és* is the present of *ser* and nothing else.
     *
     * By its ending it is a first- or second-conjugation imperfect
     * subjunctive, which is the kind of confidently wrong answer the irregular
     * table exists to prevent.
     */
    @Test
    fun serIsReadFromItsOwnParadigm() {
        assertEquals(
            VerbReading.Finite(Mood.INDICATIVE, Tense.PRESENT, Person.S3),
            leading("és", "ser"),
        )
        assertEquals(
            VerbReading.Finite(Mood.INDICATIVE, Tense.PRESENT, Person.S1),
            leading("sóc", "ser"),
        )
        assertEquals("and the other spelling of it", leading("sóc", "ser"), leading("soc", "ser"))
        assertEquals(
            VerbReading.Finite(Mood.SUBJUNCTIVE, Tense.IMPERFECT, Person.S1),
            leading("fos", "ser"),
        )
        assertEquals("filed under both its names", leading("és", "ser"), leading("és", "ésser"))
    }

    @Test
    fun theOtherIrregularsAreReadToo() {
        assertEquals(
            VerbReading.Finite(Mood.INDICATIVE, Tense.PRESENT, Person.S1),
            leading("vaig", "anar"),
        )
        assertEquals(
            VerbReading.Finite(Mood.INDICATIVE, Tense.PRESENT, Person.S1),
            leading("tinc", "tenir"),
        )
        assertEquals(
            VerbReading.Finite(Mood.INDICATIVE, Tense.PRESENT, Person.S1),
            leading("faig", "fer"),
        )
        assertEquals(
            VerbReading.Finite(Mood.INDICATIVE, Tense.FUTURE, Person.S1),
            leading("tindré", "tenir"),
        )
        assertEquals(
            VerbReading.Finite(Mood.INDICATIVE, Tense.PRESENT, Person.S3),
            leading("ha", "haver"),
        )
        assertEquals(
            VerbReading.Finite(Mood.INDICATIVE, Tense.PRESENT, Person.S1),
            leading("estic", "estar"),
        )
    }

    /**
     * An irregular stem plus a one-letter ending is not evidence of anything.
     *
     * *digues* is an order. By its last letter alone it is the second person
     * of the present, which is both wrong and entirely plausible-looking — so
     * the card says it is a form of *dir* and stops there.
     */
    @Test
    fun aSingleLetterEndingOnAnIrregularStemIsNotGuessedAt() {
        val found = CatalanVerbs.analyse("digues", "dir")!!
        assertTrue("nothing is claimed about which form it is", found.readings.isEmpty())
        assertEquals("dir", found.infinitive)
        assertEquals(Conjugation.THIRD, found.conjugation)
    }

    /**
     * An irregular form the table has never heard of says nothing rather than
     * guessing.
     */
    @Test
    fun anUnknownFormOfAKnownIrregularIsLeftUnplaced() {
        val found = CatalanVerbs.analyse("essent-hi", "ser")
        assertTrue("it is still a form of ser", found!!.readings.isEmpty())
        assertEquals("ser", found.infinitive)
    }
}
