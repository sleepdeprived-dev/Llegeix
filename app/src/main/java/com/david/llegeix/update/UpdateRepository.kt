package com.david.llegeix.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.SigningInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.david.llegeix.util.runCatchingCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

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

    /** Serialises downloads; see the note in [download]. */
    private val downloadLock = Mutex()

    private val prefs by lazy {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Whether a newer version is known to be waiting, for the dot on the gear.
     *
     * Seeded from disk so the dot is on the very first frame after a launch
     * rather than appearing a second later, and cleared the moment a check
     * finds the newest release is the one already running — including the
     * check that happens right after the reader installs it.
     */
    private val _updateWaiting = MutableStateFlow(waitingVersion() != null)
    val updateWaiting: StateFlow<Boolean> = _updateWaiting.asStateFlow()

    /**
     * Look for a new version without being asked, at most once a day.
     *
     * This is the one thing the app does on the network that nobody pressed a
     * button for, and it is deliberately the smallest version of it that can
     * work. There is no background service, no job scheduler, no notification
     * and no wake-up: it runs when the library comes to the foreground, which
     * is to say only while the reader is already looking at the app, and then
     * not again for a day. Nothing is downloaded and nothing is installed —
     * the whole result is a red dot on the settings gear, which the reader can
     * ignore for as long as they like.
     *
     * The request is the same anonymous GET the button makes. It carries no
     * identifier, and a phone that is offline simply learns nothing and tries
     * again tomorrow.
     */
    suspend fun checkQuietly() {
        val now = System.currentTimeMillis()
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
        if (now - last in 0 until QUIET_CHECK_INTERVAL_MS) return
        // Written before the call rather than after it, so a phone with no
        // connection does not retry on every single trip to the foreground.
        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
        when (val answer = check()) {
            is UpdateCheck.Available -> rememberWaiting(answer.update.version)
            is UpdateCheck.UpToDate -> rememberWaiting(null)
            // An answer that never arrived says nothing about what is out
            // there, so whatever was known before stands.
            is UpdateCheck.Trouble -> Unit
        }
    }

    /** Record what the last check found, so the dot survives the app closing. */
    fun rememberWaiting(version: String?) {
        prefs.edit().apply {
            if (version == null) remove(KEY_WAITING) else putString(KEY_WAITING, version)
        }.apply()
        _updateWaiting.value = version != null
    }

    private fun waitingVersion(): String? = prefs.getString(KEY_WAITING, null)
        ?.takeIf { ReleaseFeed.isNewer(it, installedVersion) }

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
        // One at a time. The screen cancels the previous download before
        // starting another, but cancellation is cooperative and only noticed
        // between reads: without this, the job on its way out can clear the
        // folder — and delete the file — of the job that replaced it, which
        // ends in an install of a file that is no longer there.
        downloadLock.withLock {
            val folder = File(context.cacheDir, UPDATE_FOLDER)
            folder.deleteRecursively()
            if (!folder.mkdirs()) return@withLock null
            val target = File(folder, "Llegeix-${update.version}-${update.abi}.apk")
            // The version is a dotted number by the time it gets here, checked
            // where it was parsed, and this is the line that says so out loud.
            // A path built out of anything off the network is worth asserting
            // about in the place it is built, not only in the place it is
            // validated: the two are a file apart and only one of them is
            // obviously security-relevant to read.
            if (target.canonicalFile.parentFile != folder.canonicalFile) return@withLock null

            val done = try {
                runCatchingCancellable { fetchTo(update, target, onProgress) }.getOrDefault(false)
            } catch (cancelled: CancellationException) {
                // Deleted on the way out as well as on failure. Cancelling
                // leaves whatever had arrived so far on disk, and half an APK
                // in the cache is a file with no purpose and a misleading name.
                target.delete()
                throw cancelled
            }

            if (done) {
                target
            } else {
                target.delete()
                null
            }
        }
    }

    private suspend fun fetchTo(
        update: AvailableUpdate,
        target: File,
        onProgress: (Float) -> Unit,
    ): Boolean {
        // Checked again here rather than trusted from the check that offered
        // it: this is the call that actually reaches out, and it is one line.
        if (!ReleaseFeed.isTrustedUrl(update.downloadUrl)) return false
        val connection = open(update.downloadUrl)
        try {
            if (connection.responseCode !in 200..299) return false
            // Where the redirects actually landed. HttpURLConnection will not
            // follow a downgrade to cleartext, so this should be impossible;
            // it is asserted because "should be impossible" is doing a lot of
            // work in a sentence about installing an APK.
            if (!connection.url.protocol.equals("https", ignoreCase = true)) return false

            // What the server says, falling back to what the release said,
            // so the bar still moves when the redirect drops the length.
            val total = connection.contentLengthLong.takeIf { it > 0 }
                ?: update.downloadBytes
            // A length nobody could mean. The universal build is the largest
            // this app produces at about 160 MB; anything past the cap is a
            // mistake or a cache being filled on purpose, and either way it is
            // not an update.
            if (total > MAX_DOWNLOAD_BYTES) return false
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
                        // The cap again, against the bytes rather than the
                        // header: a server is free to understate what it is
                        // about to send.
                        if (written > MAX_DOWNLOAD_BYTES) return false
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
            return total <= 0 || written == total
        } finally {
            connection.disconnect()
        }
    }

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
    fun isOurBuild(file: File): Boolean = runCatching {
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
                    // Capped. A release document is a few kilobytes; anything
                    // claiming to be more than this is not one, and reading a
                    // stream of unknown length straight into a String is how a
                    // network reply becomes an out-of-memory crash.
                    //
                    // Read in a loop, because a Reader returns what it has
                    // rather than what was asked for: a single read() of a
                    // large buffer comes back with the first chunk and nothing
                    // more, which for a JSON document means half a document.
                    connection.inputStream.bufferedReader().use { reader ->
                        val text = StringBuilder()
                        val chunk = CharArray(FEED_CHUNK_CHARS)
                        while (text.length < MAX_FEED_CHARS) {
                            val read = reader.read(chunk)
                            if (read < 0) break
                            text.append(chunk, 0, minOf(read, MAX_FEED_CHARS - text.length))
                        }
                        text.toString()
                    },
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

        /** A release document is a few kilobytes; this is room to spare. */
        const val MAX_FEED_CHARS = 512 * 1024

        const val FEED_CHUNK_CHARS = 8 * 1024

        /**
         * The largest build this app will accept.
         *
         * The universal APK, the biggest thing a release carries, is about
         * 160 MB. Twice that is room for the app to grow and still a refusal
         * for anything that could only be an attempt to fill the cache.
         */
        const val MAX_DOWNLOAD_BYTES = 320L * 1024 * 1024

        /** Its own file: this is bookkeeping about the app, not a preference. */
        const val PREFS_NAME = "llegeix.updates"

        const val KEY_LAST_CHECK = "last_quiet_check"

        /** The version the last check found, so the dot survives a restart. */
        const val KEY_WAITING = "waiting_version"

        /**
         * How long a quiet check waits before it is allowed to run again.
         *
         * A day. Llegeix is released when there is something to release, not on
         * a schedule, so asking more often would be one more request a day for
         * an answer that changes a handful of times a year — and the dot is not
         * urgent enough to be worth a single extra one.
         */
        const val QUIET_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
    }
}
