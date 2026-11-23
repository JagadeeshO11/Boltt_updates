package in.apsrtc.connect

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.location.LocationServices
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { APSRTCApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun APSRTCApp() {
    val context = LocalContext.current
    val repo = remember { TransitRepository(context) }
    val scope = rememberCoroutineScope()
    var telugu by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }
    var stops by remember { mutableStateOf<List<Stop>>(emptyList()) }
    var routes by remember { mutableStateOf<List<BusRoute>>(emptyList()) }
    var buses by remember { mutableStateOf<List<LiveBus>>(emptyList()) }
    var status by remember { mutableStateOf("Connecting to APSRTC live feed…") }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var origin by remember { mutableStateOf<Stop?>(null) }
    var destination by remember { mutableStateOf<Stop?>(null) }
    var originText by remember { mutableStateOf("") }
    var destinationText by remember { mutableStateOf("") }
    var routeResults by remember { mutableStateOf<List<BusRoute>>(emptyList()) }
    var nearbyStops by remember { mutableStateOf<List<Stop>>(emptyList()) }
    val fusedLocation = remember { LocationServices.getFusedLocationProviderClient(context) }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) fusedLocation.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) nearbyStops = stops.sortedBy { stop ->
                val results = FloatArray(1)
                android.location.Location.distanceBetween(loc.latitude, loc.longitude, stop.lat, stop.lon, results)
                results[0]
            }.take(10)
        }
    }
    var arrivals by remember { mutableStateOf<List<Arrival>>(emptyList()) }
    var favorites by remember { mutableStateOf(repo.favorites()) }
    var selectedBus by remember { mutableStateOf<LiveBus?>(null) }

    fun refreshLive() {
        scope.launch {
            runCatching {
                buses = repo.liveBuses()
                arrivals = repo.arrivals()
                val json = repo.status()
                val feedTime = runCatching { com.google.gson.JsonParser.parseString(json).asJsonObject.get("feed_time").asLong }.getOrNull()
                status = if (feedTime != null) "Live feed updated ${SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(feedTime * 1000))} • ${buses.size} buses" else "${buses.size} buses in feed"
            }.onFailure { status = "Live feed unavailable • showing last loaded data" }
        }
    }

    LaunchedEffect(Unit) {
        runCatching { val data = repo.loadStaticData(); stops = data.first; routes = data.second }
            .onFailure { error = it.message ?: "Could not load APSRTC route data" }
        loading = false
        while (true) {
            refreshLive()
            delay(30_000)
        }
    }

    MaterialTheme(colorScheme = lightColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFFB3261E),
        secondary = androidx.compose.ui.graphics.Color(0xFF315C8A),
        background = androidx.compose.ui.graphics.Color(0xFFF7F7FB)
    )) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("APSRTC Connect", fontWeight = FontWeight.Bold)
                            Text(if (telugu) "ఆంధ్రప్రదేశ్ రాష్ట్ర రోడ్డు రవాణా" else "Andhra Pradesh bus services", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    actions = {
                        TextButton(onClick = { telugu = !telugu }) { Text(if (telugu) "EN" else "తె") }
                        IconButton(onClick = { refreshLive() }) { Icon(Icons.Default.Refresh, "Refresh live buses") }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.Search, null) }, label = { Text(if (telugu) "శోధన" else "Search") })
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.Map, null) }, label = { Text(if (telugu) "మ్యాప్" else "Map") })
                    NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Default.DirectionsBus, null) }, label = { Text(if (telugu) "లైవ్" else "Live") })
                    NavigationBarItem(selected = tab == 3, onClick = { tab = 3 }, icon = { Icon(Icons.Default.Favorite, null) }, label = { Text(if (telugu) "ఇష్టమైనవి" else "Saved") })
                }
            }
        ) { padding ->
            when (tab) {
                0 -> SearchScreen(
                    Modifier.padding(padding), telugu, loading, error, stops, originText, destinationText,
                    { originText = it; origin = null }, { destinationText = it; destination = null },
                    { origin = it; originText = it.name }, { destination = it; destinationText = it.name },
                    origin, destination, routeResults,
                    { scope.launch { if (origin != null && destination != null) runCatching { routeResults = repo.findRoutes(origin!!, destination!!, routes) }.onFailure { error = it.message ?: "Route search failed" } } },
                    favorites, { id -> favorites = repo.toggleFavorite(id) },
                    nearbyStops, { locationPermission.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)) },
                    error
                )
                1 -> MapScreen(Modifier.padding(padding), buses, selectedBus) { selectedBus = it }
                2 -> LiveScreen(Modifier.padding(padding), status, buses, arrivals, stops, telugu)
                else -> FavoritesScreen(Modifier.padding(padding), favorites, routes, telugu) { id -> favorites = repo.toggleFavorite(id) }
            }
        }
    }
}

