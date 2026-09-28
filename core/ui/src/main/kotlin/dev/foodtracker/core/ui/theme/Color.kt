package dev.foodtracker.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Fallback brand palette, used only on devices without dynamic colour (API < 31) or when the user
 * turns dynamic colour off. On API 31+ Material You wallpaper colours take over.
 */
internal val BrandGreen = Color(0xFF3F6B3F)
internal val BrandGreenLight = Color(0xFFBFF0B6)
internal val BrandAmber = Color(0xFF7A5900)
internal val BrandAmberLight = Color(0xFFFFDF95)
internal val BrandTeal = Color(0xFF3B6560)
internal val BrandTealLight = Color(0xFFBDEBE4)

/** Macro colours. Kept outside the M3 scheme so protein/carbs/fat stay recognisable in every theme. */
object MacroColors {
    val Protein = Color(0xFF5B8DEF)
    val Carbs = Color(0xFFF2A65A)
    val Fat = Color(0xFFE0685F)

    val ProteinDark = Color(0xFF9EBEFF)
    val CarbsDark = Color(0xFFFFC488)
    val FatDark = Color(0xFFFF9C93)
}
