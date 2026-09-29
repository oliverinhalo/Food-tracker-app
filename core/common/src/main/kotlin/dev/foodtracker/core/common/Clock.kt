package dev.foodtracker.core.common

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Indirection over "now" so meal-type suggestion and diary dates are testable. */
interface TimeProvider {
    fun now(): LocalTime
    fun today(): LocalDate
    fun epochMillis(): Long

    /**
     * The current date, re-emitted when it changes.
     *
     * Screens showing "today" are long-lived. Leave the app open overnight, or on the counter while
     * cooking past midnight, and a date captured once at construction quietly keeps showing
     * yesterday's totals under today's heading. Waking near midnight is cheap next to getting that
     * wrong.
     */
    fun todayFlow(): Flow<LocalDate> = flow {
        while (true) {
            val today = today()
            emit(today)
            delay(millisUntilMidnightFrom(today).coerceAtMost(MAX_POLL_INTERVAL_MILLIS))
        }
    }.distinctUntilChanged()

    private fun millisUntilMidnightFrom(today: LocalDate): Long {
        val midnight = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return (midnight - epochMillis()).coerceAtLeast(1_000L)
    }

    private companion object {
        /** Also catches the clock or time zone moving, not only the day rolling over. */
        const val MAX_POLL_INTERVAL_MILLIS = 5 * 60 * 1000L
    }
}

@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun now(): LocalTime = LocalTime.now()
    override fun today(): LocalDate = LocalDate.now()
    override fun epochMillis(): Long = System.currentTimeMillis()
}
