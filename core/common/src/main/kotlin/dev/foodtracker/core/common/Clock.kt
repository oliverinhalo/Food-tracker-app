package dev.foodtracker.core.common

import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/** Indirection over "now" so meal-type suggestion and diary dates are testable. */
interface TimeProvider {
    fun now(): LocalTime
    fun today(): LocalDate
    fun epochMillis(): Long
}

@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun now(): LocalTime = LocalTime.now()
    override fun today(): LocalDate = LocalDate.now()
    override fun epochMillis(): Long = System.currentTimeMillis()
}
