package dev.foodtracker.data.diary

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.foodtracker.core.common.PhotoDirectories
import dev.foodtracker.core.common.TimeProvider
import dev.foodtracker.core.common.di.IoDispatcher
import dev.foodtracker.core.database.dao.DiaryDao
import dev.foodtracker.core.database.dao.FoodDao
import dev.foodtracker.core.database.entity.LoggedFoodItemEntity
import dev.foodtracker.core.database.entity.LoggedMealEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class BackupFile(
    val version: Int = CURRENT_VERSION,
    val exportedAtMillis: Long,
    val meals: List<BackupMeal>,
) {
    companion object {
        /** Bumped when the shape changes, so an import can refuse a file it cannot read. */
        const val CURRENT_VERSION = 1
    }
}

@Serializable
data class BackupMeal(
    val id: String,
    val mealType: String,
    val epochDay: Long,
    val loggedAtMillis: Long,
    val items: List<BackupItem>,
)

@Serializable
data class BackupItem(
    val name: String,
    val brand: String? = null,
    val foodKey: String,
    val amount: Double,
    val unit: String,
    val grams: Double,
    val calories: Double,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
)

sealed interface ImportResult {
    data class Imported(val meals: Int) : ImportResult
    data class Failed(val reason: String) : ImportResult
}

/**
 * The on-disk shape of a backup, apart from the database.
 *
 * Kept separate so the part that actually has to survive -- a file written by one version and read
 * by another -- can be tested without a device or a Room instance.
 */
object BackupFormat {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    /** Returns the file, or the reason it cannot be restored. */
    fun decode(contents: String): Result<BackupFile> {
        val parsed = runCatching { json.decodeFromString(BackupFile.serializer(), contents) }
            .getOrElse { return Result.failure(BackupError("That file isn't a Food Tracker backup.")) }

        if (parsed.version > BackupFile.CURRENT_VERSION) {
            return Result.failure(
                BackupError("That backup was made by a newer version of the app. Update and try again."),
            )
        }
        return Result.success(parsed)
    }
}

/** Carries a message written for the person reading it, not a stack trace. */
class BackupError(override val message: String) : Exception(message)

/**
 * Export, import and erase.
 *
 * A food diary is months of a person's effort held in one app's private storage, where an
 * uninstall, a lost phone or a signing-key change destroys it silently. Being able to take it
 * somewhere else is the difference between an inconvenience and starting over.
 *
 * Photos are deliberately not included: they dominate the size, and the numbers are what has
 * lasting value. API keys are never exported -- a backup file is not an encrypted store, and
 * writing a credential into a file the user will email to themselves is how credentials leak.
 */
@Singleton
class DiaryBackup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val diaryDao: DiaryDao,
    private val foodDao: FoodDao,
    private val timeProvider: TimeProvider,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun export(fromEpochDay: Long = 0, toEpochDay: Long = Long.MAX_VALUE / 2): String =
        withContext(ioDispatcher) {
            val meals = diaryDao.mealsBetween(fromEpochDay, toEpochDay).map { row ->
                BackupMeal(
                    id = row.meal.id,
                    mealType = row.meal.mealType,
                    epochDay = row.meal.epochDay,
                    loggedAtMillis = row.meal.loggedAtMillis,
                    items = row.items.map { item ->
                        BackupItem(
                            name = item.name,
                            brand = item.brand,
                            foodKey = item.foodKey,
                            amount = item.amount,
                            unit = item.unit,
                            grams = item.grams,
                            calories = item.calories,
                            proteinGrams = item.proteinGrams,
                            carbsGrams = item.carbsGrams,
                            fatGrams = item.fatGrams,
                        )
                    },
                )
            }

            BackupFormat.encode(
                BackupFile(exportedAtMillis = timeProvider.epochMillis(), meals = meals),
            )
        }

    /**
     * Restores meals from a backup. Existing meals are kept: importing is additive, because
     * silently replacing someone's diary with an older file is not recoverable.
     */
    suspend fun import(contents: String): ImportResult = withContext(ioDispatcher) {
        val parsed = BackupFormat.decode(contents).getOrElse { error ->
            return@withContext ImportResult.Failed(
                (error as? BackupError)?.message ?: "That file isn't a Food Tracker backup.",
            )
        }

        // A backup restored twice should not double the day's calories, so ids are preserved
        // and a re-import replaces rather than appends.
        diaryDao.replaceMeals(
            parsed.meals.map { meal ->
                LoggedMealEntity(
                    id = meal.id,
                    mealType = meal.mealType,
                    epochDay = meal.epochDay,
                    loggedAtMillis = meal.loggedAtMillis,
                    photoPath = null,
                ) to meal.items.map { item ->
                    LoggedFoodItemEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        mealId = meal.id,
                        name = item.name,
                        brand = item.brand,
                        foodKey = item.foodKey,
                        cachedFoodId = null,
                        amount = item.amount,
                        unit = item.unit,
                        grams = item.grams,
                        calories = item.calories,
                        proteinGrams = item.proteinGrams,
                        carbsGrams = item.carbsGrams,
                        fatGrams = item.fatGrams,
                        recognitionSource = "USER",
                    )
                }
            },
        )

        ImportResult.Imported(parsed.meals.size)
    }

    /**
     * Erases everything this app holds: the diary, the food cache, what it learned about your
     * portions, and every saved photo. Required by Play's user-data policy, and the right thing to
     * offer regardless.
     */
    suspend fun deleteEverything() = withContext(ioDispatcher) {
        diaryDao.deleteAllMeals()
        diaryDao.deleteAllFavourites()
        foodDao.deleteAllCachedFoods()
        foodDao.deleteAllCorrections()
        foodDao.deleteAllPendingAnalyses()
        photoDirectories().forEach { directory ->
            runCatching { directory.listFiles()?.forEach { it.delete() } }
        }
        Unit
    }

    /**
     * Every directory the app writes a photo into. Named centrally rather than here, so a new one
     * cannot be added elsewhere and quietly escape an erase.
     */
    private fun photoDirectories(): List<File> = listOf(
        File(context.cacheDir, PhotoDirectories.CAPTURES),
        File(context.filesDir, PhotoDirectories.DIARY),
        File(context.filesDir, PhotoDirectories.REANALYSIS_QUEUE),
    )
}
