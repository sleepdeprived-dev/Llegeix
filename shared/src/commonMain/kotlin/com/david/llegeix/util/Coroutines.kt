package com.david.llegeix.util

import kotlin.coroutines.cancellation.CancellationException

/**
 * [runCatching], minus the part that breaks coroutines.
 *
 * `runCatching` catches [Throwable], and a coroutine unwinds by throwing
 * [CancellationException] — so the stock version silently swallows cancellation.
 * That breaks structured concurrency (the parent is never told the child
 * stopped) and, more visibly, turns an ordinary cancelled job into what looks
 * like a genuine failure: cancelling a scan to start a newer one would report
 * "StandaloneCoroutine was cancelled" to the user as an error.
 *
 * Use this for any `runCatching` wrapping suspending work.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        Result.failure(error)
    }
