package com.david.llegeix.data.flashcards

/**
 * What a study session is over.
 *
 * Three answers, and the middle one is why this type exists: before
 * collections there were two, a deck or everything, and they were carried
 * around as a nullable deck id. Adding "this shelf" to a nullable Long would
 * have meant a second nullable Long beside it and a rule, written nowhere,
 * about which one wins.
 *
 * It crosses the navigation graph as two numbers because that is all a route
 * can hold — see [toRoute] and [fromRoute] — but everything on either side of
 * that boundary deals in this.
 */
sealed interface StudyScope {

    /** Every card the reader has, whatever deck or shelf it is on. */
    data object Everything : StudyScope

    data class Deck(val id: Long) : StudyScope

    /** Every deck on one shelf, as one session. */
    data class Collection(val id: Long) : StudyScope

    /** The deck id for a route, or -1 for "not a deck". */
    val deckArgument: Long get() = (this as? Deck)?.id ?: NONE

    /** The collection id for a route, or -1 for "not a collection". */
    val collectionArgument: Long get() = (this as? Collection)?.id ?: NONE

    companion object {
        /** What a route carries when it means "no id here". */
        const val NONE = -1L

        /**
         * Rebuild a scope from the two numbers a route carries.
         *
         * A deck wins over a collection if both somehow arrive, because a deck
         * is the narrower answer and answering a narrower question than the one
         * asked is the less surprising of the two mistakes.
         */
        fun fromRoute(deckId: Long, collectionId: Long): StudyScope = when {
            deckId >= 0 -> Deck(deckId)
            collectionId >= 0 -> Collection(collectionId)
            else -> Everything
        }
    }
}
