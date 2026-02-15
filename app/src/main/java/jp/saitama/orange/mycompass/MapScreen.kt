package jp.saitama.orange.mycompass

import android.Manifest
import android.content.Context
import android.location.LocationManager
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.style.TextAlign
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale

data class SearchResult(
    val displayName: String,
    val lat: Double,
    val lon: Double
)

enum class SearchErrorType {
    NETWORK,
    CLIENT,
    SERVER,
    RATE_LIMITED,
    UNKNOWN
}

data class SearchLocationError(
    val type: SearchErrorType,
    val httpCode: Int? = null
)

data class SearchLocationResponse(
    val results: List<SearchResult> = emptyList(),
    val error: SearchLocationError? = null
)

object SearchRateLimiter {
    private var lastSearchTime = 0L
    private const val MIN_SEARCH_INTERVAL = 1500L

    fun canSearch(): Boolean {
        val currentTime = System.currentTimeMillis()
        return currentTime - lastSearchTime >= MIN_SEARCH_INTERVAL
    }

    fun recordSearch() {
        lastSearchTime = System.currentTimeMillis()
    }
}

suspend fun searchLocation(query: String, viewBox: String? = null): SearchLocationResponse {
    return withContext(Dispatchers.IO) {
        if (query.isBlank() || query.length < 2) {
            return@withContext SearchLocationResponse(emptyList())
        }
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            var urlString = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=10&addressdetails=1&accept-language=ja,en"
            if (viewBox != null) {
                urlString += "&viewbox=$viewBox"
            }

            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", "MyCompassApp/1.1 (Android; jp.saitama.orange.mycompass)")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
            }

            try {
                when (val responseCode = connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val response = connection.inputStream.bufferedReader().use { it.readText() }
                        val jsonArray = JSONArray(response)

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
                        SearchLocationResponse(results = results)
                    }
                    429 -> SearchLocationResponse(error = SearchLocationError(SearchErrorType.RATE_LIMITED, responseCode))
                    in 400..499 -> SearchLocationResponse(error = SearchLocationError(SearchErrorType.CLIENT, responseCode))
                    in 500..599 -> SearchLocationResponse(error = SearchLocationError(SearchErrorType.SERVER, responseCode))
                    else -> SearchLocationResponse(error = SearchLocationError(SearchErrorType.UNKNOWN, responseCode))
                }
            } finally {
                connection.disconnect()
            }
        } catch (_: SocketTimeoutException) {
            SearchLocationResponse(error = SearchLocationError(SearchErrorType.NETWORK))
        } catch (_: UnknownHostException) {
            SearchLocationResponse(error = SearchLocationError(SearchErrorType.NETWORK))
        } catch (_: Exception) {
            SearchLocationResponse(error = SearchLocationError(SearchErrorType.UNKNOWN))
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
    val focusManager = LocalFocusManager.current
    var selectedLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var currentLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var destinationName by remember { mutableStateOf(TextFieldValue("")) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var currentMarker by remember { mutableStateOf<Marker?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<SearchLocationError?>(null) }

    // Helper to get current map viewbox
    fun getMapViewBox(): String? {
        val mv = mapView ?: return null
        val box = mv.boundingBox
        return "${box.lonWest},${box.latNorth},${box.lonEast},${box.latSouth}"
    }

    val performSearch: (String) -> Unit = { query ->
        if (query.length < 2) {
            searchResults = emptyList()
        } else if (SearchRateLimiter.canSearch()) {
            scope.launch {
                isSearching = true
                searchError = null
                try {
                    val viewBox = getMapViewBox()
                    val response = searchLocation(query, viewBox)
                    if (response.error != null) {
                        if (response.error.type != SearchErrorType.RATE_LIMITED) {
                            searchResults = emptyList()
                        }
                        searchError = response.error
                    } else {
                        searchResults = response.results
                        searchError = null
                    }
                } finally {
                    isSearching = false
                    SearchRateLimiter.recordSearch()
                }
            }
        }
    }

    // Debounced search logic
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            searchResults = emptyList()
            return@LaunchedEffect
        }
        if (searchQuery.length >= 2) {
            delay(800)
            performSearch(searchQuery)
        }
    }

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
            } catch (_: SecurityException) { }
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
                title = { Text(stringResource(R.string.map_title_select_location)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Spacer(modifier = Modifier.height(72.dp))

                // Map section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .heightIn(min = 300.dp)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            MapView(ctx).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                controller.setZoom(15.0)

                                val initialCenter = currentLocation ?: GeoPoint(35.6812, 139.7671)
                                controller.setCenter(initialCenter)

                                val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this)
                                locationOverlay.enableMyLocation()
                                overlays.add(locationOverlay)

                                val mapEventsReceiver = object : MapEventsReceiver {
                                    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                                        p?.let {
                                            selectedLocation = it
                                            val defaultName = String.format(Locale.US, "%.4f, %.4f", it.latitude, it.longitude)
                                            destinationName = TextFieldValue(defaultName)
                                            focusManager.clearFocus(force = true)

                                            currentMarker?.let { marker -> overlays.remove(marker) }
                                            val marker = Marker(this@apply).apply {
                                                position = it
                                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                                title = ctx.getString(R.string.map_marker_selected_location)
                                            }
                                            overlays.add(marker)
                                            currentMarker = marker
                                            invalidate()
                                        }
                                        return true
                                    }
                                    override fun longPressHelper(p: GeoPoint?): Boolean = false
                                }
                                overlays.add(MapEventsOverlay(mapEventsReceiver))
                                mapView = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Bottom input section
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 8.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Button(
                                    onClick = {
                                        focusManager.clearFocus()
                                        selectedLocation?.let { location ->
                                            if (destinationName.text.isNotBlank()) {
                                                val added = destinationViewModel.addDestination(
                                                    destinationName.text,
                                                    location.latitude,
                                                    location.longitude
                                                )
                                                if (added) {
                                                    destinationName = TextFieldValue("")
                                                    selectedLocation = null
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    enabled = selectedLocation != null && destinationName.text.isNotBlank() && destinationViewModel.canAddMore()
                                ) {
                                    Text(stringResource(R.string.map_register_button, destinations.size, Destination.MAX_DESTINATIONS))
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = destinationName,
                                    onValueChange = { destinationName = it },
                                    label = { Text(stringResource(R.string.map_input_name_label)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }

                            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(
                                    text = stringResource(R.string.map_destinations_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    color = Color(0xFFFF9800)
                                )
                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth().height(100.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(destinations) { destination ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF59D))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(4.dp).fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(destination.name, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF9800), modifier = Modifier.weight(1f))
                                                IconButton(onClick = { destinationViewModel.removeDestination(destination.id) }, modifier = Modifier.size(20.dp)) {
                                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Text(
                            text = stringResource(R.string.map_data_credit),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Search bar overlay
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 8.dp) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text(stringResource(R.string.map_search_placeholder)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = ""; searchResults = emptyList() }) {
                                    Icon(Icons.Default.Close, contentDescription = null)
                                }
                            }
                        },
                        singleLine = true
                    )
                }

                if (isSearching) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))

                if (searchResults.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(max = 300.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        LazyColumn {
                            items(searchResults) { result ->
                                ListItem(
                                    headlineContent = { Text(result.displayName, style = MaterialTheme.typography.bodyMedium) },
                                    modifier = Modifier.clickable {
                                        val geoPoint = GeoPoint(result.lat, result.lon)
                                        selectedLocation = geoPoint
                                        destinationName = TextFieldValue(result.displayName)
                                        focusManager.clearFocus(force = true)
                                        mapView?.controller?.animateTo(geoPoint)
                                        mapView?.controller?.setZoom(17.0)
                                        
                                        currentMarker?.let { marker -> mapView?.overlays?.remove(marker) }
                                        val marker = Marker(mapView).apply {
                                            position = geoPoint
                                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                            title = result.displayName
                                        }
                                        mapView?.overlays?.add(marker)
                                        currentMarker = marker
                                        mapView?.invalidate()
                                        
                                        searchResults = emptyList()
                                        searchQuery = ""
                                    }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }

                // Error message display (if needed)
                searchError?.let { error ->
                    val errorText = when (error.type) {
                        SearchErrorType.NETWORK -> stringResource(R.string.map_search_error_network)
                        SearchErrorType.RATE_LIMITED -> stringResource(R.string.map_search_error_rate_limited)
                        else -> stringResource(R.string.map_search_error_unknown)
                    }
                    Text(
                        text = errorText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
