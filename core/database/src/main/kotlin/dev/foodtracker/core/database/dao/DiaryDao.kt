package dev.foodtracker.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import dev.foodtracker.core.database.entity.FavouriteFoodEntity
import dev.foodtracker.core.database.entity.LoggedFoodItemEntity
import dev.foodtracker.core.database.entity.LoggedMealEntity
import kotlinx.coroutines.flow.Flow

data class MealWithItems(
    @Embedded val meal: LoggedMealEntity,
    @Relation(parentColumn = "id", entityColumn = "mealId")
    val items: List<LoggedFoodItemEntity>,
)

/** Pre-aggregated day totals, so the home ring does not have to sum every row in memory. */
data class DayTotals(
    val epochDay: Long,
    val calories: Double,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
)

@Dao
interface DiaryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeal(meal: LoggedMealEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<LoggedFoodItemEntity>)

    /** A meal and its items must appear together or not at all. */
    @Transaction
    suspend fun logMeal(meal: LoggedMealEntity, items: List<LoggedFoodItemEntity>) {
        insertMeal(meal)
        insertItems(items)
    }

    @Transaction
    @Query("SELECT * FROM logged_meals WHERE epochDay = :epochDay ORDER BY loggedAtMillis ASC")
    fun mealsForDay(epochDay: Long): Flow<List<MealWithItems>>

    @Transaction
    @Query("SELECT * FROM logged_meals WHERE id = :id")
    suspend fun mealById(id: String): MealWithItems?

    @Query(
        """
        SELECT m.epochDay AS epochDay,
               COALESCE(SUM(i.calories), 0)     AS calories,
               COALESCE(SUM(i.proteinGrams), 0) AS proteinGrams,
               COALESCE(SUM(i.carbsGrams), 0)   AS carbsGrams,
               COALESCE(SUM(i.fatGrams), 0)     AS fatGrams
        FROM logged_meals m
        LEFT JOIN logged_food_items i ON i.mealId = m.id
        WHERE m.epochDay = :epochDay
        GROUP BY m.epochDay
        """,
    )
    fun totalsForDay(epochDay: Long): Flow<DayTotals?>

    @Query(
        """
        SELECT m.epochDay AS epochDay,
               COALESCE(SUM(i.calories), 0)     AS calories,
               COALESCE(SUM(i.proteinGrams), 0) AS proteinGrams,
               COALESCE(SUM(i.carbsGrams), 0)   AS carbsGrams,
               COALESCE(SUM(i.fatGrams), 0)     AS fatGrams
        FROM logged_meals m
        LEFT JOIN logged_food_items i ON i.mealId = m.id
        WHERE m.epochDay BETWEEN :fromEpochDay AND :toEpochDay
        GROUP BY m.epochDay
        ORDER BY m.epochDay ASC
        """,
    )
    fun totalsBetween(fromEpochDay: Long, toEpochDay: Long): Flow<List<DayTotals>>

    @Query("DELETE FROM logged_meals WHERE id = :mealId")
    suspend fun deleteMeal(mealId: String)

    @Query("DELETE FROM logged_food_items WHERE mealId = :mealId")
    suspend fun deleteItemsForMeal(mealId: String)

    /**
     * Replaces a meal's contents in one transaction. Editing is a replace rather than a diff
     * because items can be added, removed and renamed at once, and a half-applied edit would leave
     * a day's totals wrong with no sign of why.
     */
    @Transaction
    suspend fun replaceMeal(meal: LoggedMealEntity, items: List<LoggedFoodItemEntity>) {
        deleteItemsForMeal(meal.id)
        insertMeal(meal)
        insertItems(items)
    }

    @Query("DELETE FROM logged_food_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavourite(favourite: FavouriteFoodEntity)

    @Query("SELECT * FROM favourite_foods ORDER BY useCount DESC, lastUsedMillis DESC LIMIT :limit")
    fun favourites(limit: Int = 30): Flow<List<FavouriteFoodEntity>>

    @Query("SELECT * FROM favourite_foods WHERE foodKey = :foodKey")
    suspend fun favouriteByKey(foodKey: String): FavouriteFoodEntity?
}
