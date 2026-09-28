package dev.foodtracker.data.diary

import dev.foodtracker.core.common.TimeProvider
import dev.foodtracker.core.common.di.IoDispatcher
import dev.foodtracker.core.database.dao.DiaryDao
import dev.foodtracker.core.database.dao.MealWithItems
import dev.foodtracker.core.database.entity.FavouriteFoodEntity
import dev.foodtracker.core.database.entity.LoggedFoodItemEntity
import dev.foodtracker.core.database.entity.LoggedMealEntity
import dev.foodtracker.core.model.DetectedItem
import dev.foodtracker.core.model.MealType
import dev.foodtracker.core.model.MeasurementUnit
import dev.foodtracker.core.model.Nutrients
import dev.foodtracker.core.model.Portion
import dev.foodtracker.domain.nutrition.foodKeyOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiaryRepository @Inject constructor(
    private val diaryDao: DiaryDao,
    private val timeProvider: TimeProvider,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    fun mealsFor(date: LocalDate): Flow<List<LoggedMeal>> =
        diaryDao.mealsForDay(date.toEpochDay()).map { rows -> rows.map { it.toDomain() } }

    fun totalsFor(date: LocalDate): Flow<Nutrients> =
        diaryDao.totalsForDay(date.toEpochDay()).map { totals ->
            totals?.let {
                Nutrients(
                    calories = it.calories,
                    proteinGrams = it.proteinGrams,
                    carbsGrams = it.carbsGrams,
                    fatGrams = it.fatGrams,
                )
            } ?: Nutrients.ZERO
        }

    fun totalsBetween(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Nutrients>> =
        diaryDao.totalsBetween(from.toEpochDay(), to.toEpochDay()).map { rows ->
            rows.associate { row ->
                LocalDate.ofEpochDay(row.epochDay) to Nutrients(
                    calories = row.calories,
                    proteinGrams = row.proteinGrams,
                    carbsGrams = row.carbsGrams,
                    fatGrams = row.fatGrams,
                )
            }
        }

    /**
     * Writes a meal and its items in one transaction, with each item's nutrition denormalised onto
     * its row: a meal must keep the numbers it was logged with even if the food database later
     * changes underneath it.
     */
    suspend fun logMeal(
        mealType: MealType,
        items: List<DetectedItem>,
        date: LocalDate = timeProvider.today(),
        photoPath: String? = null,
    ): String = withContext(ioDispatcher) {
        val mealId = UUID.randomUUID().toString()
        val loggedAt = timeProvider.epochMillis()

        // Every item the user confirmed is written, including any whose nutrition never resolved.
        // Dropping those silently made a whole meal vanish from Home whenever a lookup failed --
        // the user confirmed a plate of food and got an empty day back.
        val rows = items.map { item ->
            val nutrients = item.nutrients ?: Nutrients.ZERO
            LoggedFoodItemEntity(
                id = UUID.randomUUID().toString(),
                mealId = mealId,
                name = item.name,
                brand = item.brand,
                foodKey = foodKeyOf(item.name, item.brand),
                cachedFoodId = item.foodId,
                amount = item.portion.amount,
                unit = item.portion.unit.name,
                grams = item.portion.grams,
                calories = nutrients.calories,
                proteinGrams = nutrients.proteinGrams,
                carbsGrams = nutrients.carbsGrams,
                fatGrams = nutrients.fatGrams,
                recognitionSource = item.source.name,
            )
        }

        diaryDao.logMeal(
            meal = LoggedMealEntity(
                id = mealId,
                mealType = mealType.name,
                epochDay = date.toEpochDay(),
                loggedAtMillis = loggedAt,
                photoPath = photoPath,
            ),
            items = rows,
        )

        rows.forEach { row -> rememberAsFavourite(row) }
        mealId
    }

    suspend fun deleteMeal(mealId: String) = withContext(ioDispatcher) { diaryDao.deleteMeal(mealId) }

    fun favourites(limit: Int = 30): Flow<List<FavouriteFoodEntity>> = diaryDao.favourites(limit)

    /** Every logged item bumps its food's use count, which is what drives the recents list. */
    private suspend fun rememberAsFavourite(row: LoggedFoodItemEntity) {
        val existing = diaryDao.favouriteByKey(row.foodKey)
        diaryDao.upsertFavourite(
            FavouriteFoodEntity(
                foodKey = row.foodKey,
                name = row.name,
                brand = row.brand,
                cachedFoodId = row.cachedFoodId,
                lastUsedMillis = timeProvider.epochMillis(),
                useCount = (existing?.useCount ?: 0) + 1,
            ),
        )
    }
}

private fun MealWithItems.toDomain(): LoggedMeal = LoggedMeal(
    id = meal.id,
    mealType = MealType.entries.firstOrNull { it.name == meal.mealType } ?: MealType.SNACK,
    date = LocalDate.ofEpochDay(meal.epochDay),
    loggedAtMillis = meal.loggedAtMillis,
    items = items.map { row ->
        LoggedFood(
            id = row.id,
            name = row.name,
            brand = row.brand,
            portion = Portion(
                amount = row.amount,
                unit = MeasurementUnit.entries.firstOrNull { it.name == row.unit } ?: MeasurementUnit.GRAM,
                grams = row.grams,
            ),
            nutrients = Nutrients(
                calories = row.calories,
                proteinGrams = row.proteinGrams,
                carbsGrams = row.carbsGrams,
                fatGrams = row.fatGrams,
            ),
        )
    },
)
