package dev.foodtracker.data.diary

import dev.foodtracker.core.model.MealType
import dev.foodtracker.core.model.Nutrients
import dev.foodtracker.core.model.Portion
import java.time.LocalDate

data class LoggedFood(
    val id: String,
    val name: String,
    val brand: String?,
    val portion: Portion,
    val nutrients: Nutrients,
)

data class LoggedMeal(
    val id: String,
    val mealType: MealType,
    val date: LocalDate,
    val loggedAtMillis: Long,
    val items: List<LoggedFood>,
) {
    val totals: Nutrients
        get() = items.fold(Nutrients.ZERO) { acc, item -> acc + item.nutrients }
}

data class DaySummary(
    val date: LocalDate,
    val totals: Nutrients,
    val meals: List<LoggedMeal>,
)
