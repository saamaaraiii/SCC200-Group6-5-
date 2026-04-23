import Foundation

struct StopsResponse: Decodable {
    let status: String
    let count: Int
    let data: [APIBusStop]
}

struct APIBusStop: Decodable {
    let atcoCode: String?
    let naptanCode: String?
    let commonName: String?
    let street: String?
    let town: String?
    let latitude: Double?
    let longitude: Double?

    enum CodingKeys: String, CodingKey {
        case atcoCode = "atco_code"
        case naptanCode = "naptan_code"
        case commonName = "common_name"
        case street
        case town
        case latitude
        case longitude
    }
}

enum NetworkServiceError: Error {
    case badURL
    case badStatus(Int)
}

final class NetworkService {
    // Configure this key in UserDefaults for real-device runs:
    // apiBaseURL = http://<your-mac-lan-ip>:5000
    private let baseURL: URL
    private let session: URLSession

    init(baseURL: URL? = nil, session: URLSession = .shared) {
        if let baseURL {
            self.baseURL = baseURL
        } else if
            let configured = UserDefaults.standard.string(forKey: "apiBaseURL"),
            let url = URL(string: configured) {
            self.baseURL = url
        } else {
            self.baseURL = URL(string: "http://localhost:5000")!
        }
        self.session = session
    }

    func fetchStops(limit: Int = 2000) async throws -> [Station] {
        guard var components = URLComponents(url: baseURL.appendingPathComponent("api/bus/stops"), resolvingAgainstBaseURL: false) else {
            throw NetworkServiceError.badURL
        }

        components.queryItems = [
            URLQueryItem(name: "limit", value: String(limit))
        ]

        guard let url = components.url else {
            throw NetworkServiceError.badURL
        }

        let (data, response) = try await session.data(from: url)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw NetworkServiceError.badStatus(-1)
        }
        guard (200...299).contains(httpResponse.statusCode) else {
            throw NetworkServiceError.badStatus(httpResponse.statusCode)
        }

        let decoder = JSONDecoder()
        let payload = try decoder.decode(StopsResponse.self, from: data)

        return payload.data.compactMap { stop in
            guard let lat = stop.latitude, let lon = stop.longitude else { return nil }

            let name = (stop.commonName?.trimmingCharacters(in: .whitespacesAndNewlines)).flatMap { $0.isEmpty ? nil : $0 }
                ?? "Bus Stop"
            let code = stop.atcoCode ?? stop.naptanCode ?? "STOP"

            let idSource = stop.atcoCode ?? stop.naptanCode ?? "\(name)-\(lat)-\(lon)"
            let id = Self.deterministicID(from: idSource)

            return Station(id: id, name: name, code: code, latitude: lat, longitude: lon)
        }
    }

    private static func deterministicID(from source: String) -> Int {
        var hash: UInt64 = 5381
        for scalar in source.unicodeScalars {
            hash = ((hash << 5) &+ hash) &+ UInt64(scalar.value)
        }
        return Int(hash % UInt64(Int.max - 1)) + 1
    }
}
