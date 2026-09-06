package com.david.llegeix.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.david.llegeix.data.db.entity.ExamAttemptEntity
import com.david.llegeix.data.db.entity.ExamAudioEntity
import com.david.llegeix.data.db.entity.ExamEntity
import com.david.llegeix.data.db.entity.ExamMarkEntity
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
    val sourceName: String,
    val fileName: String,
    val pageCount: Int,
    val createdAt: Long,
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
        SELECT e.id AS id, e.title AS title, e.sourceName AS sourceName,
               e.fileName AS fileName, e.pageCount AS pageCount,
               e.createdAt AS createdAt,
               e.answerKeyFileName AS answerKeyFileName,
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
        WHERE attemptId = :attemptId AND pageIndex = :pageIndex
        ORDER BY sequence, id
        """,
    )
    fun observeMarks(attemptId: Long, pageIndex: Int): Flow<List<ExamMarkEntity>>

    /** Every mark in a sitting, for export and for comparing two sittings. */
    @Query("SELECT * FROM exam_marks WHERE attemptId = :attemptId ORDER BY pageIndex, sequence, id")
    suspend fun marks(attemptId: Long): List<ExamMarkEntity>

    /** How many pages of this sitting have anything on them at all. */
    @Query("SELECT COUNT(DISTINCT pageIndex) FROM exam_marks WHERE attemptId = :attemptId")
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

    @Query("DELETE FROM exam_marks WHERE attemptId = :attemptId AND pageIndex = :pageIndex")
    suspend fun clearPage(attemptId: Long, pageIndex: Int)

    // --- Wipe --------------------------------------------------------------

    /**
     * Every file the app is holding, so the wipe can delete the copies too.
     *
     * Read before the rows go, because once the table is empty there is nothing
     * left to say which files in the exam directory were ever ours.
     */
    @Query("SELECT fileName FROM exams")
    suspend fun allExamFileNames(): List<String>

    @Query("SELECT fileName FROM exam_audio")
    suspend fun allAudioFileNames(): List<String>

    /** Empties the table, for the "erase everything" action in Configuració. */
    @Query("DELETE FROM exams")
    suspend fun clear()
}
