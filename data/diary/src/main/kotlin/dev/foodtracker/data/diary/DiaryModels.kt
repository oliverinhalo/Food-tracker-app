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
    val photoPath: String? = null,
) {
    val totals: Nutrients
        get() = items.fold(Nutrients.ZERO) { acc, item -> acc + item.nutrients }
}

data class DaySummary(
    val date: LocalDate,
    val totals: Nutrients,
    val meals: List<LoggedMeal>,
)

/**
 * A food the user logs often, offered for one-tap logging. A domain type rather than the Room
 * entity: the database's shape should not reach the UI layer.
 */
data class FavouriteFood(
    val foodKey: String,
    val name: String,
    val brand: String?,
    val cachedFoodId: String?,
    val useCount: Int,
    val lastUsedMillis: Long,
)
