package dev.foodtracker.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.foodtracker.core.ui.component.LocalHapticsEnabled

private val LightScheme = lightColorScheme(
    primary = BrandGreen,
    primaryContainer = BrandGreenLight,
    secondary = BrandTeal,
    secondaryContainer = BrandTealLight,
    tertiary = BrandAmber,
    tertiaryContainer = BrandAmberLight,
)

private val DarkScheme = darkColorScheme(
    primary = BrandGreenLight,
    primaryContainer = BrandGreen,
    secondary = BrandTealLight,
    secondaryContainer = BrandTeal,
    tertiary = BrandAmberLight,
    tertiaryContainer = BrandAmber,
)

/** Macro colours resolved for the current theme, reachable from any composable. */
data class MacroPalette(val protein: Color, val carbs: Color, val fat: Color)

val LocalMacroPalette = staticCompositionLocalOf {
    MacroPalette(MacroColors.Protein, MacroColors.Carbs, MacroColors.Fat)
}

object FoodTrackerTheme {
    val macros: MacroPalette
        @Composable @ReadOnlyComposable get() = LocalMacroPalette.current
}

@Composable
fun FoodTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    hapticsEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkScheme
        else -> LightScheme
    }

    val macros = if (darkTheme) {
        MacroPalette(MacroColors.ProteinDark, MacroColors.CarbsDark, MacroColors.FatDark)
    } else {
        MacroPalette(MacroColors.Protein, MacroColors.Carbs, MacroColors.Fat)
    }

    CompositionLocalProvider(
        LocalMacroPalette provides macros,
        LocalHapticsEnabled provides hapticsEnabled,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FoodTrackerTypography,
            content = content,
        )
    }
}
