package com.david.llegeix.data.exam

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.david.llegeix.data.db.LlegeixDatabase
import com.david.llegeix.data.db.dao.ExamWithProgress
import com.david.llegeix.data.db.entity.ExamAttemptEntity
import com.david.llegeix.data.db.entity.ExamAudioEntity
import com.david.llegeix.data.db.entity.ExamEntity
import com.david.llegeix.data.db.entity.ExamMarkEntity
import com.david.llegeix.data.db.entity.ExamPartEntity
import com.david.llegeix.pdf.PdfiumPageRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/** What went wrong bringing a file in, in terms the screen can say out loud. */
enum class ImportFailure { UNREADABLE, NOT_A_PDF, OUT_OF_SPACE }

/**
 * How an import went, in enough detail for one honest sentence about it.
 *
 * A single import can now be several files, and several files can fail
 * separately — one of five being a Word document is not the import failing. So
 * this counts what landed and what did not, rather than being a yes or a no.
 */
sealed interface ImportResult {

    data class Imported(
        /** The exams created. One when the files were grouped, else one each. */
        val examIds: List<Long>,
        /** The name to say back, when exactly one exam came out of it. */
        val title: String?,
        val fileCount: Int,
        /** Files that could not be brought in, and why the first of them failed. */
        val failedCount: Int,
        val failure: ImportFailure?,
    ) : ImportResult

    /** Nothing landed at all. */
    data class Failed(val reason: ImportFailure) : ImportResult
}

/**
 * The exams the reader has brought in, and the files behind them.
 *
 * This is the only place in the app that copies a document rather than
 * referring to one. See [ExamEntity] for why; the short version is that an
 * attempt is a set of marks on particular pages, and marks whose pages can be
 * deleted by another app are marks the reader will one day come back to and
 * find gone.
 *
 * Everything is kept under one directory the app owns outright. It is private
 * storage, so nothing else on the phone can read it, it is counted against the
 * app in Settings where the reader can see it, and it goes when the app does.
 */
