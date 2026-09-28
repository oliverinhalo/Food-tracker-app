package dev.foodtracker.data.nutrition

import dev.foodtracker.core.database.entity.CachedFoodEntity
import dev.foodtracker.core.database.entity.FoodSource
import dev.foodtracker.core.model.Nutrients

/**
 * A food as the app understands it, independent of which database it came from. Both USDA and
 * Open Food Facts normalise to this at the boundary, so nothing downstream needs to know the shape
 * of either API.
 */
data class FoodRecord(
    val id: String,
    val name: String,
    val brand: String?,
    val barcode: String?,
    val source: FoodSource,
    val searchKey: String,
    val per100g: Nutrients,
    val servingSizeGrams: Double?,
    val servingDescription: String?,
) {
    val isBranded: Boolean get() = !brand.isNullOrBlank()
}

fun FoodRecord.toEntity(nowMillis: Long): CachedFoodEntity = CachedFoodEntity(
    id = id,
    name = name,
    brand = brand,
    barcode = barcode,
    source = source,
    searchKey = searchKey,
    caloriesPer100g = per100g.calories,
    proteinPer100g = per100g.proteinGrams,
    carbsPer100g = per100g.carbsGrams,
    fatPer100g = per100g.fatGrams,
    fiberPer100g = per100g.fiberGrams,
    sugarPer100g = per100g.sugarGrams,
    sodiumMgPer100g = per100g.sodiumMilligrams,
    servingSizeGrams = servingSizeGrams,
    servingDescription = servingDescription,
    cachedAtMillis = nowMillis,
)

fun CachedFoodEntity.toRecord(): FoodRecord = FoodRecord(
    id = id,
    name = name,
    brand = brand,
    barcode = barcode,
    source = source,
    searchKey = searchKey,
    per100g = Nutrients(
        calories = caloriesPer100g,
        proteinGrams = proteinPer100g,
        carbsGrams = carbsPer100g,
        fatGrams = fatPer100g,
        fiberGrams = fiberPer100g,
        sugarGrams = sugarPer100g,
        sodiumMilligrams = sodiumMgPer100g,
    ),
    servingSizeGrams = servingSizeGrams,
    servingDescription = servingDescription,
)
