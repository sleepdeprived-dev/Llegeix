package com.david.llegeix.data.source

import com.david.llegeix.data.model.PdfDocument

/**
 * One folder as the library browser shows it.
 *
 * [documentCount] counts everything below the folder, not only what sits
 * directly inside it, because the question a closed folder has to answer is
 * "is it worth going in", and "3 PDFs" on a folder holding forty of them in
 * subfolders answers it wrongly.
 */
data class LibraryFolder(
    /** Full display path, e.g. `Documents/Català`. */
    val path: String,
    /** Just this folder's own name, which is what the row shows. */
    val name: String,
    val documentCount: Int,
    /** Immediate subfolders, so a row can say there is more structure inside. */
    val folderCount: Int,
)

/**
 * The folders directly inside [path]; a null [path] is the top of the library.
 *
 * Built from the documents rather than by walking storage again: every document
 * already knows the path it was found at, so the folders that matter — the ones
 * with a PDF somewhere below them — are all described by the list itself. A
 * folder holding nothing but other empty folders is not somewhere a reader
 * needs to be offered a trip to.
 */
fun foldersIn(path: String?, documents: List<PdfDocument>): List<LibraryFolder> {
    val prefix = if (path == null) "" else "$path/"
    val counts = HashMap<String, Int>()
    val children = HashMap<String, MutableSet<String>>()

    for (document in documents) {
        val label = document.parentLabel?.takeIf { it.isNotBlank() } ?: continue
        if (label == path) continue
        if (path != null && !label.startsWith(prefix)) continue

        val below = label.removePrefix(prefix)
        if (below.isEmpty()) continue
        val childPath = prefix + below.substringBefore('/')
        counts[childPath] = (counts[childPath] ?: 0) + 1

        val deeper = below.substringAfter('/', "")
        if (deeper.isNotEmpty()) {
            children.getOrPut(childPath) { mutableSetOf() }.add(deeper.substringBefore('/'))
        }
    }

    return counts.map { (folderPath, count) ->
        LibraryFolder(
            path = folderPath,
            name = folderPath.substringAfterLast('/'),
            documentCount = count,
            folderCount = children[folderPath]?.size ?: 0,
        )
    }.sortedBy { it.name.lowercase() }
}

/**
 * The documents sitting in [path] itself rather than in a folder below it.
 *
 * A null [path] is the top of the library, which is where files picked one by
 * one end up: they have no folder on the device that the app can see.
 */
fun documentsIn(path: String?, documents: List<PdfDocument>): List<PdfDocument> =
    documents.filter { document ->
        document.parentLabel?.takeIf { it.isNotBlank() } == path
    }

/**
 * [path] if there is still anything under it, otherwise the nearest folder
 * above it that has something, otherwise the top.
 *
 * A rescan, or unticking a folder on the Sources screen, can empty the folder
 * the reader is currently standing in. Falling back up the path rather than
 * showing an empty screen means the library never becomes a dead end that only
 * the back button gets you out of.
 */
fun nearestLivePath(path: String?, documents: List<PdfDocument>): String? {
    var candidate = path
    while (candidate != null) {
        val prefix = "$candidate/"
        val alive = documents.any { document ->
            val label = document.parentLabel ?: return@any false
            label == candidate || label.startsWith(prefix)
        }
        if (alive) return candidate
        candidate = candidate.substringBeforeLast('/', "").ifEmpty { null }
    }
    return null
}
