package com.david.llegeix.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A word or phrase saved while reading, with everything needed to make sense of
 * it later.
 *
 * The point of saving a word in a language you are learning is not the word: it
 * is the sentence it was doing a job in. So the row keeps [context] — the line
 * it came from — alongside the document, page and line it was found on, and the
 * translation and pronunciation as they were at the time.
 *
 * Deliberately not a foreign key on the document: a saved word is a note about
 * the language, and losing your vocabulary because a PDF moved would be the
 * wrong trade. [displayName] is stored flat for the same reason.
 */
@Entity(
    tableName = "word_bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["uriString"],
            childColumns = ["documentUri"],
            // The word survives the document being forgotten.
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("documentUri"),
        Index(value = ["word"]),
    ],
)
data class WordBookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The Catalan word or phrase, exactly as it appeared. */
    val word: String,
    /** English, from the on-device model at the time of saving. */
    val translation: String?,
    /** Central Catalan IPA, as produced by the transcriber. */
    val ipa: String?,
    /** The line it was read in, so the sense is recoverable. */
    val context: String?,
    /** That line translated, which is what makes quoting it useful later. */
    val contextTranslation: String? = null,
    /**
     * What the word meant *there*, when the line said something the word alone
     * does not.
     *
     * Saved because otherwise the note contradicts the app that made it: a
     * reader who was just shown that *cap* is a head in this line would come
     * back to a card reading "cap — no". The row already exists to keep the
     * sentence the word was doing a job in; this is that job, written down.
     */
    val senseTranslation: String? = null,
    /**
     * The listed expression the reading came from, or null when it was read off
     * the line itself.
     *
     * Doubles as the record of which kind of reading this was, and so of how
     * much it can be trusted: an expression is a dictionary headword, a line
     * reading is a translation of one sentence.
     */
    val senseSource: String? = null,
    val documentUri: String?,
    val displayName: String?,
    val pageIndex: Int,
    /** 1-based line within the page, counted from the text layer. */
    val lineNumber: Int,
    val createdAt: Long = System.currentTimeMillis(),
)
