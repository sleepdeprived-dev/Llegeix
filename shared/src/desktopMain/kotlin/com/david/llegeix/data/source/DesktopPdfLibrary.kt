package com.david.llegeix.data.source

import com.david.llegeix.data.model.PdfDocument
import com.david.llegeix.data.model.PdfOrigin
import com.david.llegeix.data.settings.SettingsRepository
import com.david.llegeix.data.settings.SettingsStore
import com.david.llegeix.data.settings.javaPrefsStore
import com.david.llegeix.platform.ContentRef
import com.david.llegeix.platform.desktopPrefsRoot
import com.david.llegeix.platform.logWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** The Mac's library, its choices kept beside the app's other preferences. */
fun desktopPdfLibrary(settings: SettingsRepository): DesktopPdfLibrary =
    DesktopPdfLibrary(javaPrefsStore(desktopPrefsRoot.node(DesktopPdfLibrary.PREFS_NAME)), settings)

/**
 * Where the Mac's PDFs come from: the phone's three ways in, in the Mac's terms.
 *
 * - Folders the reader chose in the open panel, walked as they are — the
 *   phone's granted folder trees. Each is labelled with its own name, and the
 *   folders inside it hang under that, as the phone's do.
 * - Files chosen one by one, kept by path.
 * - The sweep of everything, which on the phone is Android's media index: on
 *   the Mac it is Spotlight's, asked for every PDF under the reader's home
 *   folder. Spotlight needs no permission, so it is only ever on because the
 *   reader turned it on ([allowDeviceScan]), and Configuració's switch turns
 *   it off again as it does on the phone.
 *
 * Every document's handle is its file URI.
 */
