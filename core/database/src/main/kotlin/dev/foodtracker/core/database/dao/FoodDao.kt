package dev.foodtracker.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.foodtracker.core.database.entity.CachedFoodEntity
import dev.foodtracker.core.database.entity.PortionCorrectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(food: CachedFoodEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(foods: List<CachedFoodEntity>)

    @Query("SELECT * FROM cached_foods WHERE id = :id")
    suspend fun byId(id: String): CachedFoodEntity?

    @Query("SELECT * FROM cached_foods WHERE barcode = :barcode LIMIT 1")
    suspend fun byBarcode(barcode: String): CachedFoodEntity?

    @Query("SELECT * FROM cached_foods WHERE searchKey = :searchKey ORDER BY cachedAtMillis DESC LIMIT 1")
    suspend fun byExactKey(searchKey: String): CachedFoodEntity?

    /**
     * Offline search. Ordered so that a prefix match outranks a match buried mid-string, which is
     * what makes typing "chi" put chicken above "sweet chilli sauce".
     */
    @Query(
        """
        SELECT * FROM cached_foods
        WHERE searchKey LIKE '%' || :query || '%'
        ORDER BY
            CASE WHEN searchKey LIKE :query || '%' THEN 0 ELSE 1 END,
            LENGTH(searchKey),
            cachedAtMillis DESC
        LIMIT :limit
        """,
    )
    suspend fun search(query: String, limit: Int = 20): List<CachedFoodEntity>

    @Query("DELETE FROM cached_foods WHERE cachedAtMillis < :olderThanMillis AND barcode IS NULL")
    suspend fun evictOlderThan(olderThanMillis: Long): Int

    @Insert
    suspend fun insertCorrection(correction: PortionCorrectionEntity)

    @Query("SELECT * FROM portion_corrections WHERE foodKey = :foodKey")
    suspend fun correctionsFor(foodKey: String): List<PortionCorrectionEntity>

    @Query("SELECT * FROM portion_corrections")
    fun allCorrections(): Flow<List<PortionCorrectionEntity>>
}
