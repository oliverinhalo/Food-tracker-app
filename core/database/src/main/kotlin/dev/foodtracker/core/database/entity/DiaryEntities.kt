package dev.foodtracker.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One logged meal. [epochDay] is stored alongside the timestamp because every screen queries by
 * local calendar day, and deriving that from a millisecond timestamp in SQL would bake the
 * device's current timezone into the query.
 */
@Entity(
    tableName = "logged_meals",
    indices = [Index(value = ["epochDay"])],
)
data class LoggedMealEntity(
    @PrimaryKey val id: String,
    val mealType: String,
    val epochDay: Long,
    val loggedAtMillis: Long,
    val photoPath: String?,
)

/**
 * One food within a logged meal. Nutrition is denormalised onto the row on purpose: a meal logged
 * last month must keep the numbers it was logged with, even if the cached food is later corrected
 * or the database row changes upstream.
 */
@Entity(
    tableName = "logged_food_items",
    foreignKeys = [
        ForeignKey(
            entity = LoggedMealEntity::class,
            parentColumns = ["id"],
            childColumns = ["mealId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["mealId"])],
)
data class LoggedFoodItemEntity(
    @PrimaryKey val id: String,
    val mealId: String,
    val name: String,
    val brand: String?,
    val foodKey: String,
    val cachedFoodId: String?,
    val amount: Double,
    val unit: String,
    val grams: Double,
    val calories: Double,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
    val recognitionSource: String,
)

/** A food the user logs often, surfaced for one-tap logging. */
@Entity(tableName = "favourite_foods", indices = [Index(value = ["foodKey"], unique = true)])
data class FavouriteFoodEntity(
    @PrimaryKey val foodKey: String,
    val name: String,
    val brand: String?,
    val cachedFoodId: String?,
    val lastUsedMillis: Long,
    val useCount: Int,
)
