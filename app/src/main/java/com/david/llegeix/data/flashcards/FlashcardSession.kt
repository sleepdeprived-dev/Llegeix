package com.david.llegeix.data.flashcards

import com.david.llegeix.data.db.entity.FlashcardEntity
import kotlin.random.Random

/**
 * Which cards a study session asks about, and in what order.
 */
object FlashcardSession {

    /** An extra round can go through a whole ordinary deck. */
    const val EXTRA_LIMIT = 50

    /**
     * Every card there is to practise, least known first.
     *
     * A session used to be the cards that were *due* in this direction, capped
     * at twenty. It was a defensible design and it was not the one anybody
     * wanted: pressing play on *Vegetables* is a request to go through
     * *Vegetables*, and being handed the four cards a schedule had picked out
     * — with no way to tell, from the button, that the other twenty-six were
     * being held back — is the app answering a question it was not asked. A
     * deck is a thing somebody wrote by hand, and going through it is the
     * whole of what it is for.
     *
     * So there is no filter on the clock here and no cap. What the schedule is
     * still good for is the *order*: the cards in the lowest Leitner box come
     * first, so the ones least known are met while there is most attention
     * left, and cards in the same box are shuffled among themselves so that a
     * deck does not become a recitation in the order it was written.
     *
     * Answers are still recorded. The boxes are what order this list and what
     * fill the bar on each deck, so a session that changed nothing would make
     * both of them lies.
     *
     * @param random passed in so a test can fix the shuffle.
     */
    fun everything(
        cards: List<FlashcardEntity>,
        direction: StudyDirection,
        random: Random = Random.Default,
        language: MeaningLanguage = MeaningLanguage.Default,
    ): List<FlashcardEntity> =
        cards.filter { language.meaningOf(it) != null }
            .groupBy { direction.boxOf(it) }
            .toSortedMap()
            .values
            .flatMap { sameBox -> sameBox.sortedBy { it.id }.shuffled(random) }

    /**
     * A round of extra practice: cards whether they are due or not.
     *
     * Repetition is good, and a reader who wants to go through a deck again
     * after getting everything right should be able to. It is kept off the
     * schedule, though — answers in an extra round are not recorded — because
     * a card answered right three times in ten minutes has not been learned
     * three times over, and moving it up three boxes would say it had.
     *
     * The least-known come first, then shuffled among equals, so a short round
     * is spent where it helps most.
     */
    fun extra(
        cards: List<FlashcardEntity>,
        direction: StudyDirection,
        limit: Int = EXTRA_LIMIT,
        random: Random = Random.Default,
        language: MeaningLanguage = MeaningLanguage.Default,
    ): List<FlashcardEntity> =
        cards.filter { language.meaningOf(it) != null }
            .groupBy { direction.boxOf(it) }
            .toSortedMap()
            .values
            .flatMap { sameBox -> sameBox.sortedBy { it.id }.shuffled(random) }
            .take(limit)
}
