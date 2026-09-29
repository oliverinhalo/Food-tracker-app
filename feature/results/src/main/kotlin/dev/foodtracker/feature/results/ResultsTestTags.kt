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
    const val STAGE_LABEL = "results_stage_label"
    const val ANALYZING = "results_analyzing"
    const val PICKER_SHEET = "results_picker_sheet"
    const val PICKER_SEARCH_FIELD = "results_picker_search"
    const val PICKER_RESULTS = "results_picker_results"
    const val PICKER_SCAN = "results_picker_scan"
    const val UNDO_BAR = "results_undo_bar"
    const val UNDO_BUTTON = "results_undo_button"

    fun itemCard(id: String) = "results_item_$id"
    fun itemIncrement(id: String) = "results_item_inc_$id"
    fun itemDecrement(id: String) = "results_item_dec_$id"
    fun itemSlider(id: String) = "results_item_slider_$id"
    fun itemUnitPicker(id: String) = "results_item_unit_$id"
    fun itemAlternatives(id: String) = "results_item_alts_$id"
}
