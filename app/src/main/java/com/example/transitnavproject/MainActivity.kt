package com.example.transitnavproject

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.transitnavproject.database.BookingEntity
import com.example.transitnavproject.models.RouteResult
import com.example.transitnavproject.models.Station
import org.osmdroid.config.Configuration
import org.osmdroid.views.MapView
import org.osmdroid.util.GeoPoint
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import java.util.Locale

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

val railcards = listOf("No Railcard", "16-25 Railcard", "Senior Railcard", "Two Together Railcard", "Disabled Persons Railcard")

@Composable
fun TransitNavHomeScreen(viewModel: TransitNavViewModel = viewModel()) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSearchScreen by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color(0xFF070B14),
        bottomBar = {
            if (!showSearchScreen) {
                NavigationBar(
                    containerColor = Color(0xFF070B14),
                    tonalElevation = 8.dp
                ) {
                    val tabs = listOf(
                        Triple(0, Icons.Default.Home, "Home"),
                        Triple(1, Icons.Default.Map, "Map"),
                        Triple(2, Icons.Default.ConfirmationNumber, "Tickets"),
                        Triple(3, Icons.Default.Person, "Account")
                    )
                    tabs.forEach { (index, icon, label) ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Icon(icon, null) },
                            label = { Text(label, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                indicatorColor = Color(0xFF2196F3).copy(alpha = 0.6f),
                                unselectedIconColor = Color.Gray,
                                selectedTextColor = Color.White,
                                unselectedTextColor = Color.Gray
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070B14))
            .padding(if (showSearchScreen) PaddingValues(0.dp) else padding)) {
            when (selectedTab) {
                0 -> {
                    if (showSearchScreen) {
                        SearchTrainsScreen(viewModel, onBack = { showSearchScreen = false })
                    } else {
                        DashboardScreen(onNavigateToSearch = { showSearchScreen = true })
                    }
                }
                1 -> MapsScreen()
                2 -> TicketsTabScreen(viewModel)
                3 -> Box(Modifier.fillMaxSize().background(Color(0xFF070B14)), contentAlignment = Alignment.Center) { Text("Account", color = Color.White) }
            }
        }
    }
}

@Composable
fun TicketsTabScreen(viewModel: TransitNavViewModel) {
    val bookings by viewModel.bookings.collectAsState()
    var selectedBookingForQr by remember { mutableStateOf<BookingEntity?>(null) }
    var bookingToDelete by remember { mutableStateOf<BookingEntity?>(null) }

    Column(modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF070B14))
        .padding(16.dp)) {
        Text("My Tickets", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(16.dp))

        if (bookings.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No tickets booked yet", color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(bookings) { booking ->
                    BookingCard(
                        booking = booking,
                        onShowTicket = { selectedBookingForQr = it },
                        onDelete = { bookingToDelete = it }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }

    selectedBookingForQr?.let { booking ->
        Dialog(onDismissRequest = { selectedBookingForQr = null }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141A26)),
                modifier = Modifier.padding(16.dp).fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Ticket for ${booking.originName}", color = Color.Gray, fontSize = 12.sp)
                    Text("to ${booking.destinationName}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    MockQRCode(data = "TICKET-${booking.id}-${booking.timestamp}", modifier = Modifier.size(200.dp))
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text("Reference: TNP-${booking.id}-${booking.timestamp.toString().takeLast(4)}", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(onClick = { selectedBookingForQr = null }, modifier = Modifier.fillMaxWidth()) {
                        Text("Close")
                    }
                }
            }
        }
    }

    bookingToDelete?.let { booking ->
        AlertDialog(
            onDismissRequest = { bookingToDelete = null },
            title = { Text("Delete Ticket", color = Color.White) },
            text = { Text("Are you sure you want to delete this ticket from ${booking.originName} to ${booking.destinationName}?", color = Color.Gray) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteBooking(booking)
                    bookingToDelete = null
                }) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { bookingToDelete = null }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF141A26)
        )
    }
}

