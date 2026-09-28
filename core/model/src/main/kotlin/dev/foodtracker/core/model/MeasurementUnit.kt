package dev.foodtracker.core.model

/**
 * Units the user can pick in the results sheet. [GRAM] and [OUNCE] are absolute mass units and
 * convert without knowing anything about the food; the rest are volume or count units whose gram
 * value depends on the food itself (see the density tables in `:domain:nutrition`).
 */
enum class MeasurementUnit(val abbreviation: String, val isAbsoluteMass: Boolean) {
    GRAM("g", true),
    OUNCE("oz", true),
    PIECE("piece", false),
    CUP("cup", false),
    TABLESPOON("tbsp", false),
    SERVING("serving", false),
    ;

    companion object {
        /** Units offered in the unit picker, in display order. */
        val pickerOrder: List<MeasurementUnit> = listOf(GRAM, OUNCE, PIECE, CUP, TABLESPOON, SERVING)
    }
}
