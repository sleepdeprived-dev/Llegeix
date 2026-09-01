package com.david.llegeix.data.source

import com.david.llegeix.data.model.PdfDocument

/**
 * One way of finding PDFs on the device.
 *
 * Both implementations are cheap to construct and do all their work in
 * [findPdfs], which is expected to be called off the main thread.
 */
interface PdfSource {

    /** True when this source currently has whatever access it needs to run. */
    fun isAvailable(): Boolean

    /**
     * Enumerate every PDF this source can see. Implementations must return an
     * empty list rather than throwing when access has been revoked, since the
     * user can withdraw a grant at any time from system Settings.
     */
    suspend fun findPdfs(): List<PdfDocument>
}
