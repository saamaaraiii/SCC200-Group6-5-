// iOS Swift Client Example
// Add to your iOS project to communicate with the DB_Scc API

import Foundation

class DBSccAPIClient {
    static let shared = DBSccAPIClient()
    
    let baseURL = "http://localhost:5000"
    let session = URLSession.shared
    
    private init() {}
    
    // MARK: - Generic Request Handler
    
    func makeRequest<T: Decodable>(
        endpoint: String,
        queryParams: [String: String]? = nil
    ) async throws -> T {
        var urlComponents = URLComponents(string: baseURL + endpoint)
        
        if let params = queryParams {
            urlComponents?.queryItems = params.map { URLQueryItem(name: $0.key, value: $0.value) }
        }
        
        guard let url = urlComponents?.url else {
            throw APIError.invalidURL
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        
        let (data, response) = try await session.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse, 200..<300 ~= httpResponse.statusCode else {
            throw APIError.serverError
        }
        
        let decoder = JSONDecoder()
        return try decoder.decode(T.self, from: data)
    }
    
    // MARK: - Lancaster Facility Endpoints
    
    func getStationInfo() async throws -> LancasterFacility {
        return try await makeRequest(endpoint: "/api/lancs_fac")
    }
    
    func getAccessibilityInfo() async throws -> AccessibilityInfo {
        return try await makeRequest(endpoint: "/api/lancs_fac/accessibility")
    }
    
    func getLiftInfo() async throws -> LiftInfo {
        return try await makeRequest(endpoint: "/api/lancs_fac/lifts")
    }
    
    func getTicketInfo() async throws -> TicketInfo {
        return try await makeRequest(endpoint: "/api/lancs_fac/ticket_buying")
    }
    
    func getTransportLinks() async throws -> TransportLinks {
        return try await makeRequest(endpoint: "/api/lancs_fac/transport_links")
    }
    
    func getCyclingInfo() async throws -> CyclingInfo {
        return try await makeRequest(endpoint: "/api/lancs_fac/cycling")
    }
    
    func getParkingInfo() async throws -> ParkingInfo {
        return try await makeRequest(endpoint: "/api/lancs_fac/parking")
    }
    
    // MARK: - Train Departure Endpoints
    
    func getDepartures(limit: Int? = nil) async throws -> DeparturesResponse {
        var params: [String: String]? = nil
        if let limit = limit {
            params = ["limit": String(limit)]
        }
        return try await makeRequest(endpoint: "/api/departures", queryParams: params)
    }
    
    func searchDepartures(destination: String, limit: Int? = nil) async throws -> DeparturesResponse {
        var params = ["destination": destination]
        if let limit = limit {
            params["limit"] = String(limit)
        }
        return try await makeRequest(endpoint: "/api/departures/search", queryParams: params)
    }
    
    // MARK: - Bus Live Tracking Endpoints
    
    func getBusLive(line: String? = nil, limit: Int? = nil) async throws -> BusResponse {
        var params: [String: String]? = nil
        if line != nil || limit != nil {
            params = [:]
            if let line = line {
                params?["line"] = line
            }
            if let limit = limit {
                params?["limit"] = String(limit)
            }
        }
        return try await makeRequest(endpoint: "/api/bus/live", queryParams: params)
    }
    
    func getBusLocation(vehicle: String) async throws -> BusLocationResponse {
        return try await makeRequest(endpoint: "/api/bus/live/location", queryParams: ["vehicle": vehicle])
    }
    
    // MARK: - Delay Codes Endpoints
    
    func getDelayCodes(code: String? = nil) async throws -> DelayCodesResponse {
        var params: [String: String]? = nil
        if let code = code {
            params = ["code": code]
        }
        return try await makeRequest(endpoint: "/api/delay_codes", queryParams: params)
    }
    
    func searchDelayCodes(query: String) async throws -> DelayCodesResponse {
        return try await makeRequest(endpoint: "/api/delay_codes/search", queryParams: ["q": query])
    }
    
    // MARK: - Weather Endpoint
    
    func getWeather() async throws -> WeatherResponse {
        return try await makeRequest(endpoint: "/api/weather")
    }
    
    // MARK: - Metadata Endpoints
    
    func getEndpoints() async throws -> EndpointsResponse {
        return try await makeRequest(endpoint: "/api/endpoints")
    }
}

// MARK: - Data Models

struct APIResponse<T: Decodable>: Decodable {
    let status: String
    let data: T?
    let message: String?
}

struct LancasterFacility: Decodable {
    let status: String
    let data: StationData
}

struct StationData: Decodable {
    let station_name: String?
    let crs_code: String?
    let nrcc_code: String?
    let location: Location?
    let address: Address?
    let operator: String?
    let staffing_level: String?
    let alerts: [Alert]?
}

struct Location: Decodable {
    let latitude: Double
    let longitude: Double
}

struct Address: Decodable {
    let addressLine1: String?
    let addressLine2: String?
    let addressLine3: String?
    let postcode: String?
}

struct Alert: Decodable {
    let title: String?
    let description: String?
}

struct AccessibilityInfo: Decodable {
    let status: String
    let data: AccessibilityData
}

struct AccessibilityData: Decodable {
    let step_free_category: String?
    let tactile_paving: String?
    let induction_loops: String?
    let wheelchairs_available: Bool?
    let passenger_assistance: [String]?
    let train_ramp: Bool?
    let ticket_barriers: Bool?
}

struct LiftInfo: Decodable {
    let status: String
    let data: LiftData
}

struct LiftData: Decodable {
    let available: Bool?
    let statement: String?
    let lifts_info: [[String: AnyCodable]]?
}

struct TicketInfo: Decodable {
    let status: String
    let data: TicketData
}

struct TicketData: Decodable {
    let ticket_office: [String: AnyCodable]?
    let machines_available: Bool?
    let collect_online: [String: AnyCodable]?
    let pay_as_you_go: [String: AnyCodable]?
}

struct TransportLinks: Decodable {
    let status: String
    let data: TransportData
}

struct TransportData: Decodable {
    let bus: Bool?
    let replacement_bus: Bool?
    let taxi: Bool?
    let taxi_ranks: [String]?
    let airport: Bool?
    let underground: Bool?
    let car_hire: Bool?
    let port: Bool?
}

struct CyclingInfo: Decodable {
    let status: String
    let data: CyclingData
}

struct CyclingData: Decodable {
    let storage_available: Bool?
    let spaces: Int?
    let storage_types: [String]?
    let sheltered: Bool?
    let cctv: Bool?
    let location: String?
}

struct ParkingInfo: Decodable {
    let status: String
    let data: ParkingData
}

struct ParkingData: Decodable {
    let car_parks: [String: AnyCodable]?
}

struct DeparturesResponse: Decodable {
    let status: String
    let station: String?
    let crs: String?
    let timestamp: String?
    let count: Int
    let data: [TrainService]
}

struct TrainService: Decodable {
    let scheduled_departure: String?
    let estimated_departure: String?
    let platform: String?
    let operator: String?
    let origin: String?
    let destination: String?
    let delay_reason: String?
    let calling_points: [CallingPoint]?
}

struct CallingPoint: Decodable {
    let location: String?
    let scheduled_time: String?
    let estimated_time: String?
}

struct BusResponse: Decodable {
    let status: String
    let timestamp: String?
    let count: Int
    let data: [BusVehicle]
}

struct BusVehicle: Decodable {
    let line_ref: String?
    let vehicle_ref: String?
    let direction: String?
    let origin_name: String?
    let destination_name: String?
    let origin_time: String?
    let destination_time: String?
    let location: BusLocation?
    let bearing: String?
    let recorded_at: String?
}

struct BusLocation: Decodable {
    let latitude: Double
    let longitude: Double
}

struct BusLocationResponse: Decodable {
    let status: String
    let data: BusVehicle?
}

struct DelayCode: Decodable {
    let Code: String
    let Cause: String
    let Abbreviation: String
}

struct DelayCodesResponse: Decodable {
    let status: String
    let count: Int
    let data: [DelayCode]?
    let query: String?
    let message: String?
}

struct WeatherResponse: Decodable {
    let status: String
    let data: WeatherData
}

struct WeatherData: Decodable {
    let location: WeatherLocation
    let conditions: WeatherCondition
    let temperature: TemperatureData
    let wind: WindData
    let humidity: Int?
    let visibility: Int?
    let clouds: Int?
    let timestamp: Int?
}

struct WeatherLocation: Decodable {
    let name: String?
    let latitude: Double?
    let longitude: Double?
}

struct WeatherCondition: Decodable {
    let main: String?
    let description: String?
    let icon: String?
}

struct TemperatureData: Decodable {
    let current: Double?
    let feels_like: Double?
    let min: Double?
    let max: Double?
}

struct WindData: Decodable {
    let speed: Double?
    let direction: Int?
    let gust: Double?
}

struct Endpoint: Decodable {
    let method: String
    let path: String
    let description: String
    let params: String?
}

struct EndpointsResponse: Decodable {
    let status: String
    let endpoints: [Endpoint]
}

// MARK: - Helper Types

enum APIError: LocalizedError {
    case invalidURL
    case serverError
    case decodingError(Error)
    case networkError(Error)
    
