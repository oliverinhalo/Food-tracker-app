package dev.foodtracker.data.nutrition.off

import dev.foodtracker.core.common.di.IoDispatcher
import dev.foodtracker.core.database.entity.FoodSource
import dev.foodtracker.core.model.Nutrients
import dev.foodtracker.data.nutrition.FoodRecord
import dev.foodtracker.domain.nutrition.NutritionCalculator
import dev.foodtracker.domain.nutrition.foodKeyOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Open Food Facts: branded and packaged products, reachable by barcode.
 *
 * Two different hosts are used on purpose. Barcode lookup goes to the main API, which is reliable.
 * Text search does NOT: `world.openfoodfacts.org`'s search endpoints (both the v2 API and the
 * legacy cgi/search.pl) return 503 for text queries, so brand search goes to the dedicated
 * search service instead. Pointing both at the obvious host would ship a brand search that is
 * always empty.
 */
@Singleton
class OpenFoodFactsClient @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun byBarcode(barcode: String): FoodRecord? = withContext(ioDispatcher) {
        if (barcode.isBlank()) return@withContext null

        val url = "$PRODUCT_HOST/api/v2/product/$barcode.json".toHttpUrl().newBuilder()
            .addQueryParameter("fields", PRODUCT_FIELDS)
            .build()

        val body = runCatching { get(url.toString()) }.getOrNull() ?: return@withContext null
        val parsed = runCatching { json.decodeFromString<OffProductResponse>(body) }.getOrNull()
            ?: return@withContext null

        if (parsed.status != 1) return@withContext null
        parsed.product?.toFoodRecord()
    }

    suspend fun search(query: String, limit: Int = 10): List<FoodRecord> = withContext(ioDispatcher) {
        if (query.isBlank()) return@withContext emptyList()

        val url = "$SEARCH_HOST/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page_size", limit.toString())
            .addQueryParameter("fields", PRODUCT_FIELDS)
            .build()

        val body = runCatching { get(url.toString()) }.getOrNull() ?: return@withContext emptyList()
        val parsed = runCatching { json.decodeFromString<OffSearchResponse>(body) }.getOrNull()
            ?: return@withContext emptyList()

        // Many indexed products carry no nutrition at all; showing them would be a dead end for
        // the user, so they are dropped rather than listed as "0 kcal".
        parsed.hits.mapNotNull { it.toFoodRecord() }
    }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            // Open Food Facts asks clients to identify themselves and throttles those that do not.
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Open Food Facts returned ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    private companion object {
        const val PRODUCT_HOST = "https://world.openfoodfacts.org"
        const val SEARCH_HOST = "https://search.openfoodfacts.org"
        const val USER_AGENT = "FoodTracker/0.1 (github.com/oliverinhalo/Food-tracker-app)"
        const val PRODUCT_FIELDS = "code,product_name,brands,quantity,serving_size,serving_quantity,nutriments"
    }
}

internal fun OffProduct.toFoodRecord(): FoodRecord? {
    val name = productName?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val nutriments = nutriments ?: return null

    val calories = nutriments.energyKcal100g
        ?: nutriments.energyKj100g?.div(KJ_PER_KCAL)
        ?: return null

    val nutrients = Nutrients(
        calories = calories,
        proteinGrams = nutriments.proteins100g ?: 0.0,
        carbsGrams = nutriments.carbohydrates100g ?: 0.0,
        fatGrams = nutriments.fat100g ?: 0.0,
        fiberGrams = nutriments.fiber100g,
        sugarGrams = nutriments.sugars100g,
        // OFF publishes sodium in grams; the rest of the app uses milligrams.
        sodiumMilligrams = nutriments.sodium100g?.times(1000)
            ?: nutriments.salt100g?.times(1000 / SALT_TO_SODIUM),
    )

    val brand = brandLabel

    return FoodRecord(
        id = "off:${code ?: name}",
        name = name,
        brand = brand,
        barcode = code?.takeIf { it.isNotBlank() },
        source = FoodSource.OPEN_FOOD_FACTS,
        searchKey = foodKeyOf(name, brand),
        // Crowd-sourced rows regularly carry an energy value that contradicts their own macros,
        // which would quietly wreck a day's total.
        per100g = NutritionCalculator.withReconciledEnergy(nutrients),
        servingSizeGrams = servingGrams,
        servingDescription = servingSize?.trim()?.takeIf { it.isNotBlank() },
    )
}

private const val KJ_PER_KCAL = 4.184

/** Salt is sodium chloride: 1 g sodium is 2.5 g salt. */
private const val SALT_TO_SODIUM = 2.5