class ExamRepository(
    private val context: Context,
    database: LlegeixDatabase,
) {

    private val exams = database.examDao()

    /**
     * Where the copies live.
     *
     * Under `filesDir` rather than `cacheDir`, and the distinction is not
     * pedantic: the system deletes cache when storage runs short, and an exam
     * whose paper vanished under it would take the reader's answers with it.
     */
    private val directory: File
        get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    fun observeExams(): Flow<List<ExamWithProgress>> = exams.observeExams()

    fun observeAttempts(examId: Long): Flow<List<ExamAttemptEntity>> =
        exams.observeAttempts(examId)

    fun observeAudio(examId: Long): Flow<List<ExamAudioEntity>> = exams.observeAudio(examId)

    fun observeExam(examId: Long): Flow<ExamEntity?> = exams.observeExam(examId)

    fun observeMarks(attemptId: Long, partId: Long, pageIndex: Int): Flow<List<ExamMarkEntity>> =
        exams.observeMarks(attemptId, partId, pageIndex)

    fun observeMarkedPageCount(attemptId: Long): Flow<Int> =
        exams.observeMarkedPageCount(attemptId)

    suspend fun marks(attemptId: Long): List<ExamMarkEntity> = exams.marks(attemptId)

    suspend fun exam(examId: Long): ExamEntity? = exams.exam(examId)

    suspend fun attempt(attemptId: Long): ExamAttemptEntity? = exams.attempt(attemptId)

    fun observeAttempt(attemptId: Long): Flow<ExamAttemptEntity?> =
        exams.observeAttempt(attemptId)

    /** Where a stored copy actually is, for the renderer and the player. */
    fun fileFor(fileName: String): File = File(directory, fileName)

    /**
     * Bring one or more PDFs in as a single paper.
     *
     * Grouping is the reader's call rather than a guess. A Catalan exam sample
     * routinely arrives as several files — the reading, the listening, the
     * writing — which are one paper to whoever sits them, and treating each as
     * its own exam is the thing that was most confusing about this feature: the
     * same name three times in the list, three separate sets of attempts, and
     * no way to say they were one thing.
     *
     * Files land in the order they are given, which is the order the picker
     * returned them in, and that becomes the order the pages run in.
     */
    suspend fun importAsOneExam(uris: List<Uri>, title: String? = null): ImportResult =
        withContext(Dispatchers.IO) {
            if (uris.isEmpty()) return@withContext ImportResult.Failed(ImportFailure.UNREADABLE)

            val copied = ArrayList<CopiedPart>()
            var failure: ImportFailure? = null
            for (uri in uris) {
                when (val part = copyPart(uri)) {
                    is CopyOutcome.Copied -> copied += part.part
                    is CopyOutcome.Failed -> failure = failure ?: part.reason
                }
            }
            if (copied.isEmpty()) {
                return@withContext ImportResult.Failed(failure ?: ImportFailure.UNREADABLE)
            }

            // Sorted by name rather than left in the order the picker handed
            // them over. That order is not defined — it varies by provider and
            // by how the files were tapped — and an exam's files are almost
            // always named to sort into the order they are sat in. It is a
            // guess either way, but a repeatable one, and the paper's own screen
            // can move a document that landed in the wrong place.
            val ordered = copied.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.sourceName })
            val name = title?.trim()?.takeIf { it.isNotEmpty() }
                ?: nameOf(ordered.first().sourceName)
            val examId = exams.insert(ExamEntity(title = name))
            ordered.forEachIndexed { index, part -> insertPart(examId, part, index) }

            ImportResult.Imported(
                examIds = listOf(examId),
                title = name,
                fileCount = copied.size,
                failedCount = uris.size - copied.size,
                failure = failure,
            )
        }

    /** Bring in several PDFs, each as a paper of its own. */
    suspend fun importAsSeparateExams(uris: List<Uri>): ImportResult =
        withContext(Dispatchers.IO) {
            if (uris.isEmpty()) return@withContext ImportResult.Failed(ImportFailure.UNREADABLE)

            val created = ArrayList<Long>()
            var lastName: String? = null
            var failure: ImportFailure? = null
            for (uri in uris) {
                when (val part = copyPart(uri)) {
                    is CopyOutcome.Failed -> failure = failure ?: part.reason
                    is CopyOutcome.Copied -> {
                        val name = nameOf(part.part.sourceName)
                        val examId = exams.insert(ExamEntity(title = name))
                        insertPart(examId, part.part, 0)
                        created += examId
                        lastName = name
                    }
                }
            }
            if (created.isEmpty()) {
                return@withContext ImportResult.Failed(failure ?: ImportFailure.UNREADABLE)
            }
            ImportResult.Imported(
                examIds = created,
                title = lastName.takeIf { created.size == 1 },
                fileCount = created.size,
                failedCount = uris.size - created.size,
                failure = failure,
            )
        }

    /** Add another document to the end of a paper that already exists. */
    suspend fun addParts(examId: Long, uris: List<Uri>): ImportResult =
        withContext(Dispatchers.IO) {
            var position = exams.parts(examId).size
            var added = 0
            var failure: ImportFailure? = null
            for (uri in uris) {
                when (val part = copyPart(uri)) {
                    is CopyOutcome.Failed -> failure = failure ?: part.reason
                    is CopyOutcome.Copied -> {
                        insertPart(examId, part.part, position++)
                        added++
                    }
                }
            }
            if (added == 0) return@withContext ImportResult.Failed(failure ?: ImportFailure.UNREADABLE)
            ImportResult.Imported(
                examIds = listOf(examId),
                title = null,
                fileCount = added,
                failedCount = uris.size - added,
                failure = failure,
            )
        }

    /**
     * Take one document back out of a paper.
     *
     * Its marks go with it, by cascade, which is right: the pages they were
     * written on have gone. The positions of what is left are closed up so the
     * order stays dense — a gap in it is invisible until something sorts on it.
     */
    suspend fun removePart(partId: Long) = withContext(Dispatchers.IO) {
        val part = exams.part(partId) ?: return@withContext
        exams.deletePart(partId)
        File(directory, part.fileName).delete()
        exams.parts(part.examId).forEachIndexed { index, remaining ->
            if (remaining.position != index) exams.setPartPosition(remaining.id, index)
        }
    }

    fun observeParts(examId: Long): Flow<List<ExamPartEntity>> = exams.observeParts(examId)

    /**
     * Move one document up or down the paper.
     *
     * Safe for the reader's work, and that is not an accident: a mark records
     * the document it was made on and its page *within* that document, so
     * reordering changes which page number a page answers to and nothing else.
     * Had marks been numbered across the whole paper, this would have slid every
     * answer after the moved document onto the wrong question.
     */
    suspend fun movePart(partId: Long, delta: Int) = withContext(Dispatchers.IO) {
        val part = exams.part(partId) ?: return@withContext
        val ordered = exams.parts(part.examId).toMutableList()
        val from = ordered.indexOfFirst { it.id == partId }
        val to = from + delta
        if (from < 0 || to !in ordered.indices) return@withContext
        ordered.add(to, ordered.removeAt(from))
        exams.reorderParts(part.examId, ordered.map { it.id })
    }

    suspend fun parts(examId: Long): List<ExamPartEntity> = exams.parts(examId)

    /**
     * Copy a picked file, and check that what landed is really a PDF.
     *
     * The file is copied first and opened afterwards, because the only honest
     * test of whether a paper can be worked on is PDFium opening the very bytes
     * that were stored — not the ones the picker offered, which a cloud provider
     * may still have been streaming. A copy that will not open is deleted again
     * rather than left as a document that fails every time it is touched.
     */
    private suspend fun copyPart(uri: Uri): CopyOutcome {
        val sourceName = displayName(uri) ?: DEFAULT_NAME
        val fileName = "${UUID.randomUUID()}.pdf"
        val destination = File(directory, fileName)

        val copied = runCatching { copy(uri, destination) }
        if (copied.isFailure) {
            destination.delete()
            return CopyOutcome.Failed(
                if (copied.exceptionOrNull() is OutOfSpace) {
                    ImportFailure.OUT_OF_SPACE
                } else {
                    ImportFailure.UNREADABLE
                },
            )
        }

        val pageCount = runCatching {
            PdfiumPageRenderer.open(context, destination.toUri()).use { it.pageCount }
        }.getOrNull()
        if (pageCount == null || pageCount <= 0) {
            destination.delete()
            return CopyOutcome.Failed(ImportFailure.NOT_A_PDF)
        }
        return CopyOutcome.Copied(CopiedPart(sourceName, fileName, pageCount))
    }

    private suspend fun insertPart(examId: Long, part: CopiedPart, position: Int) {
        exams.insert(
            ExamPartEntity(
                examId = examId,
                sourceName = part.sourceName,
                fileName = part.fileName,
                pageCount = part.pageCount,
                position = position,
            ),
        )
    }

    /** A file's name without its extension, which is what a paper gets called. */
    private fun nameOf(sourceName: String): String =
        sourceName.removeSuffix(".pdf").removeSuffix(".PDF").ifBlank { DEFAULT_TITLE }

    private data class CopiedPart(
        val sourceName: String,
        val fileName: String,
        val pageCount: Int,
    )

    private sealed interface CopyOutcome {
        data class Copied(val part: CopiedPart) : CopyOutcome
        data class Failed(val reason: ImportFailure) : CopyOutcome
    }

    /**
     * Attach a recording to an exam.
     *
     * The duration is read once, here, rather than every time the list is
     * drawn: it costs opening the file, and a listening exam can carry a dozen
     * tracks. A file that will not report one is still attached — the player
     * will find out — because refusing a recording over a missing number would
     * be the app being fussier than the reader.
     */
    suspend fun addAudio(examId: Long, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val sourceName = displayName(uri) ?: DEFAULT_AUDIO_NAME
        val extension = sourceName.substringAfterLast('.', "").takeIf { it.isNotEmpty() }
        val fileName = UUID.randomUUID().toString() + extension?.let { ".$it" }.orEmpty()
        val destination = File(directory, fileName)

        if (runCatching { copy(uri, destination) }.isFailure) {
            destination.delete()
            return@withContext false
        }

        exams.insert(
            ExamAudioEntity(
                examId = examId,
                displayName = sourceName,
                fileName = fileName,
                durationMs = durationOf(destination),
                position = exams.audio(examId).size,
            ),
        )
        true
    }

    /**
     * Attach an official answer sheet, replacing any already there.
     *
     * The old key's file is deleted only once the new one has been copied and
     * checked, so a failed import leaves the reader with the key they had
     * rather than with neither.
     */
    suspend fun setAnswerKey(examId: Long, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val previous = exams.exam(examId)?.answerKeyFileName
        val fileName = "${UUID.randomUUID()}.pdf"
        val destination = File(directory, fileName)

        if (runCatching { copy(uri, destination) }.isFailure) {
            destination.delete()
            return@withContext false
        }
        val pageCount = runCatching {
            PdfiumPageRenderer.open(context, destination.toUri()).use { it.pageCount }
        }.getOrNull()
        if (pageCount == null || pageCount <= 0) {
            destination.delete()
            return@withContext false
        }

        exams.setAnswerKey(examId, fileName, pageCount)
        previous?.let { File(directory, it).delete() }
        true
    }

    suspend fun removeAnswerKey(examId: Long) = withContext(Dispatchers.IO) {
        val previous = exams.exam(examId)?.answerKeyFileName
        exams.setAnswerKey(examId, null, null)
        previous?.let { File(directory, it).delete() }
        Unit
    }

    suspend fun removeAudio(audioId: Long, fileName: String) = withContext(Dispatchers.IO) {
        exams.deleteAudio(audioId)
        File(directory, fileName).delete()
        Unit
    }

    // --- Marks -------------------------------------------------------------

    /**
     * Record one mark, and say what it was given.
     *
     * Written the instant it is made rather than batched or saved on exit. The
     * reader asked for work that is "saved continuously and not lost", and the
     * only version of that which survives the app being killed mid-sentence —
     * which is what Android does to a backgrounded app — is a row per stroke,
     * committed as the finger lifts.
     *
     * The sequence number is read inside the same call so two strokes made in
     * quick succession cannot be handed the same one.
     */
    suspend fun addMark(mark: ExamMarkEntity): Long = withContext(Dispatchers.IO) {
        exams.insert(mark.copy(sequence = exams.nextSequence(mark.attemptId)))
    }

    suspend fun updateMark(mark: ExamMarkEntity) = exams.update(mark)

    suspend fun deleteMark(markId: Long) = exams.deleteMark(markId)

    /** Take back the last thing written anywhere in this sitting. */
    suspend fun undoLastMark(attemptId: Long): ExamMarkEntity? = withContext(Dispatchers.IO) {
        exams.lastMark(attemptId)?.also { exams.deleteMark(it.id) }
    }

    suspend fun clearPage(attemptId: Long, partId: Long, pageIndex: Int) =
        exams.clearPage(attemptId, partId, pageIndex)

    /**
     * Start a new sitting of a paper.
     *
     * Numbered from what is already there rather than from the count, so
     * deleting attempt 2 of three does not produce a second attempt 3.
     */
    suspend fun startAttempt(examId: Long, label: String? = null): Long {
        val name = label?.trim()?.takeIf { it.isNotEmpty() }
            ?: "${exams.attemptCount(examId) + 1}"
        return exams.insert(ExamAttemptEntity(examId = examId, label = name))
    }

    suspend fun recordAttemptOpened(attemptId: Long, page: Int) =
        exams.recordOpened(attemptId, page)

    suspend fun setAttemptFinished(attemptId: Long, finished: Boolean) =
        exams.setFinished(attemptId, if (finished) System.currentTimeMillis() else null)

    suspend fun renameAttempt(attemptId: Long, label: String) =
        exams.renameAttempt(attemptId, label.trim())

    suspend fun deleteAttempt(attemptId: Long) = exams.deleteAttempt(attemptId)

    suspend fun renameExam(examId: Long, title: String) = exams.rename(examId, title.trim())

    /**
     * Delete a paper, its sittings, and every file behind them.
     *
     * The file names are read before the rows are deleted, because the cascade
     * takes the audio rows with the exam and those rows are the only record of
     * which files in the directory belonged to it. Getting this the other way
     * round leaves orphaned megabytes nothing will ever clean up.
     */
    suspend fun deleteExam(examId: Long) = withContext(Dispatchers.IO) {
        val exam = exams.exam(examId)
        val files = exams.parts(examId).map { it.fileName } +
            exams.audio(examId).map { it.fileName } +
            listOfNotNull(exam?.answerKeyFileName)
        exams.delete(examId)
        files.forEach { File(directory, it).delete() }
    }

    /** Everything, for the "erase everything" action in Configuració. */
    suspend fun eraseEverything() = withContext(Dispatchers.IO) {
        exams.clear()
        // The whole directory rather than the files just named: anything left
        // in here after the table is empty is by definition an orphan.
        directory.listFiles()?.forEach { it.delete() }
    }

    /**
     * Copy the picked file into the exam directory.
     *
     * Space is discovered rather than checked up front. A provider is not
     * obliged to report a file's size — cloud ones frequently do not — so there
     * is nothing to compare against before starting, and the only reliable
     * moment to learn the phone is full is when the write fails.
     *
     * Which failure it was is then decided by asking the filesystem how much
     * room is left, not by reading the exception's message. Message text is not
     * API: it varies by Android version and by filesystem, and a check for the
     * word "space" is a check that silently stops working. What is left on the
     * volume afterwards is a fact.
     */
    private fun copy(uri: Uri, destination: File) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Could not open $uri")
        input.use { source ->
            destination.outputStream().use { sink ->
                try {
                    source.copyTo(sink)
                } catch (error: IOException) {
                    throw if (directory.usableSpace < LOW_SPACE_BYTES) OutOfSpace() else error
                }
            }
        }
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                    cursor.getString(index)
                } else {
                    null
                }
            }
            ?: uri.lastPathSegment?.substringAfterLast('/')

    private fun durationOf(file: File): Long? = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        }
    }.getOrNull()

    private class OutOfSpace : IOException("No room left for this file")

    private companion object {
        const val DIRECTORY = "exams"
        const val DEFAULT_TITLE = "Exam"

        /**
         * Below this much free space, a failed copy is treated as the disk
         * being full.
         *
         * Generous on purpose. A write does not fail at exactly zero bytes
         * free — the filesystem needs room for its own bookkeeping, and Android
         * starts refusing writes well before the volume is literally full — so
         * a tighter threshold would report "unreadable" for what is plainly a
         * full phone, and send the reader looking for a fault in their file.
         */
        const val LOW_SPACE_BYTES = 32L * 1024 * 1024
        const val DEFAULT_NAME = "Exam.pdf"
        const val DEFAULT_AUDIO_NAME = "Recording"
    }
}
