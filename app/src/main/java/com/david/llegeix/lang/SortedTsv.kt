package com.david.llegeix.lang

import android.content.Context
import java.io.IOException

/**
 * A sorted, tab-separated reference file, searched without unpacking it.
 *
 * The language assets are all the same shape: one line per headword, sorted,
 * with the key first and tab-separated fields after it. This holds the file as
 * raw bytes and binary-searches those bytes directly, so none of the tens of
 * thousands of keys is ever turned into a Java string and there is no index to
 * build on top of the file.
 *
 * Byte comparison is the same order as character comparison here because UTF-8
 * preserves code-point order, which is what the files are sorted by.
 */
class SortedTsv private constructor(
    private val data: ByteArray,
    private val lineStarts: IntArray,
) {

    val isEmpty: Boolean get() = lineStarts.isEmpty()

    /**
     * The fields of the line whose key is [key], or null when there is none.
     *
     * The first element is the key itself, matching the file's own spelling.
     */
    fun find(key: String): List<String>? {
        if (key.isEmpty() || isEmpty) return null
        val needle = key.toByteArray(Charsets.UTF_8)
        var low = 0
        var high = lineStarts.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val cmp = compareKeyAt(lineStarts[mid], needle)
            when {
                cmp < 0 -> low = mid + 1
                cmp > 0 -> high = mid - 1
                else -> return fieldsAt(lineStarts[mid])
            }
        }
        return null
    }

    /**
     * Keys that begin with [prefix], in the file's own order, at most [limit].
     *
     * The same binary search as [find], stopped at the first line that could
     * match rather than at an exact hit, then walked forward. That is what makes
     * a live suggestion list affordable over forty thousand headwords: no index
     * is built and no key but the handful being offered is ever turned into a
     * string.
     */
    fun keysStartingWith(prefix: String, limit: Int): List<String> {
        if (prefix.isEmpty() || limit <= 0 || isEmpty) return emptyList()
        val needle = prefix.toByteArray(Charsets.UTF_8)
        var low = 0
        var high = lineStarts.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (comparePrefixAt(lineStarts[mid], needle) < 0) low = mid + 1 else high = mid
        }
        val found = ArrayList<String>(limit)
        var index = low
        while (index < lineStarts.size && found.size < limit) {
            if (comparePrefixAt(lineStarts[index], needle) != 0) break
            found += keyAt(lineStarts[index])
            index++
        }
        return found
    }

    /**
     * Whether the key at [start] sorts before, inside, or after [needle]'s
     * prefix range: negative, zero, positive. A key shorter than the prefix
     * sorts before it, which is what keeps the lower-bound search honest.
     */
    private fun comparePrefixAt(start: Int, needle: ByteArray): Int {
        var i = start
        var j = 0
        while (j < needle.size) {
            if (i >= data.size || data[i] == TAB || data[i] == NEWLINE) return -1
            val a = data[i].toInt() and 0xFF
            val b = needle[j].toInt() and 0xFF
            if (a != b) return a - b
            i++
            j++
        }
        return 0
    }

    /** Just the key of the line starting at [start]. */
    private fun keyAt(start: Int): String {
        var end = start
        while (end < data.size && data[end] != TAB && data[end] != NEWLINE) end++
        return String(data, start, end - start, Charsets.UTF_8)
    }

    /** Compares the key of the line starting at [start] against [needle]. */
    private fun compareKeyAt(start: Int, needle: ByteArray): Int {
        var i = start
        var j = 0
        while (i < data.size && data[i] != TAB && data[i] != NEWLINE) {
            if (j == needle.size) return 1
            val a = data[i].toInt() and 0xFF
            val b = needle[j].toInt() and 0xFF
            if (a != b) return a - b
            i++
            j++
        }
        return if (j == needle.size) 0 else -1
    }

    private fun fieldsAt(start: Int): List<String> {
        var end = start
        while (end < data.size && data[end] != NEWLINE) end++
        return String(data, start, end - start, Charsets.UTF_8).split('\t')
    }

    companion object {
        private const val TAB = '\t'.code.toByte()
        private const val NEWLINE = '\n'.code.toByte()

        /**
         * Reads [asset] into memory.
         *
         * A missing or unreadable file yields an empty table rather than an
         * error: the screens that use these can all say "nothing found", and
         * that is a far better failure than a crash in the middle of reading.
         */
        fun load(context: Context, asset: String): SortedTsv {
            val data = try {
                context.applicationContext.assets.open(asset).use { it.readBytes() }
            } catch (error: IOException) {
                ByteArray(0)
            }
            return SortedTsv(data, indexLines(data))
        }

        /** Builds a table from lines, for tests and for small in-memory sets. */
        fun of(lines: List<String>): SortedTsv {
            if (lines.isEmpty()) return SortedTsv(ByteArray(0), IntArray(0))
            val data = (lines.sorted().joinToString("\n") + "\n").toByteArray(Charsets.UTF_8)
            return SortedTsv(data, indexLines(data))
        }

        private fun indexLines(data: ByteArray): IntArray {
            if (data.isEmpty()) return IntArray(0)
            var count = 0
            for (byte in data) if (byte == NEWLINE) count++
            val starts = IntArray(count + 1)
            var n = 0
            starts[n++] = 0
            for (i in data.indices) {
                if (data[i] == NEWLINE && i + 1 < data.size) starts[n++] = i + 1
            }
            return if (n == starts.size) starts else starts.copyOf(n)
        }
    }
}