@Composable
fun SearchScreen(
    modifier: Modifier, telugu: Boolean, loading: Boolean, initialError: String, stops: List<Stop>,
    from: String, to: String, onFrom: (String) -> Unit, onTo: (String) -> Unit,
    selectFrom: (Stop) -> Unit, selectTo: (Stop) -> Unit, origin: Stop?, destination: Stop?,
    results: List<BusRoute>, search: () -> Unit, favorites: Set<String>, toggleFavorite: (String) -> Unit,
    nearbyStops: List<Stop>, findNearby: () -> Unit, error: String
) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text(if (telugu) "మీ ప్రయాణాన్ని ప్లాన్ చేయండి" else "Plan your journey", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(if (telugu) "APSRTC బస్ స్టాప్‌ల మధ్య సేవలను కనుగొనండి" else "Find scheduled services between APSRTC stops", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        StopAutocomplete(if (telugu) "ఎక్కడి నుంచి?" else "From", from, stops, onFrom, selectFrom)
        Spacer(Modifier.height(10.dp))
        StopAutocomplete(if (telugu) "ఎక్కడికి?" else "To", to, stops, onTo, selectTo)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = findNearby, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.MyLocation, null); Spacer(Modifier.width(8.dp)); Text(if (telugu) "దగ్గరలోని బస్ స్టాప్‌లు" else "Find nearby bus stops")
        }
        if (nearbyStops.isNotEmpty()) {
            Text(if (telugu) "దగ్గరలోని స్టాప్‌లు" else "Nearby stops", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            nearbyStops.forEach { Text("• ${it.name}", Modifier.padding(vertical = 2.dp)) }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = search, enabled = origin != null && destination != null, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text(if (telugu) "బస్సులు వెతకండి" else "Find buses")
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp))
        if (initialError.isNotBlank()) Text(initialError, color = MaterialTheme.colorScheme.error)
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        if (results.isNotEmpty()) {
            Spacer(Modifier.height(16.dp)); Text("Matching services", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            results.forEach { route ->
                ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(route.shortName.ifBlank { route.id }, fontWeight = FontWeight.Bold)
                            Text(route.longName, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { toggleFavorite(route.id) }) {
                            Icon(if (favorites.contains(route.id)) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Toggle favourite")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopAutocomplete(label: String, value: String, stops: List<Stop>, onValue: (String) -> Unit, onSelect: (Stop) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val matches = remember(value, stops) { if (value.length < 2) emptyList() else stops.filter { it.name.contains(value, true) }.take(8) }
    ExposedDropdownMenuBox(expanded = expanded && matches.isNotEmpty(), onExpandedChange = { expanded = it }) {
        OutlinedTextField(value, onValue, Modifier.fillMaxWidth().menuAnchor(), label = { Text(label) }, singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) })
        ExposedDropdownMenu(expanded = expanded && matches.isNotEmpty(), onDismissRequest = { expanded = false }) {
            matches.forEach { stop -> DropdownMenuItem(text = { Text(stop.name) }, onClick = { onSelect(stop); expanded = false }) }
        }
    }
}

@Composable
fun LiveScreen(modifier: Modifier, status: String, buses: List<LiveBus>, arrivals: List<Arrival>, stops: List<Stop>, telugu: Boolean) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text(if (telugu) "ప్రత్యక్ష బస్సులు" else "Live APSRTC buses", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        if (buses.isEmpty() && arrivals.isEmpty()) {
            Text(if (telugu) "ప్రస్తుతం లైవ్ సమాచారం అందుబాటులో లేదు" else "No live vehicle positions or arrival updates are currently available.")
        }
        LazyColumn(Modifier.weight(1f)) {
            if (buses.isNotEmpty()) {
                item { Text(if (telugu) "బస్సుల స్థానాలు" else "Vehicle positions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(buses.take(300)) { bus ->
                    ListItem(
                        headlineContent = { Text(bus.label) },
                        supportingContent = { Text(if (bus.routeId.isBlank()) "Route not matched" else "Route: ${bus.routeId}") },
                        leadingContent = { Icon(Icons.Default.DirectionsBus, null) },
                        trailingContent = { Text("GPS") }
                    )
                    HorizontalDivider()
                }
            }
            if (arrivals.isNotEmpty()) {
                item { Text("Arrival predictions", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(arrivals.take(100)) { arrival ->
                    val stopName = stops.firstOrNull { it.id == arrival.stopId }?.name ?: arrival.stopId
                    val time = arrival.arrivalEpoch?.let { SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(it * 1000)) } ?: "Time unavailable"
                    ListItem(
                        headlineContent = { Text(stopName) },
                        supportingContent = { Text("Route ${arrival.routeId.ifBlank { "unmatched" }} • ${time}") },
                        trailingContent = { arrival.delay?.let { Text(if (it >= 0) "+${it / 60}m" else "${it / 60}m") } }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun MapScreen(modifier: Modifier, buses: List<LiveBus>, selected: LiveBus?, onSelect: (LiveBus?) -> Unit) {
    Column(modifier.fillMaxSize()) {
        Text("Live bus map", Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    webViewClient = WebViewClient()
                    loadDataWithBaseURL("https://www.openstreetmap.org", mapHtml(buses), "text/html", "UTF-8", null)
                }
            },
            update = { it.loadDataWithBaseURL("https://www.openstreetmap.org", mapHtml(buses), "text/html", "UTF-8", null) }
        )
        if (selected != null) Text("Selected: ${selected.label} • ${selected.lat}, ${selected.lon}", Modifier.padding(12.dp))
        Text("${buses.size} live positions • Map uses OpenStreetMap", Modifier.padding(12.dp), style = MaterialTheme.typography.labelSmall)
    }
}

private fun mapHtml(buses: List<LiveBus>): String {
    val markers = buses.take(500).joinToString(",") { "[${it.lat},${it.lon},'${it.label.replace("'","")}']" }
    return """<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
    <style>html,body,#map{height:100%;margin:0}</style></head><body><div id="map"></div><script>
    const pts=[$markers]; const map=L.map('map').setView([16.5,80.6],7);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:18,attribution:'© OpenStreetMap'}).addTo(map);
    pts.forEach(p=>L.marker([p[0],p[1]]).addTo(map).bindPopup(p[2]));
    if(pts.length){map.fitBounds(pts.map(p=>[p[0],p[1]]),{padding:[20,20]});}
    </script></body></html>"""
}

@Composable
fun FavoritesScreen(modifier: Modifier, favorites: Set<String>, routes: List<BusRoute>, telugu: Boolean, toggle: (String) -> Unit) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text(if (telugu) "ఇష్టమైన రూట్లు" else "Favourite routes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (favorites.isEmpty()) Text(if (telugu) "ఇంకా రూట్లు సేవ్ చేయలేదు" else "No favourites saved yet. Save routes here when you add them.")
        LazyColumn {
            items(favorites.toList()) { id ->
                val route = routes.firstOrNull { it.id == id }
                ListItem(headlineContent = { Text(route?.let { it.shortName.ifBlank { it.longName } } ?: id) },
                    supportingContent = { Text(route?.longName.orEmpty()) },
                    trailingContent = { IconButton(onClick = { toggle(id) }) { Icon(Icons.Default.Favorite, "Remove favourite") } })
            }
        }
    }
}
