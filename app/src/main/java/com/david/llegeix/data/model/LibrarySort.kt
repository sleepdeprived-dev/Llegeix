package com.david.llegeix.data.model

import com.david.llegeix.resources.*
import org.jetbrains.compose.resources.StringResource



/**
 * How the library list is ordered.
 *
 * Three names apiece, and each earns its keep. [labelRes] is what the order is
 * called in the sheet where it is chosen; [shortLabelRes] is what fits on the
 * chip that sits in the library saying which order is in force, because
 * "Recently modified" is a sentence and a chip has room for a word; and
 * [summaryRes] is the line under the name in the sheet, which says what the
 * order actually does rather than making the reader try it to find out.
 */
enum class LibrarySort(
    val labelRes: StringResource,
    val shortLabelRes: StringResource,
    val summaryRes: StringResource,
) {
    RECENT(
        Res.string.library_sort_recent,
        Res.string.library_sort_recent_short,
        Res.string.library_sort_recent_summary,
    ),
    NAME(
        Res.string.library_sort_name,
        Res.string.library_sort_name,
        Res.string.library_sort_name_summary,
    ),
    SIZE(
        Res.string.library_sort_size,
        Res.string.library_sort_size,
        Res.string.library_sort_size_summary,
    ),

    /**
     * Groups documents under their first tag, alphabetically, with untagged
     * ones last.
     *
     * A document can carry several tags, so "sorted by tag" has to pick one to
     * sort on; the first alphabetically is the one the list already shows, so
     * the order matches what is on screen rather than something invisible.
     */
    TAG(
        Res.string.library_sort_tag,
        Res.string.library_sort_tag,
        Res.string.library_sort_tag_summary,
    ),
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
