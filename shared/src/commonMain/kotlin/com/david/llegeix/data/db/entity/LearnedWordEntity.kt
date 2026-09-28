package com.david.llegeix.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A word the reader says they learned, on the day they learned it: the
 * Catalan and its Romanian, and nothing else. A diary more than a deck — the
 * list is read by day, "25 de setembre: paraula, poma, pera" — so the date is
 * the point, and there is no schedule.
 */
@Entity(tableName = "learned_words", indices = [Index("learnedAt")])
data class LearnedWordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val catalan: String,
    val romanian: String,
    /** When it was added, which decides the day it is listed under. */
    val learnedAt: Long = System.currentTimeMillis(),
)