class DesktopPdfLibrary internal constructor(
    private val prefs: SettingsStore,
    private val settings: SettingsRepository?,
    private val home: File = File(System.getProperty("user.home")),
    /** Every PDF Spotlight knows of under a folder; replaced in tests. */
    private val spotlight: (File) -> List<File> = ::spotlightPdfs,
) : PdfLibrary {

    override fun grantedFolders(): List<GrantedFolder> =
        paths(KEY_FOLDERS).map { File(it) }.map { GrantedFolder(it.toURI().toString(), it.name) }

    override fun isDeviceScanPermitted(): Boolean = prefs.getBoolean(KEY_DEVICE_SCAN, false)

    override fun isDeviceScanEnabled(): Boolean =
        isDeviceScanPermitted() && settings?.current?.deviceScanOptOut != true

    /** The reader asked for the sweep: the Mac's equivalent of granting All Files Access. */
    fun allowDeviceScan() {
        prefs.putBoolean(KEY_DEVICE_SCAN, true)
        settings?.setDeviceScanOptOut(false)
    }

    /** Forget every folder and file chosen, and the leave to sweep, for "erase everything". */
    fun forgetAll() = prefs.clear()

    override fun addFolder(folder: ContentRef) {
        if (folder.file.isDirectory) add(KEY_FOLDERS, folder.file.absolutePath)
    }

    override fun removeFolder(treeUri: String) {
        val path = runCatching { File(URI(treeUri)).absolutePath }.getOrNull() ?: return
        write(KEY_FOLDERS, paths(KEY_FOLDERS) - path)
    }

    override fun addPickedFile(file: ContentRef) {
        if (isPdf(file.file.name, null)) add(KEY_FILES, file.file.absolutePath)
    }

    override fun pickedUris(): Set<String> = paths(KEY_FILES).mapTo(HashSet()) { File(it).toURI().toString() }

    override suspend fun loadLibrary(): LibrarySnapshot = coroutineScope {
        val fromFolders = async(Dispatchers.IO) { grantedFolders().flatMap { walk(File(URI(it.treeUri)), it.label) } }
        val fromPicked = async(Dispatchers.IO) { paths(KEY_FILES).mapNotNull { picked(File(it)) } }
        val fromDevice = async(Dispatchers.IO) { if (isDeviceScanEnabled()) swept() else emptyList() }
        LibrarySnapshot(
            documents = mergePdfDocuments(fromFolders.await() + fromPicked.await(), fromDevice.await()),
            grantedFolders = grantedFolders(),
            deviceScanEnabled = isDeviceScanEnabled(),
        )
    }

    /**
     * Every PDF under [root], breadth first. Hidden folders are left alone, as
     * the Finder leaves them, and so are links to folders, which can loop; the
     * walk stops at [MAX_DIRECTORIES] so choosing the whole disk cannot hang it.
     */
    private fun walk(root: File, label: String): List<PdfDocument> {
        if (!root.isDirectory) return emptyList()
        val found = ArrayList<PdfDocument>()
        val queue = ArrayDeque(listOf(root to label))
        var visited = 0
        while (queue.isNotEmpty()) {
            if (++visited > MAX_DIRECTORIES) {
                logWarning(TAG, "Stopped walking $label at $MAX_DIRECTORIES folders")
                break
            }
            val (folder, path) = queue.removeFirst()
            val children = folder.listFiles() ?: continue
            for (child in children.sortedBy { it.name.lowercase() }) {
                if (child.isHidden) continue
                when {
                    child.isDirectory && !Files.isSymbolicLink(child.toPath()) ->
                        queue.addLast(child to "$path/${child.name}")
                    child.isFile && isPdf(child.name, null) ->
                        found += document(child, PdfOrigin.GRANTED_FOLDER, path)
                }
            }
        }
        return found
    }

    /** A file chosen on its own, under the name of the folder it is in. */
    private fun picked(file: File): PdfDocument? =
        if (file.isFile) document(file, PdfOrigin.PICKED_FILE, file.parentFile?.name) else null

    /**
     * Spotlight's PDFs, placed by their folder under home ("Documents/Català")
     * as the phone places the media index's. Nothing from ~/Library, nothing
     * hidden, and none of the app's own files.
     */
    private fun swept(): List<PdfDocument> {
        val library = File(home, "Library").absolutePath + File.separator
        return spotlight(home).filter { file ->
            val path = file.absolutePath
            !path.startsWith(library) &&
                path.startsWith(home.absolutePath + File.separator) &&
                path.removePrefix(home.absolutePath).split(File.separatorChar).none { it.startsWith(".") } &&
                file.isFile
        }.map { file ->
            val folder = file.parentFile.absolutePath.removePrefix(home.absolutePath).trim(File.separatorChar)
            document(file, PdfOrigin.DEVICE_SCAN, folder.ifEmpty { null })
        }
    }

    private fun document(file: File, origin: PdfOrigin, parentLabel: String?) = PdfDocument(
        uriString = file.toURI().toString(),
        displayName = file.name,
        sizeBytes = file.length(),
        lastModified = file.lastModified(),
        origin = origin,
        parentLabel = parentLabel,
    )

    private fun paths(key: String): List<String> =
        prefs.getString(key, null)?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()

    private fun add(key: String, path: String) {
        val current = paths(key)
        if (path !in current) write(key, current + path)
    }

    private fun write(key: String, paths: List<String>) = prefs.putString(key, paths.joinToString(SEPARATOR))

    companion object {
        const val PREFS_NAME = "llegeix.library"
        private const val KEY_FOLDERS = "folders"
        private const val KEY_FILES = "files"
        private const val KEY_DEVICE_SCAN = "deviceScan"

        /**
         * A Mac path can hold a newline, but no folder anybody keeps PDFs in
         * does, and the preferences store refuses the one character no path
         * can hold.
         */
        private const val SEPARATOR = "\n"

        private const val MAX_DIRECTORIES = 5_000
        private const val TAG = "DesktopPdfLibrary"
    }
}

/** Every PDF Spotlight has indexed under [folder]; empty if it cannot be asked. */
private fun spotlightPdfs(folder: File): List<File> = runCatching {
    val process = ProcessBuilder("mdfind", "-0", "-onlyin", folder.absolutePath, "kMDItemContentType == 'com.adobe.pdf'")
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start()
    val output = process.inputStream.readBytes().decodeToString()
    if (!process.waitFor(SPOTLIGHT_TIMEOUT_S, TimeUnit.SECONDS)) process.destroy()
    output.split('\u0000').filter { it.isNotBlank() }.map(::File)
}.getOrElse { error ->
    logWarning("DesktopPdfLibrary", "Spotlight could not be asked: ${error.message}")
    emptyList()
}

private const val SPOTLIGHT_TIMEOUT_S = 30L
