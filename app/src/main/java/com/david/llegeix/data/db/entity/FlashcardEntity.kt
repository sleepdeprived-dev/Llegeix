package com.david.llegeix.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One hand-made card: a Catalan word and what it means in Romanian.
 *
 * ### Two schedules, one per direction
 *
 * Recognising *pa* and producing *pa* from *pâine* are different skills, and
 * the first is always the easier. A card with one schedule would be pushed back
 * a week by a right answer in the easy direction while the hard one had never
 * been asked. So each direction keeps its own Leitner box and its own due date,
 * and both are moved by the same [com.david.llegeix.data.practice.Leitner]. The
 * fields mean exactly what they mean on [WordBookmarkEntity].
 *
 * ### The deck owns its cards
 *
 * Deleting a deck deletes its cards, by cascade. The pictures are files, which
 * SQLite cannot delete, so whoever deletes a deck collects [imagePath] first.
 */
@Entity(
    tableName = "flashcards",
    foreignKeys = [
        ForeignKey(
            entity = FlashcardDeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("deckId")],
)
data class FlashcardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deckId: Long,
    val catalan: String,
    val romanian: String,
    /**
     * The meaning in English, for practising Catalan against English instead.
     *
     * Optional, and a second meaning rather than a second card: the schedule
     * is about knowing the Catalan — recognising it, producing it — and that
     * is the same skill whichever language the meaning is given in.
     */
    val english: String? = null,
    /** Central Catalan IPA, generated from the spelling and then the reader's. */
    val ipa: String? = null,
    /**
     * True while [ipa] is still the transcriber's guess at an unmarked e or o.
     *
     * Cleared the moment the reader edits the pronunciation: from then on it is
     * their transcription rather than the app's guess, and calling it
     * approximate would be the app doubting them.
     */
    val ipaApproximate: Boolean = false,
    /** Relative to the app's files directory, e.g. `flashcards/3f2a.jpg`. */
    val imagePath: String? = null,
    /**
     * Who made the picture and under what licence, when it came from a search
     * rather than the reader's own photos — "Sergio Palao · ARASAAC · CC
     * BY-NC-SA". Those licences ask for the credit to travel with the picture,
     * and the card is where the picture goes. Null for the reader's own.
     */
    val imageCredit: String? = null,
    val createdAt: Long = System.currentTimeMillis(),

    // Catalan → Romanian: seeing the word and knowing what it means.
    val box: Int = 0,
    /** Zero is "as soon as possible", which is what a card just made should be. */
    val dueAt: Long = 0,
    val reviewCount: Int = 0,
    val lastReviewedAt: Long? = null,

    // Romanian → Catalan: having the meaning and producing the word.
    val reverseBox: Int = 0,
    val reverseDueAt: Long = 0,
    val reverseReviewCount: Int = 0,
    val reverseLastReviewedAt: Long? = null,

    /**
     * When the reader last answered "not yet" to this card, or null when it
     * is not among their weak words. Set by any session; cleared when the card
     * is answered right in a review of the weak words themselves — so that
     * list is exactly the words the reader said they did not know, until they
     * show they do.
     */
    val weakAt: Long? = null,
)
