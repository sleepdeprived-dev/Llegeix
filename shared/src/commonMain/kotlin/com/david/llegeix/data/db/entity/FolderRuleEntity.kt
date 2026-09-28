package com.david.llegeix.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A decision about one folder inside a source: show what is in it, or don't.
 *
 * Only folders the reader has actually decided about are stored. Everything
 * else inherits from the nearest folder above it that has a decision, and a
 * folder with no decision anywhere above it is shown. That is what makes both
 * halves of the request work with one table: granting a big folder and hiding
 * one subfolder inside it is an exclusion on the child, and granting a big
 * folder but wanting only one subfolder out of it is an exclusion on the
 * parent plus an inclusion on the one child.
 *
 * The key is the display path the scan already builds — "Documents/Català" —
 * rather than a provider document id, because the path is what the reader sees
 * and it survives the file being re-indexed.
 */
@Entity(tableName = "folder_rules")
data class FolderRuleEntity(
    @PrimaryKey val path: String,
    val included: Boolean,
)
