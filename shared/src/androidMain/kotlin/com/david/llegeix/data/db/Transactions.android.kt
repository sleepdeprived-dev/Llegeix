package com.david.llegeix.data.db

import androidx.room.withTransaction

actual suspend fun <R> LlegeixDatabase.inTransaction(block: suspend () -> R): R = withTransaction(block)
