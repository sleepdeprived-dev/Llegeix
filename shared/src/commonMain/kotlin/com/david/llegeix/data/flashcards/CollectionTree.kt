package com.david.llegeix.data.flashcards

import com.david.llegeix.data.db.entity.FlashcardCollectionEntity

/**
 * The shape of shelves inside shelves, worked out from the flat rows.
 *
 * Kept apart from the database so the one rule that matters — a shelf can
 * never end up inside itself — can be tested without one.
 */
object CollectionTree {

    /**
     * The parent each shelf really has: its own [FlashcardCollectionEntity.parentId]
     * when that shelf still exists, and null — the top of the list — when it
     * does not, or when following parents upwards would go round in a circle.
     */
    fun parents(all: List<FlashcardCollectionEntity>): Map<Long, Long?> {
        val raw = all.associate { it.id to it.parentId?.takeIf { parent -> parent != it.id } }
        return raw.mapValues { (id, parent) ->
            if (parent == null || parent !in raw) return@mapValues null
            // Walk up; a shelf met twice is a loop, and a loop is cut at the
            // shelf being asked about so that every one of them is still shown.
            val seen = hashSetOf(id)
            var at: Long? = parent
            while (at != null) {
                if (!seen.add(at)) return@mapValues null
                at = raw[at]?.takeIf { it in raw }
            }
            parent
        }
    }

    /** [id] and every shelf under it, however deep. */
    fun subtree(id: Long, all: List<FlashcardCollectionEntity>): Set<Long> {
        val children = parents(all).entries
            .filter { it.value != null }
            .groupBy({ it.value!! }, { it.key })
        val found = linkedSetOf(id)
        val queue = ArrayDeque(listOf(id))
        while (queue.isNotEmpty()) {
            children[queue.removeFirst()].orEmpty().forEach { if (found.add(it)) queue.add(it) }
        }
        return found
    }

    /** Whether [moving] may be put inside [target] (null is the top of the list). */
    fun canMove(moving: Long, target: Long?, all: List<FlashcardCollectionEntity>): Boolean =
        target == null || target !in subtree(moving, all)
}
