package dev.foodtracker.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import dev.foodtracker.core.database.dao.DiaryDao
import dev.foodtracker.core.database.dao.FoodDao
import dev.foodtracker.core.database.entity.CachedFoodEntity
import dev.foodtracker.core.database.entity.FavouriteFoodEntity
import dev.foodtracker.core.database.entity.FoodSource
import dev.foodtracker.core.database.entity.LoggedFoodItemEntity
import dev.foodtracker.core.database.entity.LoggedMealEntity
import dev.foodtracker.core.database.entity.PortionCorrectionEntity

class Converters {
    @TypeConverter
    fun fromFoodSource(value: FoodSource): String = value.name

    @TypeConverter
    fun toFoodSource(value: String): FoodSource =
        FoodSource.entries.firstOrNull { it.name == value } ?: FoodSource.MANUAL
}

@Database(
    entities = [
        CachedFoodEntity::class,
        PortionCorrectionEntity::class,
        LoggedMealEntity::class,
        LoggedFoodItemEntity::class,
        FavouriteFoodEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FoodTrackerDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun diaryDao(): DiaryDao

    companion object {
        const val NAME = "food-tracker.db"
    }
}
