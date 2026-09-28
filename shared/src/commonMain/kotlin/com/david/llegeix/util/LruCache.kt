package com.david.llegeix.util

/**
 * A small least-recently-used cache, sized by [sizeOf], as Android's own
 * android.util.LruCache is; shared so the Mac can use the same code.
 */
open class LruCache<K : Any, V : Any>(private val maxSize: Int) {

    private val map = LinkedHashMap<K, V>(0, 0.75f, true)
    private var size = 0

    protected open fun sizeOf(key: K, value: V): Int = 1

    @Synchronized
    fun get(key: K): V? = map[key]

    @Synchronized
    fun put(key: K, value: V) {
        map.put(key, value)?.let { size -= sizeOf(key, it) }
        size += sizeOf(key, value)
        trimTo(maxSize)
    }

    @Synchronized
    fun evictAll() {
        map.clear()
        size = 0
    }

    private fun trimTo(limit: Int) {
        val entries = map.entries.iterator()
        while (size > limit && entries.hasNext()) {
            val eldest = entries.next()
            size -= sizeOf(eldest.key, eldest.value)
            entries.remove()
        }
    }
}
