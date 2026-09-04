package com.david.llegeix.update

import org.json.JSONArray
import org.json.JSONObject

/** One built APK attached to a release. */
data class ReleaseAsset(
    val name: String,
    val url: String,
    val bytes: Long,
)

/** A published release, before any particular phone has been considered. */
data class PublishedRelease(
    /** The tag with a leading "v" taken off, so "v3.3" becomes "3.3". */
    val version: String,
    /** What changed, as prose rather than as the markdown it was written in. */
    val notes: String,
    val pageUrl: String,
    val assets: List<ReleaseAsset>,
)

/** The build a particular phone should take, and the architecture it is for. */
data class ChosenBuild(val asset: ReleaseAsset, val abi: String)

/** A release, together with the one build of it this phone can install. */
data class AvailableUpdate(
    val version: String,
    val notes: String,
    val pageUrl: String,
    val downloadUrl: String,
    val downloadBytes: Long,
    /** Named so the screen can say which build it is about to fetch. */
    val abi: String,
)

/**
 * Reading the releases repository's answer.
 *
 * Kept apart from the fetching and the installing because this is the part that
 * decides whether the reader is offered an update at all, and it is all string
 * and list work that can be tested without a device, a network or a signed APK
 * to install.
 *
 * The feed is GitHub's own `releases/latest` for a public repository, which
 * needs no key, no account and no library: it is one unauthenticated GET, and
 * the repository it asks about carries nothing but the builds.
 */
object ReleaseFeed {

    /**
     * The newest release, or null when the answer was not one.
     *
     * Null covers everything from a truncated body to a repository with no
     * releases in it yet. There is nothing useful to tell a reader about which
     * of those it was, and a check that cannot be believed is a check that
     * failed.
     */
    fun parse(body: String): PublishedRelease? {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val tag = json.optString("tag_name").trim()
        if (tag.isEmpty()) return null
        // A draft is not published and a prerelease is not for readers.
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) return null

        return PublishedRelease(
            version = tag.removePrefix("v").removePrefix("V"),
            notes = ReleaseNotes.plain(json.optString("body")),
            pageUrl = json.optString("html_url"),
            assets = assetsIn(json.optJSONArray("assets")),
        )
    }

    private fun assetsIn(array: JSONArray?): List<ReleaseAsset> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val asset = array.optJSONObject(index) ?: return@mapNotNull null
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url")
            if (name.isEmpty() || url.isEmpty()) return@mapNotNull null
            ReleaseAsset(name, url, asset.optLong("size"))
        }
    }

    /**
     * Whether [candidate] is a later version than [installed].
     *
     * Compared as numbers rather than as text, which is the whole reason this
     * is not a string comparison: "3.10" is two releases after "3.9" and sorts
     * before it as characters. Anything that is not a digit is a separator, so
     * a tag written "v3.3" and a version name written "3.3" are the same
     * version.
     */
    fun isNewer(candidate: String, installed: String): Boolean {
        val newer = numbersIn(candidate)
        val current = numbersIn(installed)
        if (newer.isEmpty()) return false
        for (index in 0 until maxOf(newer.size, current.size)) {
            val a = newer.getOrElse(index) { 0 }
            val b = current.getOrElse(index) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun numbersIn(version: String): List<Int> =
        DIGITS.findAll(version).mapNotNull { it.value.toIntOrNull() }.toList()

    /**
     * The build to fetch for a phone reporting [abis], or null when the release
     * carries nothing it can run.
     *
     * [abis] is the device's own preference order — an arm64 phone lists
     * arm64-v8a before the 32-bit build it could also run — so the first match
     * is the right one. The universal build is the fallback rather than the
     * first choice: it carries the native libraries for every architecture and
     * is more than twice the size of the one the phone actually needs.
     *
     * The architecture comes back alongside the file rather than being read off
     * its name afterwards. An architecture is allowed a hyphen in it and two of
     * the four have one, so taking the last piece of "Llegeix-3.4-arm64-v8a.apk"
     * answers "v8a" — which is not an architecture, and which the card would
     * have shown the reader.
     */
    fun buildFor(release: PublishedRelease, abis: List<String>): ChosenBuild? {
        for (abi in abis) {
            release.assets.firstOrNull { it.matches(release.version, abi) }
                ?.let { return ChosenBuild(it, abi) }
        }
        return release.assets.firstOrNull { it.matches(release.version, UNIVERSAL) }
            ?.let { ChosenBuild(it, UNIVERSAL) }
    }

    /**
     * Whether an asset is the build of [version] for [abi].
     *
     * Anchored on the version rather than only on the end of the name, so that
     * the architecture has to be the *whole* of what follows it. Matching the
     * tail alone is looser than it looks: "arm64-v8a" ends with "-v8a", so a
     * device asking for one architecture could be handed the build for another.
     */
    private fun ReleaseAsset.matches(version: String, abi: String) =
        name.endsWith("-$version-$abi$APK", ignoreCase = true)

    private const val UNIVERSAL = "universal"

    private const val APK = ".apk"

    private val DIGITS = Regex("\\d+")
}
