// Android Kotlin Client Example
// Add to your Android project to communicate with the DB_Scc API

package com.example.dbscc.api

import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import okhttp3.*
import java.io.IOException

/**
 * DB_Scc API Client for Android
 * Handles all GET requests to retrieve data from multiple streams
 */
class DBSccAPIClient(
    private val baseUrl: String = "http://10.0.2.2:5000"  // 10.0.2.2 for emulator, change to server IP
) {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    // MARK: - Generic Request Handler

    private suspend inline fun <reified T> makeRequest(
        endpoint: String,
        queryParams: Map<String, String>? = null
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            val urlBuilder = HttpUrl.parse("$baseUrl$endpoint")?.newBuilder()
                ?: return@withContext Result.failure(Exception("Invalid URL"))

            queryParams?.forEach { (key, value) ->
                urlBuilder.addQueryParameter(key, value)
            }

            val request = Request.Builder()
                .url(urlBuilder.build())
                .header("Accept", "application/json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code()}"))
                }

                val body = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty response"))

                val data = json.decodeFromString<T>(body)
                Result.success(data)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // MARK: - Lancaster Facility Endpoints

    suspend fun getStationInfo(): Result<LancasterFacilityResponse> =
        makeRequest("/api/lancs_fac")

    suspend fun getAccessibilityInfo(): Result<AccessibilityResponse> =
        makeRequest("/api/lancs_fac/accessibility")

    suspend fun getLiftInfo(): Result<LiftResponse> =
        makeRequest("/api/lancs_fac/lifts")

    suspend fun getTicketInfo(): Result<TicketResponse> =
        makeRequest("/api/lancs_fac/ticket_buying")

    suspend fun getTransportLinks(): Result<TransportLinksResponse> =
        makeRequest("/api/lancs_fac/transport_links")

    suspend fun getCyclingInfo(): Result<CyclingResponse> =
        makeRequest("/api/lancs_fac/cycling")

    suspend fun getParkingInfo(): Result<ParkingResponse> =
        makeRequest("/api/lancs_fac/parking")

    // MARK: - Train Departure Endpoints

    suspend fun getDepartures(limit: Int? = null): Result<DeparturesResponse> {
        val params = limit?.let { mapOf("limit" to it.toString()) }
        return makeRequest("/api/departures", params)
    }

    suspend fun searchDepartures(destination: String, limit: Int? = null): Result<DeparturesResponse> {
        val params = mutableMapOf("destination" to destination)
        limit?.let { params["limit"] = it.toString() }
        return makeRequest("/api/departures/search", params)
    }

    // MARK: - Bus Live Tracking Endpoints

    suspend fun getBusLive(line: String? = null, limit: Int? = null): Result<BusResponse> {
        val params = mutableMapOf<String, String>()
        line?.let { params["line"] = it }
        limit?.let { params["limit"] = it.toString() }
        return makeRequest("/api/bus/live", params.ifEmpty { null })
    }

    suspend fun getBusLocation(vehicle: String): Result<BusLocationResponse> =
        makeRequest("/api/bus/live/location", mapOf("vehicle" to vehicle))

    // MARK: - Delay Codes Endpoints

    suspend fun getDelayCodes(code: String? = null): Result<DelayCodesResponse> {
        val params = code?.let { mapOf("code" to it) }
        return makeRequest("/api/delay_codes", params)
    }

    suspend fun searchDelayCodes(query: String): Result<DelayCodesResponse> =
        makeRequest("/api/delay_codes/search", mapOf("q" to query))

    // MARK: - Weather Endpoint

    suspend fun getWeather(): Result<WeatherResponse> =
        makeRequest("/api/weather")

    // MARK: - Metadata Endpoints

    suspend fun getEndpoints(): Result<EndpointsResponse> =
        makeRequest("/api/endpoints")
}

// MARK: - Data Models (Serializable)

@Serializable
data class LancasterFacilityResponse(
    val status: String,
    val data: StationData
)

@Serializable
data class StationData(
    val station_name: String?,
    val crs_code: String?,
    val nrcc_code: String?,
    val location: Location?,
    val address: Address?,
    val operator: String?,
    val staffing_level: String?,
    val alerts: List<Alert>? = null
)

