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

        /**
         * The same units, with the one the reader thinks in first.
         *
         * Someone who set the app to imperial should not have to scroll past grams every time they
         * adjust a portion; the rest stay because a cup of rice is a cup of rice either way.
         */
        fun pickerOrderFor(imperial: Boolean): List<MeasurementUnit> =
            if (imperial) listOf(OUNCE, GRAM, PIECE, CUP, TABLESPOON, SERVING) else pickerOrder

        /** The mass unit portions are shown in by default under each system. */
        fun massUnitFor(imperial: Boolean): MeasurementUnit = if (imperial) OUNCE else GRAM
    }
}
