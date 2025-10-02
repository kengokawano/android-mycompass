package jp.saitama.orange.mycompass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import jp.saitama.orange.mycompass.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    // Share ViewModels across navigation
    val compassViewModel: CompassViewModel = viewModel()
    val destinationViewModel: DestinationViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()

    NavHost(navController = navController, startDestination = "compass") {
        composable("compass") {
            CompassApp(
                onNavigateToMap = {
                    navController.navigate("map")
                },
                onNavigateToAbout = {
                    navController.navigate("about")
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                },
                compassViewModel = compassViewModel,
                destinationViewModel = destinationViewModel,
                settingsViewModel = settingsViewModel
            )
        }
        composable("map") {
            MapScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                destinationViewModel = destinationViewModel
            )
        }
        composable("about") {
            AboutScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        composable("settings") {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                settingsViewModel = settingsViewModel
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompassApp(
    onNavigateToMap: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToSettings: () -> Unit,
    compassViewModel: CompassViewModel,
    destinationViewModel: DestinationViewModel,
    settingsViewModel: SettingsViewModel
) {
    val azimuth by compassViewModel.azimuth.collectAsState()
    val sensorAvailable by compassViewModel.sensorAvailable.collectAsState()
    val currentLocation by compassViewModel.currentLocation.collectAsState()
    val destinationInfoList by compassViewModel.destinationInfoList.collectAsState()
    val destinations by destinationViewModel.destinations.collectAsState()
    val arEnabled by settingsViewModel.arEnabled.collectAsState()

    // Update destinations in compass viewmodel when location or destinations change
    LaunchedEffect(currentLocation, destinations) {
        if (currentLocation != null) {
            compassViewModel.updateDestinations(destinations)
        }
    }

    DisposableEffect(Unit) {
        compassViewModel.startListening()
        onDispose {
            compassViewModel.stopListening()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compass App") },
                actions = {
                    IconButton(onClick = onNavigateToMap) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = "Open Map"
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                    IconButton(onClick = onNavigateToAbout) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "About"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            if (!sensorAvailable) {
                Text(
                    text = "Sensor not available on this device",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                // 3D Compass View
                Compass3DView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    azimuth = azimuth,
                    arEnabled = arEnabled
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 2D Compass Meter
                CompassMeter(
                    azimuth = azimuth,
                    destinationInfoList = destinationInfoList
                )
            }
        }
    }
}