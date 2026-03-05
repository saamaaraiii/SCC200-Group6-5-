package com.example.transitnavproject

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.views.MapView
import org.osmdroid.util.GeoPoint
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.overlay.Marker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(
            applicationContext,
            applicationContext.getSharedPreferences("osmdroid", MODE_PRIVATE)
        )

        setContent {
            TransitNavHomeScreen()
        }
    }
}

data class BusStop(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

val lancashireStops = listOf(
    BusStop("Lancaster Bus Station", 54.0470, -2.8010),
    BusStop("Preston Bus Station", 53.7632, -2.7031),
    BusStop("Blackpool Central", 53.8142, -3.0503),
    BusStop("Morecambe Promenade", 54.0717, -2.8690),
    BusStop("Burnley Bus Station", 53.7890, -2.2440),
    BusStop("Chorley Interchange", 53.6535, -2.6326)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransitNavHomeScreen() {

    // ✅ Keep default tab = Home
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        containerColor = Color(0xFF0F1B2B),
        topBar = {
            TopAppBar(
                title = { Text("Transit Nav", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0A1420)
                ),
                actions = {
                    IconButton(onClick = { /* SETTINGS */ }) {
                        Icon(Icons.Default.Settings, null, tint = Color.White)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0A1420)) {

                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text("Home") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF2196F3),
                        selectedTextColor = Color(0xFF2196F3),
                        indicatorColor = Color(0x332196F3)
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Map, null) },
                    label = { Text("Map") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF2196F3),
                        selectedTextColor = Color(0xFF2196F3)
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.ConfirmationNumber, null) },
                    label = { Text("Tickets") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF2196F3),
                        selectedTextColor = Color(0xFF2196F3),
                        indicatorColor = Color(0x332196F3)
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Person, null) },
                    label = { Text("Account") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF2196F3),
                        selectedTextColor = Color(0xFF2196F3),
                        indicatorColor = Color(0x332196F3)
                    )
                )
            }
        }
    ) { padding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                0 -> HomeScreen()
                1 -> MapsScreen()
                2 -> TicketsScreen()
                3 -> AccountScreen()
            }
        }
    }
}

@Composable
fun HomeScreen() {

    val context = LocalContext.current
    var selectedStop by remember { mutableStateOf<BusStop?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTransport by remember { mutableStateOf("Buses") }

    val filteredStops = lancashireStops.filter {
        it.name.contains(searchQuery, ignoreCase = true)
    }

    Box(modifier = Modifier.fillMaxSize()) {

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

            Text("Current Location", color = Color.Gray, fontSize = 14.sp)

            Text(
                "Lancaster",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search for a stop...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1E2A3A),
                    unfocusedContainerColor = Color(0xFF1E2A3A),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ✅ Transport Options Highlighted
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                TransportButton("Trains", Icons.Default.Train, selectedTransport == "Trains") { selectedTransport = "Trains" }
                TransportButton("Buses", Icons.Default.DirectionsBus, selectedTransport == "Buses") { selectedTransport = "Buses" }
                TransportButton("Taxis", Icons.Default.LocalTaxi, selectedTransport == "Taxis") { selectedTransport = "Taxis" }
                TransportButton("Explore", Icons.Default.Explore, selectedTransport == "Explore") { selectedTransport = "Explore" }
            }

            Spacer(modifier = Modifier.height(20.dp))

            AndroidView(
                factory = {
                    MapView(context).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(9.0)
                        controller.setCenter(GeoPoint(53.9000, -2.7000))

                        filteredStops.forEach { stop ->
                            val marker = Marker(this)
                            marker.position = GeoPoint(stop.latitude, stop.longitude)
                            marker.title = stop.name
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            marker.setOnMarkerClickListener { _, _ ->
                                selectedStop = stop
                                true
                            }
                            overlays.add(marker)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(450.dp)
                    .clip(RoundedCornerShape(20.dp))
            )
        }

        selectedStop?.let { stop ->
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2A3A)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stop.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.height(8.dp))

                    Row {
                        Button(onClick = {}) { Text("Directions") }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(onClick = {}) { Text("Departures") }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { selectedStop = null }) {
                            Text("Close", color = Color.Red)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MapsScreen() {

    val context = LocalContext.current
    var selectedStop by remember { mutableStateOf<BusStop?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {

        AndroidView(
            factory = {
                MapView(context).apply {

                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)

                    controller.setZoom(9.0)
                    controller.setCenter(GeoPoint(53.9000, -2.7000))

                    lancashireStops.forEach { stop ->

                        val marker = Marker(this)

                        marker.position = GeoPoint(stop.latitude, stop.longitude)
                        marker.title = stop.name

                        marker.setAnchor(
                            Marker.ANCHOR_CENTER,
                            Marker.ANCHOR_BOTTOM
                        )

                        marker.setOnMarkerClickListener { _, _ ->
                            selectedStop = stop
                            true
                        }

                        overlays.add(marker)
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        selectedStop?.let { stop ->

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2A3A)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {

                Column(modifier = Modifier.padding(16.dp)) {

                    Text(
                        stop.name,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row {

                        Button(onClick = {}) {
                            Text("Directions")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        OutlinedButton(onClick = {}) {
                            Text("Departures")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        TextButton(onClick = { selectedStop = null }) {
                            Text("Close", color = Color.Red)
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun TransportButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(60.dp)
                .background(
                    if (selected) Color(0xFF2196F3) else Color(0xFF1E2A3A),
                    RoundedCornerShape(16.dp)
                )
        ) {
            Icon(icon, contentDescription = label, tint = Color.White)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = if (selected) Color(0xFF2196F3) else Color.White, fontSize = 12.sp)
    }
}

@Composable
fun TicketsScreen() {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F1B2B)), contentAlignment = Alignment.Center) {
        Text("Your Tickets", color = Color.White, fontSize = 24.sp)
    }
}

@Composable
fun AccountScreen() {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F1B2B)), contentAlignment = Alignment.Center) {
        Text("Account Details", color = Color.White, fontSize = 24.sp)
    }
}
