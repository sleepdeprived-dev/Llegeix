package com.david.llegeix.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A paper the reader has brought into the app to sit.
 *
 * Unlike every other document Llegeix knows about, an exam is **copied** into
 * the app's own storage rather than referred to by a `content://` grant, and
 * that is the central decision of the whole feature.
 *
 * Three reasons, in order of how much they matter:
 *
 *  - **The work must not be able to outlive its paper.** An attempt is a set of
 *    marks positioned on particular pages. If the PDF behind them can be moved,
 *    renamed, deleted or signed out of — all of which happen to a file sitting
 *    in Downloads or in Proton Drive — then the reader's answers survive with
 *    nothing to sit on. Copying is what makes "come back to it in March" a
 *    promise the app can actually keep.
 *  - **The original is never touched.** The reader asked for exactly this: work
 *    on the exam without changing the exam. A copy the app owns cannot be
 *    written back to by accident, because the app never holds a writable handle
 *    to the file the reader picked.
 *  - **It stays out of the library.** Persisted single-file grants are what
 *    [com.david.llegeix.data.source.PickedFilePdfSource] enumerates, so keeping
 *    a grant would quietly file every exam among the reader's books.
 *
 * The cost is disk: an exam is stored twice if the reader also keeps the
 * original. Exam papers are small next to the translation models the app
 * already downloads, and deleting the exam here deletes the copy.
 *
 * ### Why there is no file here
 *
 * An exam is a *paper*, and a paper is not always one PDF. A Catalan exam
 * sample routinely arrives as several files — the reading, the listening, the
 * writing — which are one exam to the person sitting them and were three
 * unrelated rows in this app until [ExamPartEntity] existed. The documents live
 * there, in order; this row is only the identity of the paper.
 */
@Entity(tableName = "exams")
data class ExamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** What the reader calls it. Seeded from the file's name, then editable. */
    val title: String,

    /**
     * The official answer sheet, if the reader has one.
     *
     * A second copied PDF rather than anything the app tries to understand. The
     * reader asked to be able to bring in the correct answers, and for a paper
     * that comes with them the honest form of that is the sheet itself, shown
     * beside their own work — not an attempt to parse somebody's answer key into
     * fields, which would be wrong often enough to be worse than useless.
     *
     * Null until one is attached, which is the common case: most sample papers
     * are downloaded without their key.
     */
    val answerKeyFileName: String? = null,

    /** How long the key is. Null when there is no key. */
    val answerKeyPageCount: Int? = null,

    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * One document of an exam, in the order it is worked through.
 *
 * This exists because a paper is not always a file. An exam sample is commonly
 * handed out as several PDFs — reading, listening, writing — and before this
 * they were several exams, which is exactly as confusing as it sounds: three
 * rows in the list with the same name, three separate sets of attempts, and no
 * way to say they were one thing.
 *
 * The parts are shown as one continuous run of pages, so the reader swipes off
 * the end of the reading paper and onto the front of the listening one without
 * having to know there was a join. [position] is what defines that order.
 *
 * A mark records the part it was made on rather than a page number counted
 * across the whole exam. The difference only shows up later, and then it shows
 * up badly: adding a part, removing one, or putting them in a different order
 * moves every global page number after it, and answers anchored to those
 * numbers would slide onto the wrong questions. Anchored to the part, they
 * cannot.
 */
@Entity(
    tableName = "exam_parts",
    foreignKeys = [
        ForeignKey(
            entity = ExamEntity::class,
            parentColumns = ["id"],
            childColumns = ["examId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("examId")],
)
data class ExamPartEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    val examId: Long,

    /** The name the file had when it was imported. Shown, so it is recognisable. */
    val sourceName: String,

    /** The copy's name inside the app's own exam directory. Never shown. */
    val fileName: String,

    val pageCount: Int,

    /** Where this document falls in the paper. Zero-based, dense. */
    val position: Int = 0,

    /**
     * Blank paper the app made, rather than a document the reader imported.
     *
     * A writing exam needs somewhere to write, and the honest form of that is
     * more paper at the back of the same booklet — not a separate notes screen
     * with its own navigation. Because it is an ordinary part, an essay is
     * written with the same pen, saved by the same code and exported into the
     * same PDF as every answer on the printed pages.
     */
    val isNotes: Boolean = false,
)

/**
 * One sitting of an exam: the reader's answers, on a particular day.
 *
 * This is the "instance" the whole feature is built around. The exam is the
 * paper and never changes; an attempt is everything written on it. Taking the
 * same paper again in three months makes a second attempt over the same pages,
 * which is what makes the two comparable at all — the marks differ, the thing
 * they are marks on does not.
 *
 * Deleting the exam deletes its attempts, because an attempt with no paper
 * behind it is a set of coordinates and nothing else.
 */
@Entity(
    tableName = "exam_attempts",
    foreignKeys = [
        ForeignKey(
            entity = ExamEntity::class,
            parentColumns = ["id"],
            childColumns = ["examId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("examId")],
)
data class ExamAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    val examId: Long,

    /** "Attempt 1", until the reader calls it something else. */
    val label: String,

    val startedAt: Long = System.currentTimeMillis(),

    /**
     * When it was last opened, which is what the list sorts on.
     *
     * A reader coming back to the app is far more likely to want the sitting
     * they were in the middle of than the one they started first.
     */
    val lastOpenedAt: Long = System.currentTimeMillis(),

    /**
     * When the reader said they had finished, or null while it is still open.
     *
     * Said rather than inferred. There is no way for the app to know an exam is
     * done — a blank page is indistinguishable from a page not reached — so
     * this is a button, and until it is pressed the attempt is in progress.
     */
    val finishedAt: Long? = null,

    /** Where to reopen. An exam is worked through in order far more than a book. */
    val lastPage: Int = 0,
)

/**
 * A recording that belongs to an exam, for the listening section.
 *
 * Attached to the exam rather than to an attempt, because the audio is part of
 * the paper: every sitting of a listening exam plays the same tracks. Copied
 * into app storage for the same reasons the PDF is.
 */
@Entity(
    tableName = "exam_audio",
    foreignKeys = [
        ForeignKey(
            entity = ExamEntity::class,
            parentColumns = ["id"],
            childColumns = ["examId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("examId")],
)
data class ExamAudioEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    val examId: Long,

    /** The file's own name, which is what the reader picked it out by. */
    val displayName: String,

    /** The copy's name inside the app's own exam directory. Never shown. */
    val fileName: String,

    /** Milliseconds, or null when the file would not report a duration. */
    val durationMs: Long? = null,

    /** Keeps the tracks in the order they were added, which is exam order. */
    val position: Int = 0,
)
