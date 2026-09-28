package dev.foodtracker.data.nutrition.off

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

@Serializable
internal data class OffProductResponse(
    val status: Int = 0,
    val product: OffProduct? = null,
)

@Serializable
internal data class OffSearchResponse(
    val count: Int = 0,
    val hits: List<OffProduct> = emptyList(),
)

@Serializable
internal data class OffProduct(
    val code: String? = null,
    @SerialName("product_name") val productName: String? = null,
    /**
     * The product endpoint returns this as a comma-separated string, the search service as an
     * array. [brandLabel] normalises the two.
     */
    val brands: JsonElement? = null,
    val quantity: String? = null,
    @SerialName("serving_size") val servingSize: String? = null,
    @SerialName("serving_quantity") val servingQuantity: JsonElement? = null,
    val nutriments: OffNutriments? = null,
)

@Serializable
internal data class OffNutriments(
    @SerialName("energy-kcal_100g") val energyKcal100g: Double? = null,
    @SerialName("energy-kj_100g") val energyKj100g: Double? = null,
    @SerialName("proteins_100g") val proteins100g: Double? = null,
    @SerialName("carbohydrates_100g") val carbohydrates100g: Double? = null,
    @SerialName("fat_100g") val fat100g: Double? = null,
    @SerialName("fiber_100g") val fiber100g: Double? = null,
    @SerialName("sugars_100g") val sugars100g: Double? = null,
    /** Open Food Facts reports sodium in GRAMS per 100 g, unlike USDA's milligrams. */
    @SerialName("sodium_100g") val sodium100g: Double? = null,
    @SerialName("salt_100g") val salt100g: Double? = null,
)

/** Handles both the string and array shapes the two endpoints use for `brands`. */
internal val OffProduct.brandLabel: String?
    get() = when (val value = brands) {
        is JsonPrimitive -> value.content.split(',').firstOrNull()?.trim()
        is JsonArray -> value.firstOrNull()?.let { (it as? JsonPrimitive)?.content }?.trim()
        else -> null
    }?.takeIf { it.isNotBlank() }

internal val OffProduct.servingGrams: Double?
    get() = (servingQuantity as? JsonPrimitive)?.content?.toDoubleOrNull()?.takeIf { it > 0 }
