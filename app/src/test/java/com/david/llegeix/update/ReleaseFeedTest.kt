package com.david.llegeix.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading the releases repository's answer.
 *
 * This is the part that decides whether a reader is told an update exists, and
 * both ways of being wrong are bad in their own way: missing a release means an
 * app that quietly stops being updated, and offering one that is not there means
 * a download that fails or, worse, an install of the wrong architecture.
 */
class ReleaseFeedTest {

    /** Trimmed from the real answer for this repository. */
    private val feed = """
        {
          "tag_name": "v3.4",
          "name": "Llegeix v3.4",
          "draft": false,
          "prerelease": false,
          "html_url": "https://github.com/sleepdeprived-dev/Llegeix-releases/releases/tag/v3.4",
          "body": "## Per què\n\nUna **cosa** nova.\n",
          "assets": [
            {
              "name": "Llegeix-3.4-arm64-v8a.apk",
              "size": 70546902,
              "browser_download_url": "https://github.com/x/releases/download/v3.4/Llegeix-3.4-arm64-v8a.apk"
            },
            {
              "name": "Llegeix-3.4-armeabi-v7a.apk",
              "size": 59716208,
              "browser_download_url": "https://github.com/x/releases/download/v3.4/Llegeix-3.4-armeabi-v7a.apk"
            },
            {
              "name": "Llegeix-3.4-universal.apk",
              "size": 159868438,
              "browser_download_url": "https://github.com/x/releases/download/v3.4/Llegeix-3.4-universal.apk"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `reads the version, the notes and the builds`() {
        val release = ReleaseFeed.parse(feed)!!

        // The tag is written with a v and the installed version is not; they
        // have to end up comparable.
        assertEquals("3.4", release.version)
        assertEquals(3, release.assets.size)
        assertTrue(release.pageUrl.endsWith("/tag/v3.4"))
        // Markdown marks are off, the sentence is intact.
        assertTrue(release.notes, "Per què" in release.notes)
        assertTrue(release.notes, "Una cosa nova." in release.notes)
        assertFalse(release.notes, "##" in release.notes)
        assertFalse(release.notes, "**" in release.notes)
    }

    @Test
    fun `refuses a draft or a prerelease`() {
        assertNull(ReleaseFeed.parse(feed.replace("\"draft\": false", "\"draft\": true")))
        assertNull(
            ReleaseFeed.parse(feed.replace("\"prerelease\": false", "\"prerelease\": true")),
        )
    }

    @Test
    fun `refuses an answer that is not a release`() {
        assertNull(ReleaseFeed.parse(""))
        assertNull(ReleaseFeed.parse("not json at all"))
        assertNull(ReleaseFeed.parse("""{"message":"Not Found","status":"404"}"""))
        // Truncated mid-way, which is what a dropped connection leaves behind.
        assertNull(ReleaseFeed.parse(feed.substring(0, 120)))
    }

    @Test
    fun `a release with no builds parses but offers nothing`() {
        val release = ReleaseFeed.parse(feed.replace(Regex("\"assets\": \\[[^]]*]"), "\"assets\": []"))!!
        assertNull(ReleaseFeed.buildFor(release, listOf("arm64-v8a")))
    }

    @Test
    fun `takes the newest version as a number, not as text`() {
        assertTrue(ReleaseFeed.isNewer("3.4", "3.3"))
        assertTrue(ReleaseFeed.isNewer("v3.4", "3.3"))
        assertTrue(ReleaseFeed.isNewer("4.0", "3.9"))
        // The one a string comparison gets backwards, and the reason this is
        // not a string comparison: "3.10" sorts before "3.9" as characters.
        assertTrue(ReleaseFeed.isNewer("3.10", "3.9"))
        assertFalse(ReleaseFeed.isNewer("3.9", "3.10"))

        assertFalse(ReleaseFeed.isNewer("3.3", "3.3"))
        assertFalse(ReleaseFeed.isNewer("v3.3", "3.3"))
        assertFalse(ReleaseFeed.isNewer("3.2", "3.3"))
        // A longer version is newer only if the extra part says so.
        assertTrue(ReleaseFeed.isNewer("3.3.1", "3.3"))
        assertFalse(ReleaseFeed.isNewer("3.3.0", "3.3"))
    }

    @Test
    fun `never offers an update on a version it cannot read`() {
        assertFalse(ReleaseFeed.isNewer("", "3.3"))
        assertFalse(ReleaseFeed.isNewer("latest", "3.3"))
        // An unreadable *installed* version must not turn every release into an
        // update; it does, and that is the honest answer — there is a real
        // release and nothing to compare it against, so it is offered.
        assertTrue(ReleaseFeed.isNewer("3.3", ""))
    }

    @Test
    fun `takes the build the phone prefers`() {
        val release = ReleaseFeed.parse(feed)!!

        // An arm64 phone lists both and can run both; the 64-bit one is what it
        // asked for first.
        val arm64 = ReleaseFeed.buildFor(release, listOf("arm64-v8a", "armeabi-v7a", "armeabi"))!!
        assertEquals("Llegeix-3.4-arm64-v8a.apk", arm64.asset.name)

        val older = ReleaseFeed.buildFor(release, listOf("armeabi-v7a", "armeabi"))!!
        assertEquals("Llegeix-3.4-armeabi-v7a.apk", older.asset.name)
    }

    @Test
    fun `names the architecture whole, hyphen and all`() {
        // Read off the file name, "Llegeix-3.4-arm64-v8a.apk" answers "v8a",
        // which is not an architecture and is what the card would have shown.
        val release = ReleaseFeed.parse(feed)!!
        assertEquals("arm64-v8a", ReleaseFeed.buildFor(release, listOf("arm64-v8a"))!!.abi)
        assertEquals("armeabi-v7a", ReleaseFeed.buildFor(release, listOf("armeabi-v7a"))!!.abi)
        assertEquals("universal", ReleaseFeed.buildFor(release, listOf("x86_64"))!!.abi)
    }

    @Test
    fun `falls back to the universal build, never to the wrong one`() {
        val release = ReleaseFeed.parse(feed)!!

        // A device this release has no dedicated build for still has one that
        // will run: the universal APK carries every architecture.
        val fallback = ReleaseFeed.buildFor(release, listOf("x86_64"))!!
        assertEquals("Llegeix-3.4-universal.apk", fallback.asset.name)

        // And with no universal build either, nothing is offered rather than
        // an APK the phone cannot run.
        val armOnly = release.copy(assets = release.assets.filterNot { "universal" in it.name })
        assertNull(ReleaseFeed.buildFor(armOnly, listOf("x86_64")))
    }

    @Test
    fun `does not confuse one architecture for another`() {
        // "arm64-v8a" ends with "v8a" and "armeabi-v7a" starts with "armeabi":
        // matching on anything looser than the whole suffix picks the wrong
        // file, and the wrong file is an APK the phone cannot run.
        val armOnly = ReleaseFeed.parse(feed)!!
            .let { it.copy(assets = it.assets.filterNot { asset -> "universal" in asset.name }) }

        assertEquals(
            "Llegeix-3.4-armeabi-v7a.apk",
            ReleaseFeed.buildFor(armOnly, listOf("armeabi-v7a"))!!.asset.name,
        )
        assertNull(ReleaseFeed.buildFor(armOnly, listOf("armeabi")))
        assertNull(ReleaseFeed.buildFor(armOnly, listOf("v8a")))
    }
}
