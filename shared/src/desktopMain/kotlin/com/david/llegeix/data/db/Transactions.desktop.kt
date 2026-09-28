package com.david.llegeix.data.db

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

actual suspend fun <R> LlegeixDatabase.inTransaction(block: suspend () -> R): R =
    useWriterConnection { connection -> connection.immediateTransaction { block() } }
