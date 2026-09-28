package dev.foodtracker.core.model

import java.time.LocalTime

enum class MealType {
    BREAKFAST,
    LUNCH,
    DINNER,
    SNACK,
    ;

    companion object {
        /**
         * The meal the sheet pre-selects. Boundaries are deliberately generous: a late lunch at
         * 15:30 is still lunch, and anything after 21:00 is a snack rather than dinner.
         */
        fun suggestedFor(time: LocalTime): MealType = when (time.hour) {
            in 4..10 -> BREAKFAST
            in 11..15 -> LUNCH
            in 16..20 -> DINNER
            else -> SNACK
        }
    }
}
