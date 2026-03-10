package com.example.transitnavproject.api

import com.example.transitnavproject.models.*
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface TransitNavApi {
    @GET("api/stations")
    suspend fun getStations(
        @Query("q") query: String? = null,
        @Query("limit") limit: Int? = null
    ): StationResponse

    @GET("api/routes/find")
    suspend fun findRoute(
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("algorithm") algorithm: String? = "dijkstra",
        @Query("weight") weight: String? = "duration"
    ): RouteResponse

    @GET("api/weather")
    suspend fun getWeather(): WeatherResponse

    @GET("api/departures")
    suspend fun getDepartures(
        @Query("limit") limit: Int? = null
    ): DeparturesResponse

    @GET("api/places/nearby")
    suspend fun getNearbyPlaces(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("radius") radius: Double? = 1.0,
        @Query("category") category: String? = null
    ): PlaceResponse

    @GET("api/taxi/providers")
    suspend fun getTaxiProviders(): TaxiProviderResponse

    @POST("api/users/login")
    suspend fun login(@Body credentials: Map<String, String>): GenericResponse<LoginResponseData>
}
