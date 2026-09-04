package com.david.llegeix.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.david.llegeix.util.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** What a check for updates came back with. */
sealed interface UpdateCheck {

    /** The newest release is the one already installed. */
    data object UpToDate : UpdateCheck

    data class Available(val update: AvailableUpdate) : UpdateCheck

    /**
     * The check did not happen, and why.
     *
     * Named cases rather than a message, because the screen has to say
     * something a reader can act on and the ViewModel is where the app's own
     * language lives. "No connection" and "the repository would not answer" ask
     * for different things of the person reading them.
     */
    data class Trouble(val reason: Reason) : UpdateCheck

    enum class Reason {
        /** No network, or it went away mid-request. */
        OFFLINE,

        /** GitHub's hourly allowance for anonymous callers is spent. */
        RATE_LIMITED,

        /** A release exists but carries no build this phone could run. */
        NO_BUILD,

        /** Anything else: an error status, a body that was not a release. */
        UNREADABLE,
    }
}

/**
 * Checking for, fetching and handing over a new version of the app.
 *
 * Llegeix is sideloaded rather than installed from a store, so nothing updates
 * it on its own. This is the whole of what replaces that, and it is deliberately
 * only as much as it needs to be:
 *
 *  - **Nothing happens unless it is asked for.** No background poll, no check on
 *    launch, no notification. The app makes one network call of its own, when
 *    the button in Configuració is pressed.
 *  - **Nothing is installed without Android's own dialog.** The APK is handed to
 *    the system installer, which asks. The app cannot install it and does not
 *    try; the permission it holds is only the right to ask.
 *  - **Nothing is sent.** The request carries no identifier of any kind — it is
 *    the same unauthenticated GET anybody can make of a public repository.
 *
 * The releases repository is separate from the source, and public so that this
 * call needs no key. An API token shipped inside a sideloaded APK is a token
 * anybody with the APK has.
 */
class UpdateRepository(private val context: Context) {

    /** The version running now, read from the package rather than from a build flag. */
    val installedVersion: String
        get() = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()

    suspend fun check(): UpdateCheck = withContext(Dispatchers.IO) {
        val response = fetch(LATEST_RELEASE_URL)
        val body = when (response) {
            is Response.Body -> response.text
            is Response.Trouble -> return@withContext UpdateCheck.Trouble(response.reason)
        }

        val release = ReleaseFeed.parse(body)
            ?: return@withContext UpdateCheck.Trouble(UpdateCheck.Reason.UNREADABLE)
        if (!ReleaseFeed.isNewer(release.version, installedVersion)) {
            return@withContext UpdateCheck.UpToDate
        }

        val build = ReleaseFeed.buildFor(release, Build.SUPPORTED_ABIS.orEmpty().toList())
            ?: return@withContext UpdateCheck.Trouble(UpdateCheck.Reason.NO_BUILD)

        UpdateCheck.Available(
            AvailableUpdate(
                version = release.version,
                notes = release.notes,
                pageUrl = release.pageUrl,
                downloadUrl = build.asset.url,
                downloadBytes = build.asset.bytes,
                abi = build.abi,
            ),
        )
    }

