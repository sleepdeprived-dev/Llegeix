package com.david.llegeix.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A user-created folder. Purely an organisational label, not a place on disk. */
@Entity(
    tableName = "folders",
    indices = [Index(value = ["name"], unique = true)],
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Pinned folders sort above the rest. */
    val isPinned: Boolean = false,
    val isBookmarked: Boolean = false,
    /** ARGB tint for the folder, or null for the default. */
    val colorArgb: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
