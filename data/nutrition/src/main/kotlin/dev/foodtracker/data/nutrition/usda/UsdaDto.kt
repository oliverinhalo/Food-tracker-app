package dev.foodtracker.data.nutrition.usda

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class UsdaSearchResponse(
    val totalHits: Int = 0,
    val foods: List<UsdaFood> = emptyList(),
)

@Serializable
internal data class UsdaFood(
    val fdcId: Long = 0,
    val description: String = "",
    val dataType: String? = null,
    val brandOwner: String? = null,
    val brandName: String? = null,
    val gtinUpc: String? = null,
    val servingSize: Double? = null,
    val servingSizeUnit: String? = null,
    val householdServingFullText: String? = null,
    val foodNutrients: List<UsdaNutrient> = emptyList(),
)

@Serializable
internal data class UsdaNutrient(
    @SerialName("nutrientId") val id: Int? = null,
    @SerialName("nutrientName") val name: String? = null,
    @SerialName("unitName") val unit: String? = null,
    val value: Double? = null,
    // The /food/{id} endpoint nests the same data differently from /foods/search.
    val nutrient: UsdaNestedNutrient? = null,
    val amount: Double? = null,
)

@Serializable
internal data class UsdaNestedNutrient(
    val id: Int? = null,
    val name: String? = null,
    val unitName: String? = null,
)

/**
 * USDA nutrient numbers. These are stable identifiers in the FoodData Central schema, which is why
 * we key off them rather than the display names.
 */
internal object UsdaNutrientIds {
    const val PROTEIN = 1003
    const val FAT = 1004
    const val CARBS = 1005
    const val ENERGY_KCAL = 1008
    const val ENERGY_KJ = 1062
    const val FIBER = 1079
    const val SUGARS = 2000
    const val SUGARS_ALT = 1063
    const val SODIUM = 1093

    /** Foundation and SR Legacy rows frequently carry energy only in kilojoules. */
    const val KJ_PER_KCAL = 4.184
}
