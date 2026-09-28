package dev.foodtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.foodtracker.core.ui.theme.FoodTrackerTheme
import dev.foodtracker.feature.results.ResultsBottomSheet
import dev.foodtracker.feature.results.ResultsViewModel
import dev.foodtracker.feature.settings.SettingsViewModel
import dev.foodtracker.navigation.CaptureRouteKey
import dev.foodtracker.navigation.FoodTrackerNavHost
import dev.foodtracker.navigation.HistoryRouteKey
import dev.foodtracker.navigation.HomeRouteKey
import dev.foodtracker.navigation.SettingsRouteKey

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()

            FoodTrackerTheme(dynamicColor = settings.dynamicColor) {
                FoodTrackerApp()
            }
        }
    }
}

// The sheet's default state comes from rememberModalBottomSheetState, which is still experimental.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodTrackerApp() {
    val navController = rememberNavController()
    var pendingCaptureId by remember { mutableStateOf<String?>(null) }
    var manualEntryOpen by remember { mutableStateOf(false) }
    val resultsViewModel: ResultsViewModel = hiltViewModel()

    Scaffold(
        bottomBar = { BottomBar(navController) },
    ) { padding ->
        FoodTrackerNavHost(
            navController = navController,
            onShowResults = { pendingCaptureId = it },
            onAddManually = {
                manualEntryOpen = true
                resultsViewModel.startManualEntry()
            },
            modifier = Modifier.padding(padding),
        )
    }

    if (pendingCaptureId != null || manualEntryOpen) {
        val state by resultsViewModel.uiState.collectAsStateWithLifecycle()

        pendingCaptureId?.let { captureId ->
            LaunchedEffect(captureId) { resultsViewModel.analyze(captureId) }
        }

        ResultsBottomSheet(
            state = state,
            onAction = resultsViewModel::onAction,
            onBarcodeScanned = resultsViewModel::onBarcodeScanned,
            onBarcodeScanCancelled = resultsViewModel::onBarcodeScanCancelled,
            onDismiss = {
                val cameFromCapture = pendingCaptureId != null
                pendingCaptureId = null
                manualEntryOpen = false
                if (cameFromCapture) {
                    navController.navigate(HomeRouteKey) {
                        popUpTo(HomeRouteKey) { inclusive = true }
                    }
                }
            },
        )
    }
}

@Composable
private fun BottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    NavigationBar {
        NavigationBarItem(
            selected = destination?.hasRoute(HomeRouteKey::class) == true,
            onClick = { navController.navigateSingleTop(HomeRouteKey) },
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text("Home") },
        )
        NavigationBarItem(
            selected = destination?.hasRoute(CaptureRouteKey::class) == true,
            onClick = { navController.navigateSingleTop(CaptureRouteKey) },
            icon = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
            label = { Text("Scan") },
        )
        NavigationBarItem(
            selected = destination?.hasRoute(HistoryRouteKey::class) == true,
            onClick = { navController.navigateSingleTop(HistoryRouteKey) },
            icon = { Icon(Icons.Default.Timeline, contentDescription = null) },
            label = { Text("History") },
        )
        NavigationBarItem(
            selected = destination?.hasRoute(SettingsRouteKey::class) == true,
            onClick = { navController.navigateSingleTop(SettingsRouteKey) },
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text("Settings") },
        )
    }
}

private fun NavHostController.navigateSingleTop(route: Any) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
