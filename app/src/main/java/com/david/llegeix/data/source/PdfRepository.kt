package com.david.llegeix.data.source

import android.content.Context
import com.david.llegeix.data.settings.SettingsRepository
import androidx.core.net.toUri
import com.david.llegeix.platform.ContentRef
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Single entry point the UI uses to find PDFs, hiding the fact that there are
 * two discovery mechanisms behind it.
 *
 * Feature 3 will add a Room-backed layer alongside this for folders, bookmarks
 * and history; this class stays responsible only for what is physically on the
 * device.
 */
class PdfRepository(
    private val safSource: SafPdfSource,
    private val deviceSource: MediaStorePdfSource,
    private val pickedFileSource: PickedFilePdfSource,
    /**
     * Consulted only for whether the reader wants the whole-device sweep run.
     * Null in tests, where the sweep is always allowed to run.
     */
    private val settings: SettingsRepository? = null,
) : PdfLibrary {

    constructor(context: Context, settings: SettingsRepository) : this(
        SafPdfSource(context.applicationContext),
        MediaStorePdfSource(context.applicationContext),
        PickedFilePdfSource(context.applicationContext),
        settings,
    )

    override fun grantedFolders(): List<GrantedFolder> = safSource.grantedFolders()

    /** Whether Android currently allows the whole-device sweep at all. */
    override fun isDeviceScanPermitted(): Boolean = deviceSource.isAvailable()

    override fun isDeviceScanEnabled(): Boolean =
        isDeviceScanPermitted() && settings?.current?.deviceScanOptOut != true

    override fun addFolder(folder: ContentRef) = safSource.persistGrant(folder.uri)

    override fun removeFolder(treeUri: String) = safSource.releaseGrant(treeUri.toUri())

    override fun addPickedFile(file: ContentRef) = pickedFileSource.persistGrant(file.uri)

    override fun pickedUris(): Set<String> = pickedFileSource.grantedUris().toSet()

    /**
     * Run both sources and merge them. The two run concurrently because the
     * device sweep is by far the slower of the pair and there is no reason to
     * make the folder walk wait behind it.
     */
    override suspend fun loadLibrary(): LibrarySnapshot = coroutineScope {
        val fromFolders = async { safSource.findPdfs() }
        val fromDevice = async {
            if (isDeviceScanEnabled()) deviceSource.findPdfs() else emptyList()
        }
        val fromPicked = async { pickedFileSource.findPdfs() }

        val merged = mergePdfDocuments(
            fromFolders.await() + fromPicked.await(),
            fromDevice.await(),
        )
        LibrarySnapshot(
            documents = merged,
            grantedFolders = safSource.grantedFolders(),
            deviceScanEnabled = isDeviceScanEnabled(),
        )
    }
}
