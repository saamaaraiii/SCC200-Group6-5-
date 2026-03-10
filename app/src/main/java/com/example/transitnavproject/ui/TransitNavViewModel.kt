package com.example.transitnavproject

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.transitnavproject.api.ApiService
import com.example.transitnavproject.database.AppDatabase
import com.example.transitnavproject.database.StationEntity
import com.example.transitnavproject.database.RouteSegmentEntity
import com.example.transitnavproject.database.BookingEntity
import com.example.transitnavproject.models.RouteResult
import com.example.transitnavproject.models.Station
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

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

    private val _apiRouteResult = MutableStateFlow<RouteResult?>(null)
    val apiRouteResult: StateFlow<RouteResult?> = _apiRouteResult.asStateFlow()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("http://10.0.2.2:5000/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val apiService = retrofit.create(ApiService::class.java)

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            // Fetch stations from API
            try {
                val response = apiService.getStations()
                if (response.status == "success") {
                    val apiStations = response.data.map { 
                        Station(it.id, it.name, it.code, it.latitude, it.longitude)
                    }
                    _stations.value = apiStations
                    
                    // Sync local database with API stations
                    stationDao.insertAll(apiStations.map { 
                        StationEntity(it.id, it.name, it.code, it.latitude, it.longitude)
                    })
                }
            } catch (e: Exception) {
                Log.e("TransitNavViewModel", "Error fetching stations from API", e)
                // Fallback to local
                stationDao.getAllStations().collect { entities ->
                    _stations.value = entities.map { it.toDomain() }
                }
            }

            _isReady.value = true

            // Observe bookings
            launch {
                bookingDao.getAllBookings().collect { entities ->
                    _bookings.value = entities
                }
            }
        }
    }

    fun findRouteWithApi(fromId: Int, toId: Int) {
        viewModelScope.launch {
            try {
                val fromStation = _stations.value.find { it.id == fromId }
                val toStation = _stations.value.find { it.id == toId }
                
                if (fromStation != null && toStation != null) {
                    val response = apiService.findRoute(fromStation.code, toStation.code)
                    if (response.status == "success") {
                        val routeData = response.data
                        _apiRouteResult.value = RouteResult(
                            stationIds = routeData.path.map { it.id },
                            totalDurationMins = routeData.total_duration_mins,
                            totalDistanceKm = routeData.total_distance_km
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("TransitNavViewModel", "Error fetching route from API", e)
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

    fun deleteBooking(booking: BookingEntity) {
        viewModelScope.launch {
            bookingDao.delete(booking)
        }
    }

    private fun StationEntity.toDomain() = Station(
        id = id,
        name = name,
        code = code,
        latitude = latitude ?: 0.0,
        longitude = longitude ?: 0.0
    )
}