    /**
     * Fetch the build, reporting how far along it is.
     *
     * Into the app's own cache rather than the public Downloads folder: this is
     * a file the app made for itself and should clear up after itself, and a
     * reader's Downloads folder is not the place to leave a 70 MB APK they did
     * not ask to keep. Everything already there goes first, so a failed or
     * abandoned attempt cannot accumulate.
     */
    suspend fun download(
        update: AvailableUpdate,
        onProgress: (Float) -> Unit,
    ): File? = withContext(Dispatchers.IO) {
        val folder = File(context.cacheDir, UPDATE_FOLDER)
        folder.deleteRecursively()
        if (!folder.mkdirs()) return@withContext null
        val target = File(folder, "Llegeix-${update.version}-${update.abi}.apk")

        val done = runCatchingCancellable {
            val connection = open(update.downloadUrl)
            try {
                if (connection.responseCode !in 200..299) return@runCatchingCancellable false
                // What the server says, falling back to what the release said,
                // so the bar still moves when the redirect drops the length.
                val total = connection.contentLengthLong.takeIf { it > 0 }
                    ?: update.downloadBytes
                var written = 0L
                var reported = -1

                connection.inputStream.use { source ->
                    target.outputStream().use { sink ->
                        val buffer = ByteArray(DOWNLOAD_BUFFER)
                        while (true) {
                            // A reader who left the screen is a download that
                            // should stop, not one that finishes into a file
                            // nobody will be offered.
                            currentCoroutineContext().ensureActive()
                            val read = source.read(buffer)
                            if (read < 0) break
                            sink.write(buffer, 0, read)
                            written += read
                            if (total > 0) {
                                // Reported by whole percent. A callback per 32 kB
                                // is a state update per 32 kB, and a progress bar
                                // cannot show more than a percent anyway.
                                val percent = (written * 100 / total).toInt()
                                if (percent != reported) {
                                    reported = percent
                                    onProgress(percent / 100f)
                                }
                            }
                        }
                    }
                }
                // A truncated APK installs as a corrupt one, so a download that
                // does not account for itself is thrown away rather than offered.
                total <= 0 || written == total
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(false)

        if (done) {
            target
        } else {
            target.delete()
            null
        }
    }

    /** Whether Android will let the app ask to install something. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

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

    // ---- Talking to the releases repository -------------------------------

    private sealed interface Response {
        data class Body(val text: String) : Response
        data class Trouble(val reason: UpdateCheck.Reason) : Response
    }

    private fun fetch(url: String): Response {
        val connection = runCatching { open(url) }.getOrNull()
            ?: return Response.Trouble(UpdateCheck.Reason.OFFLINE)
        return try {
            when (val code = connection.responseCode) {
                in 200..299 -> Response.Body(
                    connection.inputStream.bufferedReader().use { it.readText() },
                )
                // GitHub answers a spent allowance with 403 and a header saying
                // so. Told apart from a plain refusal because it is the one
                // failure that fixes itself, and saying "try again later" is
                // only honest when later is actually different.
                403, 429 -> if (connection.getHeaderField(RATE_LIMIT_REMAINING) == "0") {
                    Response.Trouble(UpdateCheck.Reason.RATE_LIMITED)
                } else {
                    Response.Trouble(UpdateCheck.Reason.UNREADABLE)
                }
                else -> Response.Trouble(
                    if (code >= 500) {
                        UpdateCheck.Reason.OFFLINE
                    } else {
                        UpdateCheck.Reason.UNREADABLE
                    },
                )
            }
        } catch (error: IOException) {
            Response.Trouble(UpdateCheck.Reason.OFFLINE)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            // GitHub refuses a request with no user agent outright, and the
            // Accept header pins the response shape to the documented one.
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/vnd.github+json")
        }

    private companion object {
        /**
         * The public repository holding the builds.
         *
         * Separate from the source, which is private: a check has to be
         * answerable without credentials, and the only credential that could
         * live in a sideloaded APK is one everybody holding the APK has.
         */
        const val RELEASES_REPO = "sleepdeprived-dev/Llegeix-releases"

        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/$RELEASES_REPO/releases/latest"

        const val RELEASES_PAGE = "https://github.com/$RELEASES_REPO/releases"

        const val USER_AGENT = "Llegeix"

        const val RATE_LIMIT_REMAINING = "x-ratelimit-remaining"

        const val TIMEOUT_MS = 15_000

        const val UPDATE_FOLDER = "updates"

        /** Matches the authority declared for the provider in the manifest. */
        const val PROVIDER = ".updates"

        const val APK_MIME = "application/vnd.android.package-archive"

        const val DOWNLOAD_BUFFER = 32 * 1024
    }
}
