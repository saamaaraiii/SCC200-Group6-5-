package com.example.transitnavproject.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stations")
data class StationEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val code: String,
    val latitude: Double?,
    val longitude: Double?
)

@Entity(tableName = "route_segments")
data class RouteSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val from_station_id: Int,
    val to_station_id: Int,
    val distance_km: Double,
    val duration_mins: Int
)

@Entity(tableName = "bookings")
data class BookingEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val originName: String,
    val destinationName: String,
    val outboundDate: String,
    val returnDate: String?,
    val price: Double,
    val changes: Int,
    val timestamp: Long = System.currentTimeMillis()
)
