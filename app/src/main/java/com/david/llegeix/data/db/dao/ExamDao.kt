package com.david.llegeix.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.david.llegeix.data.db.entity.ExamAttemptEntity
import com.david.llegeix.data.db.entity.ExamAudioEntity
import com.david.llegeix.data.db.entity.ExamEntity
import com.david.llegeix.data.db.entity.ExamMarkEntity
import com.david.llegeix.data.db.entity.ExamPartEntity
import kotlinx.coroutines.flow.Flow

/**
 * An exam as the list needs it: the paper, plus what has been done with it.
 *
 * The counts are computed rather than stored. A row that carried its own
 * attempt count would have to be updated every time an attempt was made or
 * deleted, and the two would eventually disagree — which for this list means
 * telling the reader they have sat a paper they have not.
 */
data class ExamWithProgress(
    val id: Long,
    val title: String,
    val createdAt: Long,
    /** Pages across every document of the paper. */
    val pageCount: Int,
    /** How many documents make up the paper; 1 for most of them. */
    val partCount: Int,
    /**
     * The first document's stored file, for the cover.
     *
     * The first rather than any: a paper's cover is its front page, and the
     * front page of a multi-part exam is the front page of its first document.
     * Null only for the moment between a paper being created and its first
     * document landing, which the list draws as a lettered tile anyway.
     */
    val coverFileName: String?,
    val attemptCount: Int,
    val audioCount: Int,
    /** Null when no answer sheet has been attached. */
    val answerKeyFileName: String?,
    /** When any sitting of this paper was last opened, or null if none has been. */
    val lastOpenedAt: Long?,
)

@Dao
interface ExamDao {

    /**
     * Every exam, most recently worked on first.
     *
     * Two LEFT JOINs would multiply each other — an exam with three attempts
     * and two recordings would report six of each — so the counts are taken as
     * correlated subqueries instead. Ordering puts a paper with no sittings at
     * the bottom by its own creation date, which is where a freshly imported
     * exam belongs until it has been opened.
     */
    @Query(
        """
        SELECT e.id AS id, e.title AS title, e.createdAt AS createdAt,
               e.answerKeyFileName AS answerKeyFileName,
               (SELECT COALESCE(SUM(p.pageCount), 0) FROM exam_parts p WHERE p.examId = e.id)
                   AS pageCount,
               (SELECT COUNT(*) FROM exam_parts p WHERE p.examId = e.id)
                   AS partCount,
               (SELECT p.fileName FROM exam_parts p WHERE p.examId = e.id
                    ORDER BY p.position, p.id LIMIT 1)
                   AS coverFileName,
               (SELECT COUNT(*) FROM exam_attempts a WHERE a.examId = e.id)
                   AS attemptCount,
               (SELECT COUNT(*) FROM exam_audio u WHERE u.examId = e.id)
                   AS audioCount,
               (SELECT MAX(a.lastOpenedAt) FROM exam_attempts a WHERE a.examId = e.id)
                   AS lastOpenedAt
        FROM exams e
        ORDER BY COALESCE(lastOpenedAt, e.createdAt) DESC
        """,
    )
    fun observeExams(): Flow<List<ExamWithProgress>>

    @Query("SELECT * FROM exams WHERE id = :examId")
    suspend fun exam(examId: Long): ExamEntity?

    @Insert
    suspend fun insert(exam: ExamEntity): Long

    @Query("UPDATE exams SET title = :title WHERE id = :examId")
    suspend fun rename(examId: Long, title: String)

    @Query("SELECT * FROM exams WHERE id = :examId")
    fun observeExam(examId: Long): Flow<ExamEntity?>

    @Query(
        """
        UPDATE exams SET answerKeyFileName = :fileName, answerKeyPageCount = :pageCount
        WHERE id = :examId
        """,
    )
    suspend fun setAnswerKey(examId: Long, fileName: String?, pageCount: Int?)

    @Query("DELETE FROM exams WHERE id = :examId")
    suspend fun delete(examId: Long)

    // --- Parts -------------------------------------------------------------

    @Query("SELECT * FROM exam_parts WHERE examId = :examId ORDER BY position, id")
    fun observeParts(examId: Long): Flow<List<ExamPartEntity>>

    @Query("SELECT * FROM exam_parts WHERE examId = :examId ORDER BY position, id")
    suspend fun parts(examId: Long): List<ExamPartEntity>

    @Query("SELECT * FROM exam_parts WHERE id = :partId")
    suspend fun part(partId: Long): ExamPartEntity?

    @Insert
    suspend fun insert(part: ExamPartEntity): Long

    @Query("DELETE FROM exam_parts WHERE id = :partId")
    suspend fun deletePart(partId: Long)

    @Query("UPDATE exam_parts SET position = :position WHERE id = :partId")
    suspend fun setPartPosition(partId: Long, position: Int)

    /**
     * What a document of the paper is called, inside the app.
     *
     * The stored copy's filename is untouched: it is a UUID nobody reads, and
     * the marks written on the document are addressed by the part's id, so
     * renaming cannot move a single answer.
     */
    @Query("UPDATE exam_parts SET sourceName = :name WHERE id = :partId")
    suspend fun renamePart(partId: Long, name: String)

    /** Used when a page is added to the blank paper at the back of a booklet. */
    @Query("UPDATE exam_parts SET pageCount = :pageCount WHERE id = :partId")
    suspend fun setPartPageCount(partId: Long, pageCount: Int)

