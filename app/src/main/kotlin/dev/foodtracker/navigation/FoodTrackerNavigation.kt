package dev.foodtracker.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.foodtracker.feature.capture.CaptureRoute
import dev.foodtracker.feature.home.HomeRoute
import dev.foodtracker.feature.settings.SettingsRoute
import kotlinx.serialization.Serializable

@Serializable
data object HomeRouteKey

@Serializable
data object CaptureRouteKey

@Serializable
data object SettingsRouteKey

/**
 * The results sheet is a destination rather than state inside the camera screen: it must survive
 * configuration change and process death, and it is reachable from re-analysis later on.
 */
@Serializable
data class ResultsRouteKey(val captureId: String)

@Composable
fun FoodTrackerNavHost(
    navController: NavHostController,
    onShowResults: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = HomeRouteKey,
        modifier = modifier,
    ) {
        composable<HomeRouteKey> {
            HomeRoute()
        }

        composable<CaptureRouteKey> {
            CaptureRoute(onCaptureReady = onShowResults)
        }

        composable<SettingsRouteKey> {
            SettingsRoute()
        }
    }
}
