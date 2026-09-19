package com.david.llegeix.data.flashcards

import java.util.Locale

/**
 * Whether a name can be given to a deck.
 *
 * Two decks may not share a name, and "the same name" means what a reader
 * would mean by it: *Menjar* and *menjar* are one deck written twice, and so is
 * *Menjar* with a stray space after it. Two decks that look identical in the
 * list would leave the reader guessing which one a card went into.
 *
 * Pure, so the rule is tested once and every dialog asks the same question.
 */
object DeckNames {

    sealed interface Check {
        /** Usable, and this is the tidied form to store. */
        data class Ok(val name: String) : Check

        data object Blank : Check

        /** Already taken, by a deck whose name is spelled [existing]. */
        data class Taken(val existing: String) : Check
    }

    /** Trimmed, with any run of spaces inside it closed up to one. */
    fun tidy(name: String): String = name.trim().replace(WHITESPACE, " ")

    /**
     * @param others the names of every deck except the one being renamed, if any.
     *   Renaming a deck to its own name, or to a different capitalisation of it,
     *   is allowed: it is not colliding with anybody.
     */
    fun check(name: String, others: Collection<String>): Check {
        val tidied = tidy(name)
        if (tidied.isEmpty()) return Check.Blank
        val key = keyOf(tidied)
        val clash = others.firstOrNull { keyOf(it) == key }
        return if (clash != null) Check.Taken(tidy(clash)) else Check.Ok(tidied)
    }

    private fun keyOf(name: String): String = tidy(name).lowercase(Locale.ROOT)

    private val WHITESPACE = Regex("\\s+")
}
