package com.example.transitnavproject.api

import retrofit2.http.GET
import retrofit2.http.Query

data class ApiResponse<T>(
    val status: String,
    val data: T
)

data class RouteResponse(
    val algorithm: String,
    val weight: String,
    val path: List<StationResponse>,
    val total_distance_km: Double,
    val total_duration_mins: Int
)

data class StationResponse(
    val id: Int,
    val name: String,
    val code: String,
    val latitude: Double,
    val longitude: Double
)

interface ApiService {
    @GET("api/routes/find")
    suspend fun findRoute(
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("algorithm") algorithm: String = "dijkstra",
        @Query("weight") weight: String = "duration"
    ): ApiResponse<RouteResponse>

    @GET("api/stations")
    suspend fun getStations(): ApiResponse<List<StationResponse>>
}
