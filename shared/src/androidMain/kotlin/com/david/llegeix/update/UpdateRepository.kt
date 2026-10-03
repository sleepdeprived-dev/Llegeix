package com.david.llegeix.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.SigningInfo
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.david.llegeix.data.settings.SharedPreferencesStore
import java.io.File
import java.security.MessageDigest

/**
 * The phone's updater: the build for this phone's architecture, the version
 * from the package, a downloaded APK checked against this app's signature,
 * and Android's installer to hand it to. See [AppUpdater] for the rest.
 */
class UpdateRepository(private val context: Context) : AppUpdater(
    SharedPreferencesStore(context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)),
) {

    /** The version running now, read from the package rather than from a build flag. */
    override val installedVersion: String
        get() = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()

    override fun buildFor(release: PublishedRelease): ChosenBuild? =
        ReleaseFeed.buildFor(release, Build.SUPPORTED_ABIS.orEmpty().toList())

    override fun downloadFolder(): File = File(context.cacheDir, UPDATE_FOLDER)

    override fun fileNameFor(update: AvailableUpdate): String = "Llegeix-${update.version}-${update.abi}.apk"

    /**
     * Whether [file] really is a newer Llegeix, signed with the key this copy
     * was signed with.
     *
     * Android checks this too, and its check is the one that counts: an APK
     * signed with a different key cannot replace an installed app, whatever
     * this method says. But the installer's refusal comes *after* a dialog has
     * been put in front of the reader, and there is one case where it does not
     * refuse at all — an APK with a *different package name* is not an update
     * being rejected, it is a new app being offered, and the reader is the only
     * thing standing between a compromised release and an installed stranger.
     *
     * So the file is opened and read before any of that. It has to be this
     * package, and it has to be signed by the same certificate. Nothing that
     * fails both tests is worth showing a dialog for, and a reader should not
     * be the check.
     */
    override fun isOurBuild(file: File): Boolean = runCatching {
        val manager = context.packageManager
        val downloaded = archiveInfo(file) ?: return false
        if (downloaded.packageName != context.packageName) return false

        val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }

        val theirs = downloaded.signingInfo?.certificates() ?: return false
        val ours = installed.signingInfo?.certificates() ?: return false
        theirs.isNotEmpty() && theirs == ours
    }.getOrDefault(false)

    /** The signatures an APK on disk actually carries, or null if it carries none. */
    private fun archiveInfo(file: File) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(
                file.path,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(
                file.path,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
        }

    /**
     * The certificates behind a signature, as digests.
     *
     * Digested rather than compared as byte arrays because a certificate is a
     * byte array and byte arrays do not compare by value — a set of them would
     * compare by identity and agree with nothing, including itself.
     */
    private fun SigningInfo.certificates(): Set<String> {
        val signers = if (hasMultipleSigners()) apkContentsSigners else signingCertificateHistory
        return signers.orEmpty().map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { byte -> "%02x".format(byte) }
        }.toSet()
    }

    /** Whether Android will let the app ask to install something. */
    override fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /**
     * Where the reader grants that, which is a screen in system Settings and
     * cannot be a dialog: installing packages is a special access, deliberately
     * made deliberate.
     */
    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData("package:${context.packageName}".toUri())

    /**
     * Hand [file] to Android's package installer.
     *
     * The app's part ends here. What follows is the system's own dialog, which
     * names the app, says it is an update, and is the only thing that can
     * actually install anything.
     */
    fun installIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}$PROVIDER", file)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Browsing to the release itself, for a reader who would rather. */
    fun releasePageIntent(update: AvailableUpdate): Intent =
        Intent(Intent.ACTION_VIEW, update.pageUrl.ifBlank { RELEASES_PAGE }.toUri())

    private companion object {
        const val UPDATE_FOLDER = "updates"

        /** Matches the authority declared for the provider in the manifest. */
        const val PROVIDER = ".updates"

        const val APK_MIME = "application/vnd.android.package-archive"

        /** Its own file: this is bookkeeping about the app, not a preference. */
        const val PREFS_NAME = "llegeix.updates"
    }
}
