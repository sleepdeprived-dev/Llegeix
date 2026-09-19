package com.david.llegeix.data.flashcards

import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * How long until a card comes back, in the one unit that reads naturally.
 *
 * "In 3 hours" and "in 2 days", not "in 187 minutes" or "in 0.1 days". The
 * wording is the resources' business; this only decides the unit and the
 * number, so the rounding is tested once and every language agrees on it.
 */
object NextDue {

    enum class Unit { MINUTES, HOURS, DAYS }

    data class Wait(val unit: Unit, val amount: Int)

    fun waitUntil(dueAt: Long, now: Long): Wait {
        val millis = (dueAt - now).coerceAtLeast(0)
        val minutes = millis / MINUTE.toDouble()
        return when {
            // Rounded up: "in 0 minutes" is not a time, and a card that is due
            // in forty seconds is still not due now.
            minutes < 60 -> Wait(Unit.MINUTES, ceil(minutes).toInt().coerceAtLeast(1))
            minutes < 48 * 60 -> Wait(Unit.HOURS, (minutes / 60).roundToInt())
            else -> Wait(Unit.DAYS, (minutes / (24 * 60)).roundToInt())
        }
    }

    private val MINUTE = TimeUnit.MINUTES.toMillis(1)
}
