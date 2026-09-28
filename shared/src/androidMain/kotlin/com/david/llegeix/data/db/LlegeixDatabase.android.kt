package com.david.llegeix.data.db

import android.content.Context
import androidx.room.Room

/**
 * The phone's database, opened as it always has been: the same file, Android's
 * own SQLite, and every migration from version 1, so an existing library, its
 * saved words and its flashcards carry over untouched.
 */
fun LlegeixDatabase.Companion.build(context: Context): LlegeixDatabase =
    Room.databaseBuilder(
        context.applicationContext,
        LlegeixDatabase::class.java,
        "llegeix.db",
    ).addMigrations(
        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
        MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
        MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15,
        MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18,
        MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22,
        MIGRATION_22_23,
    )
        .build()
