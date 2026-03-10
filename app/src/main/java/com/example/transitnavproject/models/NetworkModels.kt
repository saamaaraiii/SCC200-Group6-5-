package com.example.transitnavproject.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class StationResponse(
    val status: String,
    val count: Int,
    val data: List<Station>
)

@JsonClass(generateAdapter = true)
data class Station(
    val id: Int,
    val name: String,
    val code: String,
    val latitude: Double?,
    val longitude: Double?
)

@JsonClass(generateAdapter = true)
data class RouteResponse(
    val status: String,
    val data: RouteData
)

@JsonClass(generateAdapter = true)
data class RouteData(
    val algorithm: String,
    val weight: String,
    val path: List<Station>,
    val legs: List<RouteLeg>,
    val changes: Int,
    @Json(name = "total_distance_km") val totalDistanceKm: Double,
    @Json(name = "total_duration_mins") val totalDurationMins: Int
)

@JsonClass(generateAdapter = true)
data class RouteLeg(
    @Json(name = "from_stop") val fromStop: String,
    @Json(name = "to_stop") val toStop: String,
    @Json(name = "from_stop_id") val fromStopId: Int,
    @Json(name = "to_stop_id") val toStopId: Int,
    @Json(name = "distance_km") val distanceKm: Double,
    @Json(name = "duration_mins") val durationMins: Int
)

@JsonClass(generateAdapter = true)
data class WeatherResponse(
    val status: String,
    val data: WeatherData
)

@JsonClass(generateAdapter = true)
data class WeatherData(
    val location: WeatherLocation,
    val conditions: WeatherCondition,
    val temperature: TemperatureData,
    val wind: WindData,
    val humidity: Int?,
    val visibility: Int?,
    val clouds: Int?,
    val timestamp: Long?
)

@JsonClass(generateAdapter = true)
data class WeatherLocation(
    val name: String?,
    val latitude: Double?,
    val longitude: Double?
)

@JsonClass(generateAdapter = true)
data class WeatherCondition(
    val main: String?,
    val description: String?,
    val icon: String?
)

@JsonClass(generateAdapter = true)
data class TemperatureData(
    val current: Double?,
    @Json(name = "feels_like") val feelsLike: Double?,
    val min: Double?,
    val max: Double?
)

@JsonClass(generateAdapter = true)
data class WindData(
    val speed: Double?,
    val direction: Int?,
    val gust: Double?
)

@JsonClass(generateAdapter = true)
data class DeparturesResponse(
    val status: String,
    val station: String?,
    val crs: String?,
    val timestamp: String?,
    val count: Int,
    val data: List<TrainService>
)

@JsonClass(generateAdapter = true)
data class TrainService(
    @Json(name = "scheduled_departure") val scheduledDeparture: String?,
    @Json(name = "estimated_departure") val estimatedDeparture: String?,
    val platform: String?,
    val operator: String?,
    val origin: String?,
    val destination: String?,
    @Json(name = "delay_reason") val delayReason: String?,
    @Json(name = "calling_points") val callingPoints: List<CallingPoint>? = null
)

@JsonClass(generateAdapter = true)
data class CallingPoint(
    val location: String?,
    @Json(name = "scheduled_time") val scheduledTime: String?,
    @Json(name = "estimated_time") val estimatedTime: String?
)

// --- Added Models for Places, Taxis, and Users ---

@JsonClass(generateAdapter = true)
data class PlaceResponse(
    val status: String,
    val count: Int,
    val data: List<Place>
)

@JsonClass(generateAdapter = true)
data class Place(
    @Json(name = "osm_id") val osmId: Long,
    val name: String,
    val category: String,
    val type: String?,
    val address: String?,
    val town: String?,
    val latitude: Double?,
    val longitude: Double?,
    val stars: Double?,
    @Json(name = "distance_km") val distanceKm: Double? = null
)

@JsonClass(generateAdapter = true)
data class TaxiProviderResponse(
    val status: String,
    val count: Int,
    val data: List<TaxiProvider>
)

@JsonClass(generateAdapter = true)
data class TaxiProvider(
    @Json(name = "provider_id") val providerId: String,
    val name: String,
    val phone: String,
    @Json(name = "base_fare_gbp") val baseFareGbp: Double,
    @Json(name = "per_km_gbp") val perKmGbp: Double
)

@JsonClass(generateAdapter = true)
data class GenericResponse<T>(
    val status: String,
    val data: T
)

@JsonClass(generateAdapter = true)
data class User(
    @Json(name = "user_id") val userId: Int,
    val name: String,
    val email: String,
    @Json(name = "accessibility_needs") val accessibilityNeeds: Boolean
)

@JsonClass(generateAdapter = true)
data class LoginResponseData(
    val token: String,
    @Json(name = "expires_in_seconds") val expiresInSeconds: Int,
    val user: User
)