@Serializable
data class Location(
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class Address(
    val addressLine1: String?,
    val addressLine2: String?,
    val addressLine3: String?,
    val postcode: String?
)

@Serializable
data class Alert(
    val title: String?,
    val description: String?
)

@Serializable
data class AccessibilityResponse(
    val status: String,
    val data: AccessibilityData
)

@Serializable
data class AccessibilityData(
    val step_free_category: String?,
    val tactile_paving: String?,
    val induction_loops: String?,
    val wheelchairs_available: Boolean?,
    val passenger_assistance: List<String>? = null,
    val train_ramp: Boolean?,
    val ticket_barriers: Boolean?
)

@Serializable
data class LiftResponse(
    val status: String,
    val data: LiftData
)

@Serializable
data class LiftData(
    val available: Boolean?,
    val statement: String?,
    val lifts_info: List<Map<String, @Serializable(with = JsonElementSerializer::class) Any>>? = null
)

@Serializable
data class TicketResponse(
    val status: String,
    val data: TicketData
)

@Serializable
data class TicketData(
    val ticket_office: Map<String, @Serializable(with = JsonElementSerializer::class) Any>?,
    val machines_available: Boolean?,
    val collect_online: Map<String, @Serializable(with = JsonElementSerializer::class) Any>?,
    val pay_as_you_go: Map<String, @Serializable(with = JsonElementSerializer::class) Any>?
)

@Serializable
data class TransportLinksResponse(
    val status: String,
    val data: TransportData
)

@Serializable
data class TransportData(
    val bus: Boolean?,
    val replacement_bus: Boolean?,
    val taxi: Boolean?,
    val taxi_ranks: List<String>? = null,
    val airport: Boolean?,
    val underground: Boolean?,
    val car_hire: Boolean?,
    val port: Boolean?
)

@Serializable
data class CyclingResponse(
    val status: String,
    val data: CyclingData
)

@Serializable
data class CyclingData(
    val storage_available: Boolean?,
    val spaces: Int?,
    val storage_types: List<String>? = null,
    val sheltered: Boolean?,
    val cctv: Boolean?,
    val location: String?
)

@Serializable
data class ParkingResponse(
    val status: String,
    val data: ParkingData
)

@Serializable
data class ParkingData(
    val car_parks: Map<String, @Serializable(with = JsonElementSerializer::class) Any>?
)

@Serializable
data class DeparturesResponse(
    val status: String,
    val station: String?,
    val crs: String?,
    val timestamp: String?,
    val count: Int,
    val data: List<TrainService>
)

@Serializable
data class TrainService(
    val scheduled_departure: String?,
    val estimated_departure: String?,
    val platform: String?,
    val operator: String?,
    val origin: String?,
    val destination: String?,
    val delay_reason: String?,
    val calling_points: List<CallingPoint>? = null
)

@Serializable
data class CallingPoint(
    val location: String?,
    val scheduled_time: String?,
    val estimated_time: String?
)

@Serializable
data class BusResponse(
    val status: String,
    val timestamp: String?,
    val count: Int,
    val data: List<BusVehicle>
)

@Serializable
data class BusVehicle(
    val line_ref: String?,
    val vehicle_ref: String?,
    val direction: String?,
    val origin_name: String?,
    val destination_name: String?,
    val origin_time: String?,
    val destination_time: String?,
    val location: BusLocation?,
    val bearing: String?,
    val recorded_at: String?
)

@Serializable
data class BusLocation(
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class BusLocationResponse(
    val status: String,
    val data: BusVehicle?
)

@Serializable
data class DelayCode(
    val Code: String,
    val Cause: String,
    val Abbreviation: String
)

@Serializable
data class DelayCodesResponse(
    val status: String,
    val count: Int,
    val data: List<DelayCode>? = null,
    val query: String? = null,
    val message: String? = null
)

@Serializable
data class WeatherResponse(
    val status: String,
    val data: WeatherData
)

@Serializable
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

@Serializable
data class WeatherLocation(
    val name: String?,
    val latitude: Double?,
    val longitude: Double?
)

@Serializable
data class WeatherCondition(
    val main: String?,
    val description: String?,
    val icon: String?
)

@Serializable
data class TemperatureData(
    val current: Double?,
    val feels_like: Double?,
    val min: Double?,
    val max: Double?
)

@Serializable
data class WindData(
    val speed: Double?,
    val direction: Int?,
    val gust: Double?
)

@Serializable
data class Endpoint(
    val method: String,
    val path: String,
    val description: String,
    val params: String?
)

@Serializable
data class EndpointsResponse(
    val status: String,
    val endpoints: List<Endpoint>
)

// MARK: - Custom Serializer for flexible JSON

object JsonElementSerializer : KSerializer<Any> {
    override val descriptor = PrimitiveSerialDescriptor("Any", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Any) {}
    override fun deserialize(decoder: Decoder): Any = Unit
}

// MARK: - Usage Example

/*
class MainActivity : AppCompatActivity() {
    private val apiClient = DBSccAPIClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lifecycleScope.launch {
            // Get station info
            apiClient.getStationInfo().onSuccess { response ->
                println("Station: ${response.data.station_name}")
            }.onFailure { error ->
                println("Error: ${error.message}")
            }

            // Get departures
            apiClient.getDepartures(limit = 5).onSuccess { response ->
                response.data.forEach { service ->
                    println("${service.origin} -> ${service.destination} at ${service.estimated_departure}")
                }
            }

            // Search for trains to Glasgow
            apiClient.searchDepartures("Glasgow", limit = 3).onSuccess { response ->
                println("Found ${response.count} trains to Glasgow")
            }

            // Get live bus tracking
            apiClient.getBusLive(limit = 10).onSuccess { response ->
                response.data.forEach { bus ->
                    println("Bus ${bus.vehicle_ref} on line ${bus.line_ref}")
                }
            }

            // Get weather
            apiClient.getWeather().onSuccess { response ->
                println("Temperature: ${response.data.temperature.current}°C")
            }
        }
    }
}
*/
