package com.david.llegeix.update

import kotlinx.coroutines.flow.StateFlow

/**
 * What the library needs to know about newer versions: whether one is waiting,
 * and a once-a-day look for one. UpdateRepository on the phone.
 */
interface AppUpdates {
    val updateWaiting: StateFlow<Boolean>

    /** Look for a newer release, at most once a day and silently. */
    suspend fun checkQuietly()
}
