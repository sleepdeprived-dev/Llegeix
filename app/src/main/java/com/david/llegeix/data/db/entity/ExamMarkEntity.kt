package com.david.llegeix.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * What kind of mark this is.
 *
 * Stored as its name rather than its ordinal. An ordinal is a number whose
 * meaning lives in the order of a Kotlin enum, and the day somebody inserts a
 * value in the middle of that list every mark the reader has ever made silently
 * becomes a different kind of mark.
 */
enum class MarkKind {
    /** A freehand stroke: the path is in [ExamMarkEntity.points]. */
    INK,

    /** A typed answer: the words are in [ExamMarkEntity.text]. */
    TEXT,

    /** A box the reader ticked, whether the app found it or they placed it. */
    TICK,

    /** A run of highlighting: the rectangles are in [ExamMarkEntity.points]. */
    HIGHLIGHT,
}

/**
 * One thing the reader wrote on one page of one sitting.
 *
 * This is where the promise not to touch the PDF is actually kept. Nothing here
 * is written into the document; a mark is a row, and the page underneath it is
 * read-only for the whole life of the app. Rendering is the overlay of these
 * rows over the rendered page, which is the same trick the reader already uses
 * to underline saved words.
 *
 * ### Why the coordinates are fractions
 *
 * Every position is stored as a fraction of the page, from 0 to 1, never in
 * pixels. A pixel is only meaningful next to the size the page happened to be
 * rendered at, and that size changes constantly — a rotation, a pinch, a
 * different phone, the same phone in split screen. Storing pixels would mean
 * answers that drift off their questions the first time the reader turned the
 * device sideways, and there would be no way to put them back, because the size
 * they were written at is not recorded anywhere.
 *
 * Fractions also survive the one thing that is genuinely unrecoverable: the
 * reader coming back in three months on a new phone.
 *
 * The corollary is that the exam canvas must always render **whole** pages. The
 * reader's margin-cropping preference makes the bitmap a sub-rectangle of the
 * page, so a fraction of it is not a fraction of the page, and every mark would
 * land somewhere else the moment that switch was flicked.
 */
@Entity(
    tableName = "exam_marks",
    foreignKeys = [
        ForeignKey(
            entity = ExamAttemptEntity::class,
            parentColumns = ["id"],
            childColumns = ["attemptId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExamPartEntity::class,
            parentColumns = ["id"],
            childColumns = ["partId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    // Every read is "everything on this page of this document of this sitting",
    // so that is the index. Without it each page turn is a scan of every mark
    // in the attempt.
    indices = [Index("attemptId", "partId", "pageIndex"), Index("partId")],
)
data class ExamMarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    val attemptId: Long,

    /**
     * Which document of the paper this was written on.
     *
     * Marks are anchored to the part, not to a page number counted across the
     * whole exam, because those numbers move: adding a document, removing one
     * or reordering them shifts every page after the change, and answers
     * anchored to a shifting number slide onto the wrong questions. Removing a
     * document takes its marks with it, which is right — the pages they were
     * written on have gone.
     */
    val partId: Long,

    /** The page within [partId], not within the exam as a whole. */
    val pageIndex: Int,

    val kind: String,

    /**
     * The mark's bounding box, as fractions of the page.
     *
     * Carried even for an ink stroke, whose shape lives in [points], because
     * the eraser and the "what did I tap on" test both want a cheap answer
     * before they go near the path itself.
     */
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,

    val colorArgb: Int,

    /**
     * Stroke thickness, or text size, as a fraction of the page's width.
     *
     * A fraction for the same reason the positions are: a two-pixel line is a
     * different thing on a phone and on a tablet, and handwriting that changed
     * weight when the page was zoomed would not look like handwriting.
     */
    val size: Float = 0f,

    /** The typed answer, for [MarkKind.TEXT]. Null for every other kind. */
    val text: String? = null,

    /**
     * The geometry, encoded by [com.david.llegeix.data.exam.MarkGeometry].
     *
     * An ink path's points, or a highlight's rectangles. Null for a tick and
     * for a text box, which are fully described by the bounding box above.
     */
    val points: String? = null,

    /** Whether a [MarkKind.TICK] is ticked. A tick can be placed and then cleared. */
    val checked: Boolean = false,

    /**
     * How far a text box is turned, in degrees clockwise about its own centre.
     *
     * Only text has one. A stroke is already the shape it was drawn in — you
     * turn a stroke by drawing it turned — and a tick is a tick at any angle.
     * A typed answer is the one mark that has to fit a box somebody else
     * printed, and printed answer boxes on a scanned paper are not always
     * square to the page.
     */
    val rotation: Float = 0f,

    /**
     * Draw order, and the order the undo stack pops in.
     *
     * A counter rather than the timestamp, because two strokes made in the same
     * millisecond — which a fast scribble does produce — would otherwise have no
     * defined order and could swap places between one page turn and the next.
     */
    val sequence: Long = 0,

    val createdAt: Long = System.currentTimeMillis(),
)