@Composable
fun MockQRCode(data: String, modifier: Modifier = Modifier) {
    val gridSize = 12  // renamed from size
    val seed = data.hashCode().toLong()
    val random = remember(data) { java.util.Random(seed) }
    val grid = remember(data) {
        Array(gridSize) { BooleanArray(gridSize) { random.nextBoolean() } }
    }

    Box(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).padding(16.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cellSizeX = size.width / gridSize   // size.width now unambiguous
            val cellSizeY = size.height / gridSize

            val cornerSize = 3
            for (i in 0 until gridSize) {
                for (j in 0 until gridSize) {
                    val isCorner = (i < cornerSize && j < cornerSize) ||
                            (i >= gridSize - cornerSize && j < cornerSize) ||
                            (i < cornerSize && j >= gridSize - cornerSize)

                    val shouldFill = if (isCorner) {
                        val relI = if (i >= gridSize - cornerSize) i - (gridSize - cornerSize) else i
                        val relJ = if (j >= gridSize - cornerSize) j - (gridSize - cornerSize) else j
                        !(relI == 1 && relJ == 1)
                    } else {
                        grid[i][j]
                    }

                    if (shouldFill) {
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(i * cellSizeX, j * cellSizeY),
                            size = Size(cellSizeX, cellSizeY)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BookingCard(
    booking: BookingEntity,
    onShowTicket: (BookingEntity) -> Unit,
    onDelete: (BookingEntity) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141A26)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(booking.originName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(booking.destinationName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                IconButton(onClick = { onDelete(booking) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Ticket", tint = Color.Gray.copy(alpha = 0.6f))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Outbound: ${booking.outboundDate} Mar 2026", color = Color.Gray, fontSize = 14.sp)
            if (booking.returnDate != null) {
                Text("Return: ${booking.returnDate} Mar 2026", color = Color(0xFF2196F3), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(if (booking.changes == 0) "Direct" else "${booking.changes} change(s)", color = Color.Gray, fontSize = 12.sp)
                    Text("£${String.format(Locale.UK, "%.2f", booking.price)}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                }
                Button(
                    onClick = { onShowTicket(booking) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                ) {
                    Icon(Icons.Default.QrCode, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Show Ticket")
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(onNavigateToSearch: () -> Unit) {
    val context = LocalContext.current
    Column(modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF070B14))
        .padding(16.dp)) {
        // Profile Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141A26)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).background(Color(0xFF232B3E), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, null, tint = Color.LightGray)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Welcome back", color = Color.Gray, fontSize = 12.sp)
                    Text("Alex Traveller", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Icon(Icons.Default.Settings, null, tint = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search Trigger Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141A26)),
            modifier = Modifier.fillMaxWidth().clickable { onNavigateToSearch() }
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, tint = Color(0xFF2196F3))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Search all trains", color = Color.Gray, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Transport Modes Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141A26)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DashboardTransportItem("Train", Icons.Default.Train, true)
                DashboardTransportItem("Bus", Icons.Default.DirectionsBus, false)
                DashboardTransportItem("Taxi", Icons.Default.LocalTaxi, false)
                DashboardTransportItem("Walk", Icons.AutoMirrored.Filled.DirectionsWalk, false)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Map Preview Section
        Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(24.dp))) {
            AndroidView(
                factory = {
                    MapView(context).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        controller.setZoom(12.0)
                        controller.setCenter(GeoPoint(54.1265, -3.2285))
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            Text(
                "Barrow-in-\nFurness",
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 34.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Station Info Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color(0xFF2196F3), shape = RoundedCornerShape(4.dp)) {
                    Text("STATION", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Barrow-in-Furness", color = Color.White, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.Default.StarBorder, null, tint = Color.White)
            }
        }
    }
}

@Composable
fun DashboardTransportItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color(0xFF2196F3) else Color(0xFF232B3E))
            .padding(vertical = 12.dp, horizontal = 16.dp)
            .width(50.dp)
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
fun SearchTrainsScreen(viewModel: TransitNavViewModel, onBack: () -> Unit) {
    BackHandler { onBack() }
    val scrollState = rememberScrollState()
    val stations by viewModel.stations.collectAsState()
    val isReady by viewModel.isReady.collectAsState()

    var departureTime by remember { mutableStateOf("") }
    var originId by remember { mutableStateOf<Int?>(null) }
    var destinationId by remember { mutableStateOf<Int?>(null) }
    var tripType by remember { mutableStateOf("Single") }
    var passengerCount by remember { mutableStateOf(1) }
    var selectedRailcard by remember { mutableStateOf(railcards[0]) }

    var selectedOutboundDate by remember { mutableStateOf(5) }
    var selectedReturnDate by remember { mutableStateOf<Int?>(null) }
    var pickingReturn by remember { mutableStateOf(false) }

    val result by viewModel.apiRouteResult.collectAsState()
    var showPaymentDialog by remember { mutableStateOf(false) }
    var paymentSuccess by remember { mutableStateOf(false) }

    if (showPaymentDialog) {
        Dialog(onDismissRequest = { if (paymentSuccess) { showPaymentDialog = false; onBack() } else showPaymentDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141A26)),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val finalPrice = calculatePrice(result, passengerCount, selectedRailcard, tripType)
                    val originName = stations.find { it.id == originId }?.name ?: "Unknown"
                    val destinationName = stations.find { it.id == destinationId }?.name ?: "Unknown"

                    if (!paymentSuccess) {
                        Text("Confirm Booking", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF0F141F), RoundedCornerShape(12.dp)).padding(16.dp)) {
                            Text("Journey", color = Color.Gray, fontSize = 12.sp)
                            Text("$originName → $destinationName", color = Color.White, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Outbound: $selectedOutboundDate Mar 2026", color = Color.White, fontSize = 14.sp)
                            if (tripType == "Return" && selectedReturnDate != null) {
                                Text("Return: $selectedReturnDate Mar 2026", color = Color(0xFF2196F3), fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Tickets: $passengerCount ($tripType)", color = Color.Gray, fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Amount: £${String.format(Locale.UK, "%.2f", finalPrice)}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = { paymentSuccess = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                        ) {
                            Text("Pay Now")
                        }
                    } else {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Payment Successful!", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Your ticket is in the Tickets tab.", color = Color.Gray, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                val changes = if ((result?.stationIds?.size ?: 0) > 2) (result?.stationIds?.size ?: 0) - 2 else 0

                                viewModel.saveBooking(
                                    originName = originName,
                                    destinationName = destinationName,
                                    outboundDate = selectedOutboundDate.toString(),
                                    returnDate = if (tripType == "Return") selectedReturnDate?.toString() else null,
                                    price = finalPrice,
                                    changes = changes
                                )
                                showPaymentDialog = false
                                onBack()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF070B14)).verticalScroll(scrollState).padding(16.dp)) {
        // Custom Header
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(40.dp).clickable { onBack() }, shape = CircleShape, color = Color(0xFF1E2A3A).copy(alpha = 0.5f)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.ChevronLeft, null, tint = Color.White) }
            }
            Text("Search Trains", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(40.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Plan Your Rail Journey", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text("Enter where you are going and customise your trip", color = Color.Gray, fontSize = 14.sp)

        Spacer(modifier = Modifier.height(24.dp))

        // WHERE Card
        SearchSectionCard(title = "Where") {
            SearchField("Origin", "Type departure station", originId, stations) { originId = it }
            Spacer(modifier = Modifier.height(12.dp))
            SearchField("Destination", "Type arrival station", destinationId, stations) { destinationId = it }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // NOC Card
        SearchSectionCard(title = "Local Operators (NOC)") {
            Text("ARCT, BLAC, KLCO, SCCU, SCMY, NUTT", color = Color.Gray, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // WHEN Toggle
        Text("When", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF141A26), RoundedCornerShape(12.dp)).padding(4.dp)) {
            listOf("Single", "Return", "Open Return").forEach { type ->
                val isSel = tripType == type
                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (isSel) Color(0xFF2196F3) else Color.Transparent).clickable { tripType = type; if (type == "Single") { selectedReturnDate = null; pickingReturn = false } }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text(type, color = if (isSel) Color.White else Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // TRAVEL DATES Card
        SearchSectionCard(title = "Travel Dates") {
            Row {
                DateInfo(
                    label = "Outbound",
                    date = "Thu, $selectedOutboundDate Mar 2026",
                    modifier = Modifier.weight(1f).clickable { pickingReturn = false }.border(if (!pickingReturn) 2.dp else 0.dp, if (!pickingReturn) Color(0xFF2196F3) else Color.Transparent, RoundedCornerShape(12.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                DateInfo(
                    label = "Return",
                    date = if (tripType == "Single") "Not required" else (selectedReturnDate?.let { "Thu, $it Mar 2026" } ?: "Not selected"),
                    modifier = Modifier.weight(1f).clickable(enabled = tripType != "Single") { pickingReturn = true }.border(if (pickingReturn) 2.dp else 0.dp, if (pickingReturn) Color(0xFF2196F3) else Color.Transparent, RoundedCornerShape(12.dp))
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("March 2026", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            CalendarGridInteractive(
                selectedOutboundDate = selectedOutboundDate,
                selectedReturnDate = selectedReturnDate
            ) { date ->
                if (pickingReturn) {
                    if (date >= selectedOutboundDate) {
                        selectedReturnDate = date
                    }
                } else {
                    selectedOutboundDate = date
                    if (selectedReturnDate != null && date > selectedReturnDate!!) {
                        selectedReturnDate = null
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // PASSENGERS Card
        SearchSectionCard(title = "Passengers") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$passengerCount passenger${if (passengerCount > 1) "s" else ""}", color = Color.White, modifier = Modifier.weight(1f))
                Row(modifier = Modifier.background(Color(0xFF232B3E), RoundedCornerShape(8.dp)).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (passengerCount > 1) passengerCount-- }) { Icon(Icons.Default.Remove, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                    HorizontalDivider(modifier = Modifier.height(16.dp).width(1.dp), color = Color.Gray)
                    IconButton(onClick = { passengerCount++ }) { Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("Railcard", color = Color.Gray, fontSize = 12.sp)
            RailcardPicker(selectedRailcard) { selectedRailcard = it }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (originId != null && destinationId != null) {
                    departureTime = currentTimeString()
                    viewModel.findRouteWithApi(originId!!, destinationId!!)
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = isReady && originId != null && destinationId != null && originId != destinationId,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
        ) {
            if (!isReady) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            else Text("Find Trains", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        result?.let { res ->
            val changeCount = if (res.stationIds.size > 2) res.stationIds.size - 2 else 0
            val changeStations = if (changeCount > 0) {
                res.stationIds.drop(1).dropLast(1).map { id ->
                    stations.find { it.id == id }?.name ?: "Unknown"
                }.joinToString(", ")
            } else null

            Spacer(modifier = Modifier.height(16.dp))
            SearchSectionCard("Results") {
                // Uses the real time captured when "Find Trains" was tapped
                val depTime = departureTime.ifEmpty { currentTimeString() }
                val arrTime = calculateArrivalTime(depTime, res.totalDurationMins ?: 0)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("$depTime → $arrTime", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("${res.totalDurationMins} mins • ${if (changeCount == 0) "Direct" else "$changeCount change(s)"}", color = Color.Gray, fontSize = 14.sp)
                        if (changeStations != null) {
                            Text("Changes at: $changeStations", color = Color(0xFF2196F3).copy(alpha = 0.8f), fontSize = 12.sp)
                        }
                    }
                    val finalPrice = calculatePrice(res, passengerCount, selectedRailcard, tripType)
                    Text("£${String.format(Locale.UK, "%.2f", finalPrice)}", color = Color(0xFF2196F3), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showPaymentDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = (tripType != "Return" || selectedReturnDate != null)
                ) {
                    Text(if (tripType == "Return" && selectedReturnDate == null) "Select Return Date" else "Buy Ticket")
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
fun CalendarGridInteractive(
    selectedOutboundDate: Int,
    selectedReturnDate: Int?,
    onDateSelect: (Int) -> Unit
) {
    val dates = (1..31).toList()
    val blue = Color(0xFF2196F3)

    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = Modifier.height(200.dp),
        userScrollEnabled = false
    ) {
        items(dates) { date ->
            val isOutbound = date == selectedOutboundDate
            val isReturn = date == selectedReturnDate
            val inRange = selectedReturnDate != null && date > selectedOutboundDate && date < selectedReturnDate

            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .padding(vertical = 4.dp)
                    .clip(if (isOutbound || isReturn) CircleShape else RoundedCornerShape(0.dp))
                    .background(if (isOutbound || isReturn) blue else if (inRange) blue.copy(alpha = 0.2f) else Color.Transparent)
                    .clickable { onDateSelect(date) },
                contentAlignment = Alignment.Center
            ) {
                // If in range but not ends, we want a continuous highlight
                if (inRange) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            color = blue.copy(alpha = 0.2f),
                            size = Size(size.width, size.height)
                        )
                    }
                }
                Text(
                    "$date",
                    color = if (isOutbound || isReturn) Color.White else Color.Gray,
                    fontSize = 14.sp,
                    fontWeight = if (isOutbound || isReturn) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun RailcardPicker(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(modifier = Modifier.clickable { expanded = true }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(selected, color = Color.White, fontWeight = FontWeight.Medium)
            Icon(Icons.Default.UnfoldMore, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(Color(0xFF1E2A3A))) {
            railcards.forEach { card ->
                DropdownMenuItem(text = { Text(card, color = Color.White) }, onClick = { onSelect(card); expanded = false })
            }
        }
    }
}

fun calculatePrice(result: RouteResult?, passengers: Int, railcard: String, tripType: String): Double {
    if (result == null) return 0.0
    val basePricePerMin = 0.50
    val basePrice = (result.totalDurationMins ?: 0) * basePricePerMin
    val multiplier = when (tripType) {
        "Return" -> 1.8 // Slightly discounted return
        "Open Return" -> 2.0
        else -> 1.0
    }
    val discount = if (railcard != "No Railcard") 0.34 else 0.0
    return (basePrice * multiplier * passengers) * (1.0 - discount)
}

fun calculateArrivalTime(startTime: String, durationMins: Int): String {
    val parts = startTime.split(":")
    var hours = parts[0].toInt()
    var mins = parts[1].toInt() + durationMins
    hours += mins / 60
    mins %= 60
    return String.format(Locale.UK, "%02d:%02d", hours % 24, mins)
}

@Composable
fun SearchSectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF141A26)), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun SearchField(label: String, placeholder: String, selectedId: Int?, stations: List<Station>, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val station = stations.find { it.id == selectedId }
    Column {
        Text(label, color = Color.Gray, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF0F141F), RoundedCornerShape(12.dp)).clickable { expanded = true }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, null, tint = Color(0xFF2196F3), modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(station?.name ?: placeholder, color = if (station != null) Color.White else Color.Gray)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(Color(0xFF1E2A3A))) {
            stations.forEach { s ->
                DropdownMenuItem(text = { Text(s.name, color = Color.White) }, onClick = { onSelect(s.id); expanded = false })
            }
        }
    }
}

@Composable
fun DateInfo(label: String, date: String, modifier: Modifier) {
    Column(modifier = modifier.background(Color(0xFF0F141F), RoundedCornerShape(12.dp)).padding(12.dp)) {
        Text(label, color = Color.Gray, fontSize = 11.sp)
        Text(date, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun MapsScreen() {
    val context = LocalContext.current
    AndroidView(factory = { MapView(context).apply { setTileSource(TileSourceFactory.MAPNIK); controller.setZoom(10.0); controller.setCenter(GeoPoint(53.9000, -2.7000)) } }, modifier = Modifier.fillMaxSize())
}

fun currentTimeString(): String {
    val cal = java.util.Calendar.getInstance()
    return String.format(java.util.Locale.UK, "%02d:%02d",
        cal.get(java.util.Calendar.HOUR_OF_DAY),
        cal.get(java.util.Calendar.MINUTE))
}
