package dev.foodtracker.feature.results

/** Stable handles for the bottom-sheet UI tests. Kept in one place so tests and UI cannot drift. */
object ResultsTestTags {
    const val SHEET = "results_sheet"
    const val TOTALS_CALORIES = "results_totals_calories"
    const val ITEM_LIST = "results_item_list"
    const val ADD_ITEM = "results_add_item"
    const val CONFIRM = "results_confirm"
    const val DEGRADE_BANNER = "results_degrade_banner"
    const val MEAL_TYPE_ROW = "results_meal_type_row"

    fun itemCard(id: String) = "results_item_$id"
    fun itemIncrement(id: String) = "results_item_inc_$id"
    fun itemDecrement(id: String) = "results_item_dec_$id"
    fun itemSlider(id: String) = "results_item_slider_$id"
    fun itemUnitPicker(id: String) = "results_item_unit_$id"
    fun itemAlternatives(id: String) = "results_item_alts_$id"
}
