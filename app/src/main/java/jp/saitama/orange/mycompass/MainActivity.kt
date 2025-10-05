package jp.saitama.orange.mycompass

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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

// Factory for DestinationViewModel
class DestinationViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DestinationViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DestinationViewModel(DestinationDataStore(application)) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val application = context.applicationContext as Application

    // Share ViewModels across navigation
    val compassViewModel: CompassViewModel = viewModel()
    val destinationViewModel: DestinationViewModel = viewModel(factory = DestinationViewModelFactory(application))
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
    // removed AR setting
    val pitch by compassViewModel.pitch.collectAsState()
    val roll by compassViewModel.roll.collectAsState()
    val headingAccuracyDeg by compassViewModel.headingAccuracyDeg.collectAsState()
    val sensorAccuracy by compassViewModel.sensorAccuracy.collectAsState()
    val declinationDeg by compassViewModel.declinationDeg.collectAsState()
    val useTrueNorth by settingsViewModel.useTrueNorth.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Update destinations in compass viewmodel when location or destinations change
    LaunchedEffect(currentLocation, destinations) {
        if (currentLocation != null) {
            compassViewModel.updateDestinations(destinations)
        }
    }

    val shouldTrackLocation = remember(useTrueNorth, destinations) {
        useTrueNorth || destinations.isNotEmpty()
    }

    LaunchedEffect(shouldTrackLocation) {
        compassViewModel.setLocationTrackingEnabled(shouldTrackLocation)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME, Lifecycle.Event.ON_START -> compassViewModel.startListening()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> compassViewModel.stopListening()
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            compassViewModel.startListening()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            compassViewModel.stopListening()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onNavigateToMap) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = stringResource(R.string.cd_open_map)
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.cd_open_settings)
                        )
                    }
                    IconButton(onClick = onNavigateToAbout) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = stringResource(R.string.cd_open_about)
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            if (!sensorAvailable) {
                Text(
                    text = stringResource(R.string.error_sensor_unavailable),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                // Compass Meter
                CompassMeter(
                    azimuth = azimuth,
                    destinationInfoList = destinationInfoList,
                    pitch = pitch,
                    roll = roll,
                    headingAccuracyDeg = headingAccuracyDeg,
                    sensorAccuracy = sensorAccuracy,
                    useTrueNorth = useTrueNorth,
                    declinationDeg = declinationDeg,
                    distanceUnit = settingsViewModel.distanceUnit.collectAsState().value
                )
            }
        }
    }
}
