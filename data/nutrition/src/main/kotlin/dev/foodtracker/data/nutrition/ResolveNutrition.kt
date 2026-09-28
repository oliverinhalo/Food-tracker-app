package dev.foodtracker.data.nutrition

import dev.foodtracker.core.model.DetectedItem
import dev.foodtracker.core.model.Portion
import dev.foodtracker.domain.nutrition.FoodCategory
import dev.foodtracker.domain.nutrition.UnitConverter
import dev.foodtracker.domain.nutrition.foodKeyOf
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

/**
 * Fills in nutrition for freshly detected items and applies what the app has learned about this
 * user's portions.
 *
 * Items are resolved concurrently: a plate of five foods otherwise costs five sequential round
 * trips, which is the difference between the sheet filling in under a second and visibly ticking
 * over one row at a time.
 */
class ResolveNutrition @Inject constructor(
    private val repository: NutritionRepository,
) {

    suspend operator fun invoke(items: List<DetectedItem>): List<DetectedItem> = coroutineScope {
        items
            .map { item -> async { resolveOne(item) } }
            .map { it.await() }
    }

    private suspend fun resolveOne(item: DetectedItem): DetectedItem {
        if (item.nutrientsPer100g != null) return item
        if (item.name.isBlank()) return item

        // The recogniser already told us how it was prepared; that is exactly what separates
        // cooked rice from rice flour in a database search.
        val record = repository.resolve(item.name, item.brand, item.cookingMethod) ?: return item
        val key = foodKeyOf(item.name, item.brand)

        // A user-set portion is theirs; only a recogniser's estimate gets nudged by past habits.
        val portion = if (item.source == dev.foodtracker.core.model.RecognitionSource.USER) {
            item.portion
        } else {
            biasedPortion(item, key)
        }

        return item.copy(
            portion = portion,
            nutrientsPer100g = record.per100g,
            foodId = record.id,
            brand = item.brand ?: record.brand,
        )
    }

    private suspend fun biasedPortion(item: DetectedItem, key: String): Portion {
        val biasedGrams = repository.biasEstimate(key, item.portion.unit, item.portion.grams)
        if (biasedGrams == item.portion.grams) return item.portion

        val converter = UnitConverter(repository.overrideStoreFor(listOf(key)))
        val profile = FoodCategory.profileFor(item.name)

        return Portion(
            amount = converter.fromGrams(biasedGrams, item.portion.unit, profile, key),
            unit = item.portion.unit,
            grams = biasedGrams,
            // The recogniser's phrasing ("1 medium breast") described the estimate we have just
            // changed, so keeping it would label the new amount with the old description.
            householdDescription = null,
        )
    }
}
