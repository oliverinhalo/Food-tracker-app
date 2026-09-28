package dev.foodtracker.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Where a cached food came from. Kept as a column so a source can be re-fetched or invalidated. */
enum class FoodSource { USDA, OPEN_FOOD_FACTS, MANUAL }

/**
 * A food and its per-100g nutrition, cached so repeat foods resolve instantly and offline.
 *
 * Nutrition is stored per 100 g regardless of source, because that is the only form both USDA and
 * Open Food Facts agree on; portion scaling happens at read time.
 */
@Entity(
    tableName = "cached_foods",
    indices = [
        Index(value = ["searchKey"]),
        Index(value = ["barcode"], unique = true),
    ],
)
data class CachedFoodEntity(
    @PrimaryKey val id: String,
    val name: String,
    val brand: String?,
    val barcode: String?,
    val source: FoodSource,
    /** Normalised name used for offline lookup; see `foodKeyOf`. */
    val searchKey: String,
    val caloriesPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val fiberPer100g: Double?,
    val sugarPer100g: Double?,
    val sodiumMgPer100g: Double?,
    val servingSizeGrams: Double?,
    val servingDescription: String?,
    val cachedAtMillis: Long,
)

/**
 * A portion the user corrected. Kept as an append-only log rather than a single current value so
 * the learner can weight by recency and reconsider as habits change.
 */
@Entity(
    tableName = "portion_corrections",
    indices = [Index(value = ["foodKey", "unit"])],
)
data class PortionCorrectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodKey: String,
    val unit: String,
    val estimatedGrams: Double,
    val correctedGrams: Double,
    val recordedAtMillis: Long,
)
