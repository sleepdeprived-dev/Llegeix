package com.david.llegeix.data

/** Puts the app back to how it was before it was ever opened, for Configuració's "erase everything". */
interface DataEraser {
    suspend fun eraseEverything()
}
