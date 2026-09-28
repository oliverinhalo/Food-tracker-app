package dev.foodtracker.data.nutrition

import dev.foodtracker.core.common.TimeProvider
import dev.foodtracker.core.database.dao.FoodDao
import dev.foodtracker.core.database.entity.PortionCorrectionEntity
import dev.foodtracker.core.model.MeasurementUnit
import dev.foodtracker.core.network.NetworkMonitor
import dev.foodtracker.data.nutrition.off.OpenFoodFactsClient
import dev.foodtracker.data.nutrition.usda.UsdaClient
import dev.foodtracker.domain.nutrition.FoodMatcher
import dev.foodtracker.domain.nutrition.LearnedPortion
import dev.foodtracker.domain.nutrition.MatchCandidate
import dev.foodtracker.domain.nutrition.PortionCorrection
import dev.foodtracker.domain.nutrition.PortionLearner
import dev.foodtracker.domain.nutrition.PortionOverrideStore
import dev.foodtracker.domain.nutrition.foodKeyOf
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves foods to nutrition, cache first.
 *
 * Order matters for how the app feels: a cached hit returns without touching the network, which is
 * what makes a repeat food resolve instantly and keeps the app usable offline. Only a miss goes
 * out, and then USDA and Open Food Facts are queried together rather than in sequence, because
 * waiting for a USDA miss before trying OFF would double the latency of every branded lookup.
 */
@Singleton
class NutritionRepository @Inject constructor(
    private val foodDao: FoodDao,
    private val usdaClient: UsdaClient,
    private val offClient: OpenFoodFactsClient,
    private val networkMonitor: NetworkMonitor,
    private val timeProvider: TimeProvider,
) {
    private val learner = PortionLearner()
    private val matcher = FoodMatcher()

    /**
     * Best single match for a recognised food label.
     *
     * The search itself is only a shortlist: both databases rank by text relevance, which answers
     * "steamed broccoli" with steamed corn and "white rice" with rice flour. [FoodMatcher] decides
     * which row is actually the food, and returning null is preferable to returning a confident
     * wrong number.
     */
    suspend fun resolve(name: String, brand: String? = null, cookingMethod: String? = null): FoodRecord? {
        val key = foodKeyOf(name, brand)

        foodDao.byExactKey(key)?.let { return it.toRecord() }

        if (!networkMonitor.isCurrentlyOnline()) {
            // Offline: a fuzzy cache hit is far better than no calories at all, but it still has
            // to survive matching, or we would log whatever happens to share a word.
            val cached = foodDao.search(key, limit = CANDIDATE_POOL).map { it.toRecord() }
            return matcher.bestMatch(name, cached.toCandidates(), cookingMethod, preferBranded = !brand.isNullOrBlank())
        }

        // A wider pool than the user will ever see: the right row is often several places down.
        val candidates = search(name, brand, limit = CANDIDATE_POOL)
        return matcher.bestMatch(
            query = name,
            candidates = candidates.toCandidates(),
            cookingMethod = cookingMethod,
            preferBranded = !brand.isNullOrBlank(),
        )
    }

    /**
     * Search across both databases. Branded queries put Open Food Facts first because that is
     * where barcoded products live; generic queries prefer USDA, whose data is curated.
     */
    suspend fun search(query: String, brand: String? = null, limit: Int = 10): List<FoodRecord> {
        if (query.isBlank()) return emptyList()

        if (!networkMonitor.isCurrentlyOnline()) {
            return foodDao.search(foodKeyOf(query, brand), limit).map { it.toRecord() }
        }

        val combinedQuery = listOfNotNull(brand, query).joinToString(" ")

        val (usda, off) = coroutineScope {
            val usdaResults = async { usdaClient.search(query, limit) }
            val offResults = async { offClient.search(combinedQuery, limit) }
            usdaResults.await() to offResults.await()
        }

        val ordered = if (brand.isNullOrBlank()) usda + off else off + usda
        val deduped = ordered.distinctBy { it.searchKey }

        cache(deduped)
        return deduped.take(limit)
    }

    /** Barcode lookup. Cached results win, so a re-scanned product resolves without a round trip. */
    suspend fun byBarcode(barcode: String): FoodRecord? {
        foodDao.byBarcode(barcode)?.let { return it.toRecord() }
        if (!networkMonitor.isCurrentlyOnline()) return null

        val record = offClient.byBarcode(barcode) ?: return null
        cache(listOf(record))
        return record
    }

    private companion object {
        /** Shortlist depth handed to the matcher; the right row is often several places down. */
        const val CANDIDATE_POOL = 25
    }

    /** A previously cached food by id, for recents and favourites. */
    suspend fun cachedById(id: String): FoodRecord? = foodDao.byId(id)?.toRecord()

    suspend fun cache(records: List<FoodRecord>) {
        if (records.isEmpty()) return
        foodDao.upsertAll(records.map { it.toEntity(timeProvider.epochMillis()) })
    }

    /** Records a portion correction, but only one big enough to say something about a habit. */
    suspend fun recordCorrection(
        foodKey: String,
        unit: MeasurementUnit,
        estimatedGrams: Double,
        correctedGrams: Double,
    ) {
        val correction = PortionCorrection(
            foodKey = foodKey,
            unit = unit,
            estimatedGrams = estimatedGrams,
            correctedGrams = correctedGrams,
            recordedAtMillis = timeProvider.epochMillis(),
        )
        if (!learner.isSignificant(correction)) return

        foodDao.insertCorrection(
            PortionCorrectionEntity(
                foodKey = foodKey,
                unit = unit.name,
                estimatedGrams = estimatedGrams,
                correctedGrams = correctedGrams,
                recordedAtMillis = correction.recordedAtMillis,
            ),
        )
    }

    suspend fun learnedPortionsFor(foodKey: String): List<LearnedPortion> {
        val corrections = foodDao.correctionsFor(foodKey).mapNotNull { it.toDomain() }
        return learner.learn(corrections, timeProvider.epochMillis())
    }

    /** Applies what we know about this user's portions to a fresh estimate. */
    suspend fun biasEstimate(foodKey: String, unit: MeasurementUnit, estimatedGrams: Double): Double {
        val learned = learnedPortionsFor(foodKey).firstOrNull { it.unit == unit }
        return learner.bias(estimatedGrams, learned)
    }

    suspend fun overrideStoreFor(foodKeys: Collection<String>): PortionOverrideStore {
        val learned = foodKeys.flatMap { learnedPortionsFor(it) }
            .associateBy { it.foodKey to it.unit }

        return object : PortionOverrideStore {
            override fun gramsPerUnit(foodKey: String, unit: MeasurementUnit): Double? =
                learned[foodKey to unit]?.grams
        }
    }
}

private fun List<FoodRecord>.toCandidates(): List<MatchCandidate<FoodRecord>> = map { record ->
    MatchCandidate(
        value = record,
        name = record.name,
        caloriesPer100g = record.per100g.calories,
        isBranded = record.isBranded,
    )
}

private fun PortionCorrectionEntity.toDomain(): PortionCorrection? {
    val parsedUnit = MeasurementUnit.entries.firstOrNull { it.name == unit } ?: return null
    return PortionCorrection(
        foodKey = foodKey,
        unit = parsedUnit,
        estimatedGrams = estimatedGrams,
        correctedGrams = correctedGrams,
        recordedAtMillis = recordedAtMillis,
    )
}