    /** The blank paper attached to a paper, if the reader has asked for any. */
    @Query("SELECT * FROM exam_parts WHERE examId = :examId AND isNotes = 1 LIMIT 1")
    suspend fun notesPart(examId: Long): ExamPartEntity?

    @Transaction
    suspend fun reorderParts(examId: Long, orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> setPartPosition(id, index) }
    }

    // --- Attempts ----------------------------------------------------------

    /** Newest sitting first: the one being worked on is the one wanted. */
    @Query("SELECT * FROM exam_attempts WHERE examId = :examId ORDER BY startedAt DESC")
    fun observeAttempts(examId: Long): Flow<List<ExamAttemptEntity>>

    @Query("SELECT COUNT(*) FROM exam_attempts WHERE examId = :examId")
    suspend fun attemptCount(examId: Long): Int

    @Query("SELECT * FROM exam_attempts WHERE id = :attemptId")
    suspend fun attempt(attemptId: Long): ExamAttemptEntity?

    @Query("SELECT * FROM exam_attempts WHERE id = :attemptId")
    fun observeAttempt(attemptId: Long): Flow<ExamAttemptEntity?>

    @Insert
    suspend fun insert(attempt: ExamAttemptEntity): Long

    @Query("UPDATE exam_attempts SET label = :label WHERE id = :attemptId")
    suspend fun renameAttempt(attemptId: Long, label: String)

    @Query(
        "UPDATE exam_attempts SET lastOpenedAt = :at, lastPage = :page WHERE id = :attemptId",
    )
    suspend fun recordOpened(attemptId: Long, page: Int, at: Long = System.currentTimeMillis())

    @Query("UPDATE exam_attempts SET finishedAt = :at WHERE id = :attemptId")
    suspend fun setFinished(attemptId: Long, at: Long?)

    @Query("DELETE FROM exam_attempts WHERE id = :attemptId")
    suspend fun deleteAttempt(attemptId: Long)

    // --- Audio -------------------------------------------------------------

    @Query("SELECT * FROM exam_audio WHERE examId = :examId ORDER BY position, id")
    fun observeAudio(examId: Long): Flow<List<ExamAudioEntity>>

    @Query("SELECT * FROM exam_audio WHERE examId = :examId ORDER BY position, id")
    suspend fun audio(examId: Long): List<ExamAudioEntity>

    @Insert
    suspend fun insert(audio: ExamAudioEntity): Long

    @Query("DELETE FROM exam_audio WHERE id = :audioId")
    suspend fun deleteAudio(audioId: Long)

    /** The name shown for a recording. The stored copy keeps its own filename. */
    @Query("UPDATE exam_audio SET displayName = :name WHERE id = :audioId")
    suspend fun renameAudio(audioId: Long, name: String)

    // --- Marks -------------------------------------------------------------

    /**
     * Everything written on one page of one sitting, in the order it was made.
     *
     * A flow per page rather than one for the whole attempt: a long paper can
     * carry thousands of marks, and re-emitting all of them because a stroke
     * was added on page nine would redraw every page the pager is holding.
     */
    @Query(
        """
        SELECT * FROM exam_marks
        WHERE attemptId = :attemptId AND partId = :partId AND pageIndex = :pageIndex
        ORDER BY sequence, id
        """,
    )
    fun observeMarks(attemptId: Long, partId: Long, pageIndex: Int): Flow<List<ExamMarkEntity>>

    /** Every mark in a sitting, for export and for comparing two sittings. */
    @Query(
        """
        SELECT * FROM exam_marks WHERE attemptId = :attemptId
        ORDER BY partId, pageIndex, sequence, id
        """,
    )
    suspend fun marks(attemptId: Long): List<ExamMarkEntity>

    /**
     * How many pages of this sitting have anything on them at all.
     *
     * Counted over the pair, not over the page number alone: page three of the
     * reading paper and page three of the listening paper are two pages, and
     * counting distinct page numbers would call them one.
     */
    @Query(
        """
        SELECT COUNT(*) FROM (
            SELECT DISTINCT partId, pageIndex FROM exam_marks WHERE attemptId = :attemptId
        )
        """,
    )
    fun observeMarkedPageCount(attemptId: Long): Flow<Int>

    /**
     * The next draw position for a sitting.
     *
     * MAX + 1 rather than a count, so deleting a stroke and drawing another
     * cannot hand out a sequence number that is already in use.
     */
    @Query("SELECT COALESCE(MAX(sequence), 0) + 1 FROM exam_marks WHERE attemptId = :attemptId")
    suspend fun nextSequence(attemptId: Long): Long

    @Insert
    suspend fun insert(mark: ExamMarkEntity): Long

    @Update
    suspend fun update(mark: ExamMarkEntity)

    @Query("DELETE FROM exam_marks WHERE id = :markId")
    suspend fun deleteMark(markId: Long)

    /** The most recent mark on a sitting, which is what undo takes back. */
    @Query("SELECT * FROM exam_marks WHERE attemptId = :attemptId ORDER BY sequence DESC, id DESC LIMIT 1")
    suspend fun lastMark(attemptId: Long): ExamMarkEntity?

    @Query(
        """
        DELETE FROM exam_marks
        WHERE attemptId = :attemptId AND partId = :partId AND pageIndex = :pageIndex
        """,
    )
    suspend fun clearPage(attemptId: Long, partId: Long, pageIndex: Int)

    // --- Wipe --------------------------------------------------------------

    /** Empties the table, for the "erase everything" action in Configuració. */
    @Query("DELETE FROM exams")
    suspend fun clear()
}
