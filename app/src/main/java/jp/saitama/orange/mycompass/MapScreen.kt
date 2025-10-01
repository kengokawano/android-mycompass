package jp.saitama.orange.mycompass

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.json.JSONArray
import java.net.URL
import java.net.URLEncoder

data class SearchResult(
    val displayName: String,
    val lat: Double,
    val lon: Double
)

object SearchRateLimiter {
    private var lastSearchTime = 0L
    private const val MIN_SEARCH_INTERVAL = 5000L // 5 seconds

    fun canSearch(): Boolean {
        val currentTime = System.currentTimeMillis()
        return currentTime - lastSearchTime >= MIN_SEARCH_INTERVAL
    }

    fun getRemainingTime(): Long {
        val currentTime = System.currentTimeMillis()
        val elapsed = currentTime - lastSearchTime
        return if (elapsed < MIN_SEARCH_INTERVAL) {
            (MIN_SEARCH_INTERVAL - elapsed) / 1000
        } else {
            0L
        }
    }

    fun recordSearch() {
        lastSearchTime = System.currentTimeMillis()
    }
}

suspend fun searchLocation(query: String): List<SearchResult> {
    return withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val urlString = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=5"
            println("Search URL: $urlString")

            val url = URL(urlString)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.setRequestProperty("User-Agent", "CompassApp/1.0 (Android)")
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            println("Response: $response")

            val jsonArray = JSONArray(response)
            println("JSON Array length: ${jsonArray.length()}")

            val results = mutableListOf<SearchResult>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                results.add(
                    SearchResult(
                        displayName = obj.getString("display_name"),
                        lat = obj.getDouble("lat"),
                        lon = obj.getDouble("lon")
                    )
                )
            }
            println("Search results count: ${results.size}")
            results
        } catch (e: Exception) {
            e.printStackTrace()
            println("Search error: ${e.message}")
            emptyList()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun MapScreen(
    onNavigateBack: () -> Unit,
    destinationViewModel: DestinationViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var currentLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var destinationName by remember { mutableStateOf("") }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var currentMarker by remember { mutableStateOf<Marker?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var rateLimitMessage by remember { mutableStateOf("") }

    val destinations by destinationViewModel.destinations.collectAsState()

    val locationPermissions = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    LaunchedEffect(Unit) {
        locationPermissions.launchMultiplePermissionRequest()
    }

    LaunchedEffect(locationPermissions.allPermissionsGranted) {
        if (locationPermissions.allPermissionsGranted) {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            try {
                val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

                location?.let {
                    val geoPoint = GeoPoint(it.latitude, it.longitude)
                    currentLocation = geoPoint
                    selectedLocation = geoPoint
                    mapView?.controller?.animateTo(geoPoint)
                }
            } catch (e: SecurityException) {
                // Permission not granted
            }
        }
    }

    DisposableEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
        onDispose {
            mapView?.onDetach()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Location") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Spacer(modifier = Modifier.height(80.dp)) // Space for search bar

                // Map section (fixed height)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                ) {
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            controller.setZoom(15.0)

                            // Set initial center
                            val initialCenter = currentLocation ?: GeoPoint(35.6812, 139.7671)
                            controller.setCenter(initialCenter)

                            // Add location overlay
                            val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this)
                            locationOverlay.enableMyLocation()
                            overlays.add(locationOverlay)

                            // Add initial marker if current location exists
                            currentLocation?.let { loc ->
                                val marker = Marker(this).apply {
                                    position = loc
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    title = "Current Location"
                                }
                                overlays.add(marker)
                                currentMarker = marker
                            }

                            // Add tap event listener
                            val mapEventsReceiver = object : MapEventsReceiver {
                                override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                                    p?.let {
                                        selectedLocation = it

                                        // Remove old marker
                                        currentMarker?.let { marker ->
                                            overlays.remove(marker)
                                        }

                                        // Add new marker
                                        val marker = Marker(this@apply).apply {
                                            position = it
                                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                            title = "Selected Location"
                                        }
                                        overlays.add(marker)
                                        currentMarker = marker
                                        invalidate()
                                    }
                                    return true
                                }

                                override fun longPressHelper(p: GeoPoint?): Boolean {
                                    return false
                                }
                            }
                            overlays.add(MapEventsOverlay(mapEventsReceiver))
                            mapView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { map ->
                        currentLocation?.let { loc ->
                            if (map.overlays.none { it is Marker && it.position == loc }) {
                                map.controller.setCenter(loc)
                            }
                        }
                    }
                )
            }

                // Bottom input section with destinations list
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface),
                    tonalElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        // Left side: Input section
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            // Name input
                            OutlinedTextField(
                                value = destinationName,
                                onValueChange = { destinationName = it },
                                label = { Text("Name") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Register button
                            Button(
                                onClick = {
                                    selectedLocation?.let { location ->
                                        if (destinationName.isNotBlank()) {
                                            val added = destinationViewModel.addDestination(
                                                destinationName,
                                                location.latitude,
                                                location.longitude
                                            )
                                            if (added) {
                                                destinationName = ""
                                                selectedLocation = null
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                enabled = selectedLocation != null &&
                                         destinationName.isNotBlank() &&
                                         destinationViewModel.canAddMore()
                            ) {
                                Text("Register (${destinations.size}/3)")
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Display coordinates
                            selectedLocation?.let { location ->
                                Text(
                                    text = "Lat: ${String.format("%.6f", location.latitude)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "Lon: ${String.format("%.6f", location.longitude)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        // Right side: Destinations list
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp)
                        ) {
                            Text(
                                text = "Destinations",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            destinations.forEach { destination ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(8.dp)
                                            .fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = destination.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { destinationViewModel.removeDestination(destination.id) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Delete",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Search bar overlay (on top)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Search location...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                Button(
                                    onClick = {
                                        if (SearchRateLimiter.canSearch()) {
                                            println("Search button clicked with query: $searchQuery")
                                            rateLimitMessage = ""
                                            scope.launch {
                                                isSearching = true
                                                println("Starting search...")
                                                val results = searchLocation(searchQuery)
                                                searchResults = results
                                                println("Search completed. Results: ${results.size}")
                                                isSearching = false
                                                SearchRateLimiter.recordSearch()
                                            }
                                        } else {
                                            val remaining = SearchRateLimiter.getRemainingTime()
                                            rateLimitMessage = "Please wait ${remaining} seconds"
                                        }
                                    }
                                ) {
                                    Text("Search")
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Search
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onSearch = {
                                if (searchQuery.isNotEmpty()) {
                                    if (SearchRateLimiter.canSearch()) {
                                        rateLimitMessage = ""
                                        scope.launch {
                                            isSearching = true
                                            searchResults = searchLocation(searchQuery)
                                            isSearching = false
                                            SearchRateLimiter.recordSearch()
                                        }
                                    } else {
                                        val remaining = SearchRateLimiter.getRemainingTime()
                                        rateLimitMessage = "Please wait ${remaining} seconds"
                                    }
                                }
                            }
                        )
                    )
                }

                // Rate limit message
                if (rateLimitMessage.isNotEmpty()) {
                    Text(
                        text = rateLimitMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                // Search results
                if (searchResults.isNotEmpty()) {
                    println("Displaying ${searchResults.size} search results")
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .heightIn(max = 200.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        LazyColumn {
                            items(searchResults) { result ->
                                ListItem(
                                    headlineContent = { Text(result.displayName) },
                                    modifier = Modifier.clickable {
                                        val geoPoint = GeoPoint(result.lat, result.lon)
                                        selectedLocation = geoPoint
                                        mapView?.controller?.animateTo(geoPoint)
                                        mapView?.controller?.setZoom(15.0)

                                        // Remove old marker and add new one
                                        currentMarker?.let { marker ->
                                            mapView?.overlays?.remove(marker)
                                        }
                                        val marker = Marker(mapView).apply {
                                            position = geoPoint
                                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                            title = result.displayName
                                        }
                                        mapView?.overlays?.add(marker)
                                        currentMarker = marker
                                        mapView?.invalidate()

                                        // Clear search results
                                        searchResults = emptyList()
                                        searchQuery = ""
                                    }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }

                if (isSearching) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}