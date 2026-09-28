package dev.foodtracker.feature.results

import dev.foodtracker.core.model.MeasurementUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Number formatting for the sheet. Grams and calories are whole numbers -- a portion shown as
 * "172.4 g" implies a precision the estimate does not have -- while fractional units keep one or
 * two decimals so a quarter cup is representable.
 */
internal object ResultsFormatting {

    fun amount(value: Double, unit: MeasurementUnit): String = when (unit) {
        MeasurementUnit.GRAM -> value.roundToInt().toString()
        MeasurementUnit.OUNCE, MeasurementUnit.TABLESPOON, MeasurementUnit.SERVING ->
            trimTrailingZeros(String.format(Locale.US, "%.1f", value))
        MeasurementUnit.CUP -> trimTrailingZeros(String.format(Locale.US, "%.2f", value))
        MeasurementUnit.PIECE ->
            if (abs(value - value.roundToInt()) < 0.01) {
                value.roundToInt().toString()
            } else {
                trimTrailingZeros(String.format(Locale.US, "%.1f", value))
            }
    }

    fun calories(value: Double): String = value.roundToInt().toString()

    fun grams(value: Double): String = "${value.roundToInt()} g"

    fun portionLabel(amount: Double, unit: MeasurementUnit): String =
        "${amount(amount, unit)} ${unit.abbreviation}"

    private fun trimTrailingZeros(text: String): String =
        if (text.contains('.')) text.trimEnd('0').trimEnd('.') else text
}
