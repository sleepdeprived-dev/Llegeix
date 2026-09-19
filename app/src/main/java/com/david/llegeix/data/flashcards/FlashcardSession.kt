package com.david.llegeix.data.flashcards

import com.david.llegeix.data.db.entity.FlashcardEntity
import kotlin.random.Random

/**
 * Which cards a study session asks about, and in what order.
 */
object FlashcardSession {

    /**
     * How many cards one session holds, the same as the saved-words practice.
     *
     * A session is something to finish. Forty cards due is two sessions, not
     * one long one that is abandoned at card twenty-six.
     */
    const val DEFAULT_LIMIT = 20

    /**
     * The cards due in [direction], longest-waiting first.
     *
     * Cards that fell due at the same moment are shuffled among themselves.
     * That is almost always a batch of new cards, all due "as soon as
     * possible", and asking them in the order they were written would let the
     * order do the remembering: the third card is easy when you know it comes
     * after *pa* and *aigua*.
     *
     * @param random passed in so a test can fix the shuffle.
     */
    fun deal(
        cards: List<FlashcardEntity>,
        direction: StudyDirection,
        now: Long,
        limit: Int = DEFAULT_LIMIT,
        random: Random = Random.Default,
    ): List<FlashcardEntity> =
        cards.filter { direction.isDue(it, now) }
            .groupBy { direction.dueAtOf(it) }
            .toSortedMap()
            .values
            .flatMap { sameMoment -> sameMoment.sortedBy { it.id }.shuffled(random) }
            .take(limit)

    /** When the next card not yet due will be, or null if none is waiting. */
    fun nextDueAt(cards: List<FlashcardEntity>, direction: StudyDirection, now: Long): Long? =
        cards.map { direction.dueAtOf(it) }.filter { it > now }.minOrNull()
}
