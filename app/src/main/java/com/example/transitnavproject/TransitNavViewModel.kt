package com.example.transitnavproject

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.transitnavproject.database.AppDatabase
import com.example.transitnavproject.database.StationEntity
import com.example.transitnavproject.database.RouteSegmentEntity
import com.example.transitnavproject.database.BookingEntity
import com.example.transitnavproject.models.Station
import com.example.transitnavproject.pathfinding.PathfindingService
import com.example.transitnavproject.pathfinding.RouteGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TransitNavViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application, viewModelScope)
    private val stationDao = database.stationDao()
    private val routeSegmentDao = database.routeSegmentDao()
    private val bookingDao = database.bookingDao()

    private val _stations = MutableStateFlow<List<Station>>(emptyList())
    val stations: StateFlow<List<Station>> = _stations.asStateFlow()

    private val _bookings = MutableStateFlow<List<BookingEntity>>(emptyList())
    val bookings: StateFlow<List<BookingEntity>> = _bookings.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    val pathfindingService = PathfindingService()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            // 1. Ensure we have data (Seed if empty)
            val existingStations = stationDao.getAllStations().first()
            if (existingStations.isEmpty()) {
                seedDatabase()
            }

            // 2. Observe stations
            launch {
                stationDao.getAllStations().collect { entities ->
                    _stations.value = entities.map { it.toDomain() }
                    
                    // 3. Load segments and build graph once stations are available
                    val segments = routeSegmentDao.getAllSegments()
                    val graph = RouteGraph()
                    segments.forEach { seg ->
                        graph.addEdge(seg.from_station_id, seg.to_station_id, seg.duration_mins.toDouble())
                    }
                    pathfindingService.replaceGraph(graph)
                    _isReady.value = true
                }
            }

            // 4. Observe bookings
            launch {
                bookingDao.getAllBookings().collect { entities ->
                    _bookings.value = entities
                }
            }
        }
    }

    fun saveBooking(
        originName: String,
        destinationName: String,
        outboundDate: String,
        returnDate: String?,
        price: Double,
        changes: Int
    ) {
        viewModelScope.launch {
            bookingDao.insert(
                BookingEntity(
                    originName = originName,
                    destinationName = destinationName,
                    outboundDate = outboundDate,
                    returnDate = returnDate,
                    price = price,
                    changes = changes
                )
            )
        }
    }

    private suspend fun seedDatabase() {
        val stations = listOf(
            StationEntity(1, "London Euston", "EUS", 51.5282, -0.1337),
            StationEntity(2, "Birmingham New Street", "BHM", 52.4778, -1.8980),
            StationEntity(3, "Manchester Piccadilly", "MAN", 53.4770, -2.2301),
            StationEntity(4, "Liverpool Lime Street", "LIV", 53.4075, -2.9988),
            StationEntity(5, "Leeds", "LDS", 53.7944, -1.5473),
            StationEntity(6, "Sheffield", "SHF", 53.3781, -1.4620),
            StationEntity(7, "York", "YRK", 53.9581, -1.0916),
            StationEntity(8, "Newcastle", "NCL", 54.9683, -1.6174),
            StationEntity(9, "Edinburgh Waverley", "EDB", 55.9521, -3.1895),
            StationEntity(10, "Glasgow Central", "GLC", 55.8599, -4.2572)
        )
        stationDao.insertAll(stations)

        val segments = listOf(
            RouteSegmentEntity(1, 1, 2, 170.0, 82),
            RouteSegmentEntity(2, 2, 1, 170.0, 82),
            RouteSegmentEntity(3, 2, 3, 120.0, 88),
            RouteSegmentEntity(4, 3, 2, 120.0, 88),
            RouteSegmentEntity(5, 3, 4, 50.0, 35),
            RouteSegmentEntity(6, 4, 3, 50.0, 35),
            RouteSegmentEntity(7, 3, 6, 60.0, 48),
            RouteSegmentEntity(8, 6, 3, 60.0, 48),
            RouteSegmentEntity(9, 6, 5, 40.0, 35),
            RouteSegmentEntity(10, 5, 6, 40.0, 35),
            RouteSegmentEntity(11, 5, 7, 45.0, 25),
            RouteSegmentEntity(12, 7, 5, 45.0, 25),
            RouteSegmentEntity(13, 7, 8, 80.0, 55),
            RouteSegmentEntity(14, 8, 7, 80.0, 55),
            RouteSegmentEntity(15, 8, 9, 160.0, 90),
            RouteSegmentEntity(16, 9, 8, 160.0, 90),
            RouteSegmentEntity(17, 9, 10, 75.0, 50),
            RouteSegmentEntity(18, 10, 9, 75.0, 50),
            // Adding Direct Birmingham to Glasgow (and back)
            RouteSegmentEntity(19, 2, 10, 450.0, 240),
            RouteSegmentEntity(20, 10, 2, 450.0, 240)
        )
        routeSegmentDao.insertAll(segments)
    }

    private fun StationEntity.toDomain() = Station(
        id = id,
        name = name,
        code = code,
        latitude = latitude ?: 0.0,
        longitude = longitude ?: 0.0
    )
}
