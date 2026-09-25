package com.david.llegeix.data.flashcards

import com.david.llegeix.data.db.entity.FlashcardCollectionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionTreeTest {

    private fun shelf(id: Long, parent: Long? = null) =
        FlashcardCollectionEntity(id = id, name = "s$id", parentId = parent)

    // Menjar(1) > Fruita(2) > Cítrics(3); Viatges(4) on its own.
    private val tree = listOf(shelf(1), shelf(2, 1), shelf(3, 2), shelf(4))

    @Test
    fun `a subtree is the shelf and everything under it, however deep`() {
        assertEquals(setOf(1L, 2L, 3L), CollectionTree.subtree(1, tree))
        assertEquals(setOf(3L), CollectionTree.subtree(3, tree))
    }

    @Test
    fun `a shelf can never go inside itself or anything inside it`() {
        assertFalse(CollectionTree.canMove(1, 1, tree))
        assertFalse(CollectionTree.canMove(1, 3, tree))
        assertTrue(CollectionTree.canMove(3, 4, tree))
        assertTrue(CollectionTree.canMove(2, null, tree))
    }

    @Test
    fun `a parent that has gone reads as the top of the list`() {
        val parents = CollectionTree.parents(listOf(shelf(1, 99)))
        assertNull(parents[1])
    }

    @Test
    fun `a loop, however it got there, is cut so every shelf is still reachable`() {
        val parents = CollectionTree.parents(listOf(shelf(1, 2), shelf(2, 1)))
        assertTrue("at least one of them is at the top", parents.values.any { it == null })
        assertNull("a shelf that names itself is at the top", CollectionTree.parents(listOf(shelf(5, 5)))[5])
    }
}
