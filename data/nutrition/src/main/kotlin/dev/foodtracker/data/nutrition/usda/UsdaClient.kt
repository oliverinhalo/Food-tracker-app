package dev.foodtracker.data.nutrition.usda

import dev.foodtracker.core.common.di.IoDispatcher
import dev.foodtracker.core.database.entity.FoodSource
import dev.foodtracker.core.datastore.SecureKeyStore
import dev.foodtracker.core.model.Nutrients
import dev.foodtracker.data.nutrition.BuildConfig
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
 * USDA FoodData Central: the authority for generic, unbranded foods.
 *
 * Search is restricted to Foundation and SR Legacy data types. The Branded type is far larger but
 * is manufacturer-submitted and much noisier, and branded products are better served by Open Food
 * Facts, which has barcodes.
 */
@Singleton
class UsdaClient @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val secureKeyStore: SecureKeyStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * A key entered in Settings wins over the build-time one. Release builds are produced by CI,
     * which has no local.properties, so without a runtime option the shipped app would have no
     * USDA access at all -- and USDA is where every generic, unbranded food comes from.
     */
    private val apiKey: String? get() = secureKeyStore.usdaApiKey() ?: BuildConfig.USDA_API_KEY.takeIf { it.isNotBlank() }

    val isConfigured: Boolean get() = apiKey != null

    suspend fun search(query: String, limit: Int = 10): List<FoodRecord> = withContext(ioDispatcher) {
        val key = apiKey ?: return@withContext emptyList()
        if (query.isBlank()) return@withContext emptyList()

        val url = "$BASE_URL/foods/search".toHttpUrl().newBuilder()
            .addQueryParameter("api_key", key)
            .addQueryParameter("query", query)
            .addQueryParameter("pageSize", limit.toString())
            .addQueryParameter("dataType", "Foundation,SR Legacy")
            .build()

        val body = runCatching { get(url.toString()) }.getOrNull() ?: return@withContext emptyList()
        val parsed = runCatching { json.decodeFromString<UsdaSearchResponse>(body) }.getOrNull()
            ?: return@withContext emptyList()

        parsed.foods.mapNotNull { it.toFoodRecord() }
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("USDA returned ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    private companion object {
        const val BASE_URL = "https://api.nal.usda.gov/fdc/v1"
    }
}

internal fun UsdaFood.toFoodRecord(): FoodRecord? {
    if (description.isBlank()) return null

    val values = foodNutrients.associate { nutrient ->
        val id = nutrient.id ?: nutrient.nutrient?.id
        val value = nutrient.value ?: nutrient.amount
        id to value
    }

    fun value(id: Int): Double? = values[id]?.takeIf { it.isFinite() }

    // Foundation and SR Legacy rows often carry energy only as kilojoules; converting is the
    // difference between a correct calorie count and none at all.
    val calories = value(UsdaNutrientIds.ENERGY_KCAL)
        ?: value(UsdaNutrientIds.ENERGY_KJ)?.div(UsdaNutrientIds.KJ_PER_KCAL)
        ?: return null

    val nutrients = Nutrients(
        calories = calories,
        proteinGrams = value(UsdaNutrientIds.PROTEIN) ?: 0.0,
        carbsGrams = value(UsdaNutrientIds.CARBS) ?: 0.0,
        fatGrams = value(UsdaNutrientIds.FAT) ?: 0.0,
        fiberGrams = value(UsdaNutrientIds.FIBER),
        sugarGrams = value(UsdaNutrientIds.SUGARS) ?: value(UsdaNutrientIds.SUGARS_ALT),
        sodiumMilligrams = value(UsdaNutrientIds.SODIUM),
    )

    val brand = brandName?.takeIf { it.isNotBlank() } ?: brandOwner?.takeIf { it.isNotBlank() }

    return FoodRecord(
        id = "usda:$fdcId",
        name = description.tidiedUsdaDescription(),
        brand = brand,
        barcode = gtinUpc?.takeIf { it.isNotBlank() },
        source = FoodSource.USDA,
        searchKey = foodKeyOf(description, brand),
        // USDA values are per 100 g by definition for these data types.
        per100g = NutritionCalculator.withReconciledEnergy(nutrients),
        servingSizeGrams = servingSize?.takeIf { servingSizeUnit.equals("g", ignoreCase = true) },
        servingDescription = householdServingFullText?.takeIf { it.isNotBlank() },
    )
}

/**
 * USDA descriptions are written for a database, not a person: "Chicken, broiler or fryers, breast,
 * skinless, boneless, meat only, cooked, grilled". Keeping the leading noun and the first couple of
 * qualifiers reads far better in a list without losing what the food actually is.
 */
internal fun String.tidiedUsdaDescription(): String {
    val parts = split(',').map { it.trim() }.filter { it.isNotBlank() }
    if (parts.size <= 2) return joinTitleCase(parts)
    return joinTitleCase(parts.take(3))
}

private fun joinTitleCase(parts: List<String>): String =
    parts.joinToString(", ").replaceFirstChar { it.uppercase() }