    var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "Invalid URL"
        case .serverError:
            return "Server error"
        case .decodingError(let error):
            return "Decoding error: \(error.localizedDescription)"
        case .networkError(let error):
            return "Network error: \(error.localizedDescription)"
        }
    }
}

// AnyCodable for flexible JSON parsing
enum AnyCodable: Codable {
    case null
    case bool(Bool)
    case int(Int)
    case double(Double)
    case string(String)
    case array([AnyCodable])
    case object([String: AnyCodable])
    
    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if container.decodeNil() {
            self = .null
        } else if let bool = try? container.decode(Bool.self) {
            self = .bool(bool)
        } else if let int = try? container.decode(Int.self) {
            self = .int(int)
        } else if let double = try? container.decode(Double.self) {
            self = .double(double)
        } else if let string = try? container.decode(String.self) {
            self = .string(string)
        } else if let array = try? container.decode([AnyCodable].self) {
            self = .array(array)
        } else if let object = try? container.decode([String: AnyCodable].self) {
            self = .object(object)
        } else {
            throw DecodingError.dataCorruptedError(in: container, debugDescription: "Cannot decode AnyCodable")
        }
    }
    
    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self {
        case .null:
            try container.encodeNil()
        case .bool(let bool):
            try container.encode(bool)
        case .int(let int):
            try container.encode(int)
        case .double(let double):
            try container.encode(double)
        case .string(let string):
            try container.encode(string)
        case .array(let array):
            try container.encode(array)
        case .object(let object):
            try container.encode(object)
        }
    }
}

// MARK: - Usage Example

/*
Task {
    do {
        // Get station info
        let station = try await DBSccAPIClient.shared.getStationInfo()
        print("Station: \(station.data.station_name ?? "Unknown")")
        
        // Get departures
        let departures = try await DBSccAPIClient.shared.getDepartures(limit: 5)
        print("Next 5 departures: \(departures.count) services")
        
        // Search departures
        let glasgowTrains = try await DBSccAPIClient.shared.searchDepartures(destination: "Glasgow", limit: 3)
        print("Trains to Glasgow: \(glasgowTrains.count)")
        
        // Get live bus tracking
        let buses = try await DBSccAPIClient.shared.getBusLive(limit: 10)
        print("Active buses: \(buses.count)")
        
        // Get weather
        let weather = try await DBSccAPIClient.shared.getWeather()
        print("Temp: \(weather.data.temperature.current ?? 0)°C")
        
    } catch {
        print("Error: \(error.localizedDescription)")
    }
}
*/
