package com.david.llegeix.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A shelf of decks: "Food", holding "Vegetables", "Fruit" and "At the market".
 *
 * A reader with twenty decks on one subject wants two things — to practise
 * *Vegetables*, and to practise all of food at once — and a shelf gives them
 * both. Shelves can sit inside other shelves ([parentId]), to any depth, so
 * *Food* can hold *Fruit* and *Fruit* its own decks; practising a shelf goes
 * through everything under it.
 *
 * A deck belongs to at most one shelf, and belonging to none is normal: a
 * reader who never makes a collection never sees one, and the decks sit in the
 * list exactly as they did before collections existed.
 *
 * Names follow the same rule decks' do — see
 * [com.david.llegeix.data.flashcards.DeckNames] — and are checked against
 * other collections rather than against decks, because "Food" the shelf and
 * "Food" the deck are told apart by where they are and never confused for one
 * another in the same list.
 */
@Entity(tableName = "flashcard_collections")
data class FlashcardCollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** Kept at the top of the list, above the alphabetical rest, as a deck can be. */
    val isPinned: Boolean = false,
    /**
     * A picture the reader chose for the shelf, relative to the files directory
     * like a deck's or a card's.
     *
     * Null means the shelf borrows the picture of the first deck on it, which
     * is what it always did and is often right — *Food* showing the vegetables
     * is no worse than *Food* showing a basket. What it could not do was be
     * wrong on purpose: a shelf whose first deck alphabetically happens to be
     * *Herbs* wore a sprig of parsley and there was nothing to be done about
     * it.
     */
    val coverPath: String? = null,
    /** Who made [coverPath], when it came from a search. */
    val coverCredit: String? = null,
    /**
     * The shelf this one sits inside, or null for one at the top of the list.
     *
     * No foreign key: deleting a shelf moves what was inside it up a level
     * (see the repository), which a key's SET NULL would do wrongly — to the
     * top rather than to the parent. A parent that has gone anyway is read as
     * no parent.
     */
    val parentId: Long? = null,
)
