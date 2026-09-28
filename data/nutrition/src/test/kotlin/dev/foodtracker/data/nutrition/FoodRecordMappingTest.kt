package dev.foodtracker.data.nutrition

import com.google.common.truth.Truth.assertThat
import dev.foodtracker.data.nutrition.off.OffNutriments
import dev.foodtracker.data.nutrition.off.OffProduct
import dev.foodtracker.data.nutrition.off.toFoodRecord
import dev.foodtracker.data.nutrition.usda.UsdaFood
import dev.foodtracker.data.nutrition.usda.UsdaNutrient
import dev.foodtracker.data.nutrition.usda.tidiedUsdaDescription
import dev.foodtracker.data.nutrition.usda.toFoodRecord
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

class FoodRecordMappingTest {

    private fun usdaNutrient(id: Int, value: Double) = UsdaNutrient(id = id, value = value)

    @Test
    fun `usda energy in kilojoules is converted to calories`() {
        // Foundation and SR Legacy rows routinely carry only nutrient 1062 (kJ). Dropping those
        // would leave common staples with no calories at all.
        val food = UsdaFood(
            fdcId = 1,
            description = "Chicken, breast",
            foodNutrients = listOf(
                usdaNutrient(1062, 621.0),
                usdaNutrient(1003, 29.5),
                usdaNutrient(1004, 3.4),
                usdaNutrient(1005, 0.0),
            ),
        )

        val record = food.toFoodRecord()

        assertThat(record).isNotNull()
        assertThat(record!!.per100g.calories).isWithin(0.5).of(148.4)
    }

    @Test
    fun `usda kcal is preferred over kilojoules when both are present`() {
        val food = UsdaFood(
            fdcId = 1,
            description = "Chicken, breast",
            foodNutrients = listOf(
                usdaNutrient(1008, 151.0),
                usdaNutrient(1062, 621.0),
                usdaNutrient(1003, 30.5),
                usdaNutrient(1004, 3.2),
            ),
        )

        assertThat(food.toFoodRecord()!!.per100g.calories).isEqualTo(151.0)
    }

    @Test
    fun `a usda row with no energy at all is dropped rather than logged as zero calories`() {
        val food = UsdaFood(fdcId = 1, description = "Water", foodNutrients = listOf(usdaNutrient(1003, 0.0)))
        assertThat(food.toFoodRecord()).isNull()
    }

    @Test
    fun `usda database descriptions are trimmed to something readable`() {
        val raw = "Chicken, broiler or fryers, breast, skinless, boneless, meat only, cooked, grilled"
        assertThat(raw.tidiedUsdaDescription()).isEqualTo("Chicken, broiler or fryers, breast")
    }

    @Test
    fun `open food facts sodium is converted from grams to milligrams`() {
        val product = OffProduct(
            code = "3017624010701",
            productName = "Nutella",
            brands = JsonPrimitive("Ferrero"),
            nutriments = OffNutriments(
                energyKcal100g = 539.0,
                proteins100g = 6.3,
                carbohydrates100g = 57.5,
                fat100g = 30.9,
                sodium100g = 0.043,
            ),
        )

        val record = product.toFoodRecord()

        assertThat(record).isNotNull()
        // 0.043 g/100g is 43 mg/100g; leaving it in grams would under-report sodium 1000-fold.
        assertThat(record!!.per100g.sodiumMilligrams).isWithin(0.001).of(43.0)
    }

    @Test
    fun `open food facts salt is converted to sodium when sodium is absent`() {
        val product = OffProduct(
            productName = "Something salty",
            nutriments = OffNutriments(
                energyKcal100g = 100.0,
                proteins100g = 5.0,
                carbohydrates100g = 10.0,
                fat100g = 4.0,
                salt100g = 2.5,
            ),
        )

        // 2.5 g salt is 1 g sodium, i.e. 1000 mg.
        assertThat(product.toFoodRecord()!!.per100g.sodiumMilligrams).isWithin(1.0).of(1000.0)
    }

    @Test
    fun `brands parse from both the string and array shapes the two endpoints use`() {
        val fromProductApi = OffProduct(
            productName = "Yoghurt",
            brands = JsonPrimitive("Fage, Total"),
            nutriments = OffNutriments(energyKcal100g = 97.0, proteins100g = 9.0, carbohydrates100g = 3.0, fat100g = 5.0),
        )
        val fromSearchService = OffProduct(
            productName = "Yoghurt",
            brands = JsonArray(listOf(JsonPrimitive("Fage"))),
            nutriments = OffNutriments(energyKcal100g = 97.0, proteins100g = 9.0, carbohydrates100g = 3.0, fat100g = 5.0),
        )

        assertThat(fromProductApi.toFoodRecord()!!.brand).isEqualTo("Fage")
        assertThat(fromSearchService.toFoodRecord()!!.brand).isEqualTo("Fage")
    }

    @Test
    fun `an open food facts row with no nutrition is dropped`() {
        val product = OffProduct(code = "123", productName = "Mystery item", nutriments = null)
        assertThat(product.toFoodRecord()).isNull()
    }

    @Test
    fun `a crowd-sourced row whose energy contradicts its macros is corrected`() {
        // Kilojoules entered into the kcal field: 2228 against macros worth ~533 kcal.
        val product = OffProduct(
            productName = "Nutella",
            nutriments = OffNutriments(
                energyKcal100g = 2228.0,
                proteins100g = 6.3,
                carbohydrates100g = 57.5,
                fat100g = 30.9,
            ),
        )

        assertThat(product.toFoodRecord()!!.per100g.calories).isWithin(2.0).of(533.0)
    }

    @Test
    fun `a record round-trips through the cache entity unchanged`() {
        val record = FoodRecord(
            id = "usda:1",
            name = "Chicken breast",
            brand = null,
            barcode = null,
            source = dev.foodtracker.core.database.entity.FoodSource.USDA,
            searchKey = "chicken breast",
            per100g = dev.foodtracker.core.model.Nutrients(165.0, 31.0, 0.0, 3.6, 0.0, 0.0, 74.0),
            servingSizeGrams = 172.0,
            servingDescription = "1 breast",
        )

        assertThat(record.toEntity(nowMillis = 1L).toRecord()).isEqualTo(record)
    }
}
