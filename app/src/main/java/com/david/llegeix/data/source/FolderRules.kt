package com.david.llegeix.data.source

import com.david.llegeix.data.model.PdfDocument

/**
 * One folder inside a source, as the sources screen shows it.
 *
 * [total] counts everything underneath, [direct] only what sits in this folder
 * itself, because "Documents (0)" next to "Documents (48)" is confusing when
 * both are true of different things.
 */
data class SourceFolder(
    /** Full display path, e.g. `Documents/Català`. */
    val path: String,
    /** Just this folder's own name, for indented display. */
    val name: String,
    /** How deep below the source root this sits; the root itself is 0. */
    val depth: Int,
    val direct: Int,
    val total: Int,
    /** Whether documents here currently reach the library. */
    val visible: Boolean,
    /** True when this folder itself carries the decision, not an ancestor. */
    val decidedHere: Boolean,
)

/**
 * Which folders inside a granted source actually reach the library.
 *
 * A folder with no decision of its own follows the nearest one above it that
 * has one, and a folder with no decision anywhere above it is shown. Two very
 * different requests fall out of that single rule: hiding one subfolder of a
 * big granted folder is one exclusion on the child, and keeping only one
 * subfolder out of a big granted folder is an exclusion on the parent together
 * with an inclusion on the one child.
 *
 * Paths are the display paths the scan already produces, so what is stored is
 * what the reader saw when they decided it.
 */
class FolderRules(private val decisions: Map<String, Boolean>) {

    val isEmpty: Boolean get() = decisions.isEmpty()

    /** Whether a document found at [path] should reach the library. */
    fun allows(path: String?): Boolean {
        if (path.isNullOrEmpty()) return true
        var candidate: String? = path
        while (candidate != null) {
            decisions[candidate]?.let { return it }
            candidate = candidate.substringBeforeLast('/', "").ifEmpty { null }
        }
        return true
    }

    /** True when [path] carries a decision of its own rather than inheriting one. */
    fun decidedAt(path: String): Boolean = path in decisions

    /** Everything the rules currently say, for persisting or inspecting. */
    fun decisions(): Map<String, Boolean> = decisions

    companion object {
        val Empty = FolderRules(emptyMap())

        fun of(pairs: List<Pair<String, Boolean>>): FolderRules = FolderRules(pairs.toMap())
    }
}

/**
 * Keep only the documents the rules allow.
 *
 * Applied after discovery rather than during it, so a folder that is currently
 * hidden still appears in the sources screen with its count and can be turned
 * back on without a rescan.
 */
fun applyFolderRules(
    documents: List<PdfDocument>,
    rules: FolderRules,
): List<PdfDocument> =
    if (rules.isEmpty) documents else documents.filter { rules.allows(it.parentLabel) }

/**
 * The folder tree under [root], built from what the scan already found.
 *
 * Derived from the documents rather than by walking the provider again: every
 * document knows the path it was found at, so the folders that matter — the
 * ones with PDFs somewhere below them — are already described. Folders holding
 * nothing at all are left out on purpose; in a PDF reader they are not a
 * decision anyone needs to make.
 */
fun folderTreeUnder(
    root: String,
    documents: List<PdfDocument>,
    rules: FolderRules,
): List<SourceFolder> {
    val direct = HashMap<String, Int>()
    val total = HashMap<String, Int>()

    for (document in documents) {
        val path = document.parentLabel ?: continue
        if (path != root && !path.startsWith("$root/")) continue
        direct[path] = (direct[path] ?: 0) + 1
        // Credit the document to this folder and to every folder above it.
        var candidate: String? = path
        while (candidate != null) {
            total[candidate] = (total[candidate] ?: 0) + 1
            if (candidate == root) break
            candidate = candidate.substringBeforeLast('/', "").ifEmpty { null }
        }
    }
    if (total.isEmpty()) return emptyList()

    val rootDepth = root.count { it == '/' }
    return total.keys.sortedBy { it.lowercase() }.map { path ->
        SourceFolder(
            path = path,
            name = path.substringAfterLast('/'),
            depth = path.count { it == '/' } - rootDepth,
            direct = direct[path] ?: 0,
            total = total[path] ?: 0,
            visible = rules.allows(path),
            decidedHere = rules.decidedAt(path),
        )
    }
}
