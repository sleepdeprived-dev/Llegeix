package com.david.llegeix.data.db

/**
 * Run [block] in one transaction: every DAO call inside it lands together or
 * not at all. Room's withTransaction on the phone, as before; its
 * multiplatform transaction on the Mac.
 */
expect suspend fun <R> LlegeixDatabase.inTransaction(block: suspend () -> R): R
