package com.david.llegeix.data.model

import androidx.annotation.StringRes
import com.david.llegeix.R

/** How the library list is ordered. */
enum class LibrarySort(@param:StringRes val labelRes: Int) {
    RECENT(R.string.library_sort_recent),
    NAME(R.string.library_sort_name),
    SIZE(R.string.library_sort_size),

    /**
     * Groups documents under their first tag, alphabetically, with untagged
     * ones last.
     *
     * A document can carry several tags, so "sorted by tag" has to pick one to
     * sort on; the first alphabetically is the one the list already shows, so
     * the order matches what is on screen rather than something invisible.
     */
    TAG(R.string.library_sort_tag),
    ;

    /**
     * @param tagNameOf the first tag on a document, or null when it has none.
     *   Only consulted by [TAG]; the other orders ignore it.
     */
    fun comparator(
        tagNameOf: (PdfDocument) -> String? = { null },
    ): Comparator<PdfDocument> = when (this) {
        TAG -> compareBy<PdfDocument> { tagNameOf(it) == null }
            .thenBy { tagNameOf(it)?.lowercase() ?: "" }
            .thenBy { it.displayName.lowercase() }

        else -> baseComparator()
    }

    private fun baseComparator(): Comparator<PdfDocument> = when (this) {
        RECENT -> compareByDescending<PdfDocument> { it.lastModified }
            .thenBy { it.displayName.lowercase() }

        NAME -> compareBy<PdfDocument> { it.displayName.lowercase() }
            .thenByDescending { it.lastModified }

        SIZE -> compareByDescending<PdfDocument> { it.sizeBytes }
            .thenBy { it.displayName.lowercase() }

        // Handled by comparator(); never reached.
        TAG -> compareBy { it.displayName.lowercase() }
    }
}
