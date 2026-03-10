package com.example.transitnavproject

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.tileprovider.tilesource.TileSourceFactory

// --- UI Constants ---
val AppDarkBlue = Color(0xFF050B18)
val AppCardNav = Color(0xFF131B2C)
val AppFieldBg = Color(0xFF1C2436)
val AppAccentBlue = Color(0xFF3D8BFF)
val AppBorderGreyBlue = Color(0xFF242F42)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(applicationContext, applicationContext.getSharedPreferences("osmdroid", MODE_PRIVATE))
        setContent { TransitNavHomeScreen() }
    }
}

@Composable
fun TransitNavHomeScreen() {
    var selectedTab by remember { mutableStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showSearchOverlay by remember { mutableStateOf(false) }
    var showScheduleOverlay by remember { mutableStateOf(false) }

    var selectedTransportMode by remember { mutableStateOf("Train") }
    var currentLanguage by remember { mutableStateOf("English") }
    var themeMode by remember { mutableStateOf("Dark") }
    var selectedDestination by remember { mutableStateOf("") }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Scaffold(
            containerColor = AppDarkBlue,
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF0A1420)) {
                    val navItems = listOf(
                        Triple(0, Icons.Default.Home, "Home"),
                        Triple(1, Icons.Default.Map, "Map"),
                        Triple(2, Icons.Default.ConfirmationNumber, "Tickets"),
                        Triple(3, Icons.Default.Person, "Account")
                    )
                    navItems.forEach { (idx, icon, label) ->
                        NavigationBarItem(
                            selected = selectedTab == idx && !showSettings && !showSearchOverlay && !showScheduleOverlay,
                            onClick = {
                                selectedTab = idx
                                showSettings = false
                                showSearchOverlay = false
                                showScheduleOverlay = false
                            },
                            icon = { Icon(icon, null) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (selectedTab) {
                    0 -> HomeView(
                        mode = selectedTransportMode,
                        onModeChange = { selectedTransportMode = it },
                        onOpenSettings = { showSettings = true },
                        onOpenSearch = { showSearchOverlay = true }
                    )
                    1 -> MapsScreen(selectedTransportMode)
                    2 -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Tickets", color = Color.White) }
                    3 -> AccountView { showSettings = true }
                }

                if (showSearchOverlay) {
                    SearchOverlay(
                        mode = selectedTransportMode,
                        onBack = { showSearchOverlay = false },
                        onSelectLocation = { location ->
                            selectedDestination = location
                            showSearchOverlay = false
                            showScheduleOverlay = true
                        }
                    )
                }

                if (showScheduleOverlay) {
                    ScheduleOverlay(
                        mode = selectedTransportMode,
                        destination = selectedDestination,
                        onBack = { showScheduleOverlay = false }
                    )
                }

                if (showSettings) {
                    SettingsOverlay(
                        currentLanguage = currentLanguage,
                        themeMode = themeMode,
                        onLangChange = { currentLanguage = it },
                        onThemeChange = { themeMode = it },
                        onBack = { showSettings = false }
                    )
                }
            }
        }
    }
}

// --- UPDATED SEARCH OVERLAY WITH CALENDAR AND PASSENGERS ---
@Composable
fun SearchOverlay(mode: String, onBack: () -> Unit, onSelectLocation: (String) -> Unit) {
    var origin by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var journeyType by remember { mutableStateOf("Single") }
    var passengerCount by remember { mutableStateOf(1) }
    var selectedDate by remember { mutableStateOf(5) } // Default to 5th

    BackHandler { onBack() }

    Column(
        Modifier
            .fillMaxSize()
            .background(AppDarkBlue)
            .verticalScroll(rememberScrollState()) // Enabled scrolling for the whole form
            .padding(16.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, Modifier.background(AppCardNav, CircleShape)) {
                Icon(Icons.Default.ArrowBack, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Text("Search Trains", color = Color.White, fontSize = 18.sp)
        }

        Spacer(Modifier.height(24.dp))
        Text("Plan Your Rail Journey", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Enter where you are going and customise your trip", color = Color.Gray, fontSize = 14.sp)

        Spacer(Modifier.height(24.dp))

        // WHERE Section
        Card(colors = CardDefaults.cardColors(AppCardNav), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Where", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))
                Text("Origin", color = Color.Gray, fontSize = 12.sp)
                SearchInputField(value = origin, placeholder = "Type departure station") { origin = it }
                Spacer(Modifier.height(12.dp))
                Text("Destination", color = Color.Gray, fontSize = 12.sp)
                SearchInputField(value = destination, placeholder = "Type arrival station") { destination = it }
            }
        }

        Spacer(Modifier.height(16.dp))

        // NOC Operators
        Card(colors = CardDefaults.cardColors(AppCardNav), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Local Operators (NOC)", color = Color.White, fontWeight = FontWeight.Bold)
                Text("ARCT, BLAC, KLCO, SCCU, SCMY, NUTT", color = Color.Gray, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        // WHEN Section
        Card(colors = CardDefaults.cardColors(AppCardNav), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("When", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppFieldBg).padding(4.dp)) {
                    listOf("Single", "Return", "Open Return").forEach { type ->
                        val isSelected = journeyType == type
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) AppAccentBlue else Color.Transparent)
                                .clickable { journeyType = type }.padding(vertical = 10.dp),
                            Alignment.Center
                        ) {
                            Text(type, color = if (isSelected) Color.White else Color.Gray, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // TRAVEL DATES & CALENDAR
        Card(colors = CardDefaults.cardColors(AppCardNav), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Travel Dates", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateSummaryBox("Outbound", "Thu, $selectedDate Mar 2026", Modifier.weight(1f))
                    DateSummaryBox("Return", if (journeyType == "Single") "Not required" else "Select Date", Modifier.weight(1f))
                }

                Spacer(Modifier.height(20.dp))
                Text("March 2026 >", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))

                // Calendar Grid Placeholder
                CalendarGrid(selectedDate) { selectedDate = it }
            }
        }

        Spacer(Modifier.height(16.dp))

        // PASSENGERS Section
        Card(colors = CardDefaults.cardColors(AppCardNav), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Passengers", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppFieldBg).padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$passengerCount passenger" + if(passengerCount > 1) "s" else "", color = Color.White, modifier = Modifier.padding(start = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if(passengerCount > 1) passengerCount-- }) {
                            Icon(Icons.Default.Remove, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Box(Modifier.width(1.dp).height(24.dp).background(Color.Gray))
                        IconButton(onClick = { passengerCount++ }) {
                            Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Railcard", color = Color.Gray, fontSize = 12.sp)
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppFieldBg).padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("No Railcard", color = Color.White)
                        Icon(Icons.Default.UnfoldMore, null, tint = Color.Gray)
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = { onSelectLocation(destination) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(AppAccentBlue),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Search Journeys", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(50.dp))
    }
}

@Composable
fun DateSummaryBox(label: String, date: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(AppFieldBg).padding(12.dp)) {
        Text(label, color = Color.Gray, fontSize = 10.sp)
        Text(date, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CalendarGrid(selectedDay: Int, onDaySelected: (Int) -> Unit) {
    val days = (1..31).toList()
    val columns = 7

    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN").forEach {
                Text(it, color = Color.Gray, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(8.dp))

        // Simple chunked grid for the month
        days.chunked(columns).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                week.forEach { day ->
                    val isSelected = day == selectedDay
                    Box(
                        Modifier.size(36.dp).clip(CircleShape)
                            .background(if (isSelected) AppAccentBlue else Color.Transparent)
                            .clickable { onDaySelected(day) },
                        Alignment.Center
                    ) {
                        Text(day.toString(), color = if (isSelected) Color.White else Color.Gray, fontSize = 14.sp)
                    }
                }
                // Pad empty days for the last week if needed
                if (week.size < columns) {
                    repeat(columns - week.size) { Spacer(Modifier.size(36.dp)) }
                }
            }
        }
    }
}

@Composable
fun SearchInputField(value: String, placeholder: String, onValueChange: (String) -> Unit) {
    TextField(
        value = value, onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = Color.Gray) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = AppAccentBlue) },
        modifier = Modifier.fillMaxWidth(),
        colors = TextFieldDefaults.colors(focusedContainerColor = AppFieldBg, unfocusedContainerColor = AppFieldBg, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    )
}

// --- REST OF THE VIEWS (HOME, MAPS, SETTINGS, ACCOUNT) ---

@Composable
fun HomeView(mode: String, onModeChange: (String) -> Unit, onOpenSettings: () -> Unit, onOpenSearch: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(45.dp).clip(CircleShape).background(Color.Gray), Alignment.Center) {
                Icon(Icons.Default.Person, null, tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Welcome back", color = Color.Gray, fontSize = 12.sp)
                Text("Alex Traveller", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onOpenSettings, Modifier.background(AppCardNav, CircleShape)) {
                Icon(Icons.Default.Settings, null, tint = Color.White)
            }
        }
        Spacer(Modifier.height(20.dp))
        Card(colors = CardDefaults.cardColors(AppCardNav), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().clickable { onOpenSearch() }) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, tint = AppAccentBlue)
                Spacer(Modifier.width(12.dp))
                Text("Search all trains", color = Color.White, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val options = listOf("Train" to Icons.Default.Train, "Bus" to Icons.Default.DirectionsBus, "Taxi" to Icons.Default.LocalTaxi, "Walk" to Icons.Default.DirectionsWalk)
            options.forEach { (name, icon) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(75.dp).clip(RoundedCornerShape(16.dp)).background(if (mode == name) AppAccentBlue else AppCardNav).clickable { onModeChange(name) }, Alignment.Center) {
                        Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Text(name, color = if (mode == name) AppAccentBlue else Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Card(colors = CardDefaults.cardColors(AppCardNav), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(12.dp)) {
                Box(Modifier.height(220.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))) { MapsScreen(mode) }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = AppAccentBlue, shape = RoundedCornerShape(4.dp)) {
                        Text("STATION", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Lancaster Train Station", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.StarBorder, null, tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun ScheduleOverlay(mode: String, destination: String, onBack: () -> Unit) {
    BackHandler { onBack() }
    Column(Modifier.fillMaxSize().background(AppDarkBlue).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.Close, null, tint = Color.White) }
            Text("$mode to $destination", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
        val times = listOf("14:30", "15:05", "15:45", "16:20")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(times) { time ->
                Card(colors = CardDefaults.cardColors(AppCardNav), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().border(1.dp, AppBorderGreyBlue, RoundedCornerShape(16.dp))) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(time, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("Platform 3", color = Color.Gray, fontSize = 12.sp)
                        }
                        Button(onClick = {}, colors = ButtonDefaults.buttonColors(AppAccentBlue)) { Text("Select") }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsOverlay(currentLanguage: String, themeMode: String, onLangChange: (String) -> Unit, onThemeChange: (String) -> Unit, onBack: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(AppDarkBlue).padding(16.dp)) {
        Box(Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack, Modifier.background(AppCardNav, CircleShape)) { Icon(Icons.Default.ArrowBackIosNew, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
            Text("Settings", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.height(30.dp))
        Card(colors = CardDefaults.cardColors(AppCardNav), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Badge, null, tint = AppAccentBlue); Text(" Personal Data", color = Color.White, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(16.dp))
                InputBox("Alex Traveller")
                Spacer(Modifier.height(10.dp))
                InputBox("alex.traveller@example.com")
            }
        }
        Spacer(Modifier.height(20.dp))
        Card(colors = CardDefaults.cardColors(AppCardNav), shape = RoundedCornerShape(24.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Language, null, tint = AppAccentBlue); Text(" Language", color = Color.White) }
                Spacer(Modifier.height(12.dp))
                Box {
                    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(AppFieldBg).clickable { expanded = true }.padding(horizontal = 20.dp, vertical = 10.dp)) {
                        Text(currentLanguage, color = Color.White)
                        Icon(Icons.Default.UnfoldMore, null, tint = Color.Gray)
                    }
                    DropdownMenu(expanded, { expanded = false }) {
                        listOf("English", "Spanish", "French").forEach { lang -> DropdownMenuItem(text = { Text(lang) }, onClick = { onLangChange(lang); expanded = false }) }
                    }
                }
            }
        }
    }
}

@Composable
fun AccountView(onOpenSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Account", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(30.dp))
        Card(colors = CardDefaults.cardColors(AppCardNav), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("Student Account", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("UPSA Exchange – Lancaster University", color = Color.Gray)
            }
        }
        Spacer(Modifier.height(24.dp))
        val actions = listOf("Saved Trips" to Icons.Default.Bookmark, "Settings" to Icons.Default.Settings)
        actions.forEach { (title, icon) ->
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).border(1.dp, AppBorderGreyBlue, RoundedCornerShape(14.dp)).clickable { if(title=="Settings") onOpenSettings() }.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = AppAccentBlue)
                    Text(title, color = Color.White, modifier = Modifier.weight(1f).padding(start = 16.dp))
                    Icon(Icons.Default.ChevronRight, null, tint = AppAccentBlue)
                }
            }
        }
    }
}

@Composable
fun InputBox(text: String) {
    TextField(value = text, onValueChange = {}, modifier = Modifier.fillMaxWidth(), colors = TextFieldDefaults.colors(focusedContainerColor = AppFieldBg, unfocusedContainerColor = AppFieldBg, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = Color.White, unfocusedTextColor = Color.White), shape = RoundedCornerShape(12.dp))
}

@Composable
fun MapsScreen(mode: String = "Train") {
    val context = LocalContext.current
    val lancaster = GeoPoint(54.0475, -2.8015)
    AndroidView(factory = { MapView(context).apply { setTileSource(TileSourceFactory.MAPNIK); controller.setZoom(16.0); controller.setCenter(lancaster) } }, modifier = Modifier.fillMaxSize())
}