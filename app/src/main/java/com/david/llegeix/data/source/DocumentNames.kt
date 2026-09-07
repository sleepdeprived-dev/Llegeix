package com.david.llegeix.data.source

import com.david.llegeix.util.pdfTitle

/**
 * What every PDF in the app is called.
 *
 * Renaming a document is one fact about it, and it has to be *one* fact: a PDF
 * renamed in a collection and still called something else in the library is
 * worse than no renaming at all, because the reader now has two names for one
 * book and no way to tell which list is lying. That is exactly what happened
 * when each screen looked the name up for itself and half of them did not
 * bother — the shelf of part-read books, the marked pages, the line under a
 * saved word, and the search box all went on using the filename.
 *
 * So the lookup is a type rather than a `Map` passed around by convention. A
 * screen that has one of these can only get the answer right; a screen that
 * needs one cannot silently do without it, because there is nothing else to
 * call.
 *
 * [titleFor] is the whole interface, and it takes the filename as the fallback
 * rather than looking it up, because the caller always has it — the fallback is
 * the file's own name with its extension trimmed, which is what the app showed
 * before any of this existed.
 */
@JvmInline
value class DocumentNames(private val byUri: Map<String, String>) {

    /** What to call the document at [uriString]. */
    fun titleFor(uriString: String, displayName: String): String =
        byUri[uriString] ?: pdfTitle(displayName)

    /** Whether the reader has given this one a name of their own. */
    fun isRenamed(uriString: String): Boolean = uriString in byUri

    /**
     * The reader's name for this document, if there is one.
     *
     * Used by the search, which has to match what is on screen: a book renamed
     * "Petit Príncep" and then searched for by that name found nothing, because
     * the filter only ever saw the filename.
     */
    fun customName(uriString: String): String? = byUri[uriString]

    companion object {
        val Empty = DocumentNames(emptyMap())
    }
}
