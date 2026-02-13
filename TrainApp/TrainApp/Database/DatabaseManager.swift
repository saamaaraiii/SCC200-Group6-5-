import Foundation
import SQLite3

/// Manages SQLite database: init from schema/seed and run queries.
final class DatabaseManager {
    private var db: OpaquePointer?
    private let dbPath: String

    init(dbPath: String? = nil) {
        if let path = dbPath {
            self.dbPath = path
        } else {
            let fileManager = FileManager.default
            let docs = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first!
            self.dbPath = docs.appendingPathComponent("trainapp.db").path
        }
    }

    deinit {
        close()
    }

    func open() throws {
        if db != nil { return }
        guard sqlite3_open(dbPath, &db) == SQLITE_OK else {
            throw DatabaseError.openFailed(String(cString: sqlite3_errmsg(db))))
        }
    }

    func close() {
        if let db = db {
            sqlite3_close(db)
            self.db = nil
        }
    }

    /// Create schema and load seed if the DB is new (no stations table or empty).
    func initializeIfNeeded(schemaSQL: String, seedSQL: String) throws {
        try open()
        let tableExists = try executeScalar("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='stations'") as? Int64
        if tableExists == 0 {
            try execute(schemaSQL)
            try execute(seedSQL)
        }
    }

    /// Run a multi-statement SQL string (e.g. schema or seed). Splits by ";" and runs each statement.
    func execute(_ sql: String) throws {
        let statements = sql.split(separator: ";").map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
        for stmt in statements {
            var statement: OpaquePointer?
            defer { sqlite3_finalize(statement) }
            guard sqlite3_prepare_v2(db, stmt, -1, &statement, nil) == SQLITE_OK else {
                throw DatabaseError.prepareFailed(String(cString: sqlite3_errmsg(db))))
            }
            guard sqlite3_step(statement) == SQLITE_DONE else {
                throw DatabaseError.prepareFailed(String(cString: sqlite3_errmsg(db))))
            }
        }
    }

    /// Execute SQL that returns a single value (e.g. SELECT count(*) ...).
    func executeScalar(_ sql: String) throws -> Any? {
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK else {
            throw DatabaseError.prepareFailed(String(cString: sqlite3_errmsg(db))))
        }
        defer { sqlite3_finalize(statement) }
        guard sqlite3_step(statement) == SQLITE_ROW else { return nil }
        let colType = sqlite3_column_type(statement, 0)
        switch colType {
        case SQLITE_INTEGER: return sqlite3_column_int64(statement, 0)
        case SQLITE_FLOAT: return sqlite3_column_double(statement, 0)
        case SQLITE_TEXT: return String(cString: sqlite3_column_text(statement, 0))
        default: return nil
        }
    }

    func fetchStations() throws -> [Station] {
        try open()
        let sql = "SELECT id, name, code, latitude, longitude FROM stations ORDER BY name"
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK else {
            throw DatabaseError.prepareFailed(String(cString: sqlite3_errmsg(db))))
        }
        defer { sqlite3_finalize(statement) }
        var list = [Station]()
        while sqlite3_step(statement) == SQLITE_ROW {
            let id = Int(sqlite3_column_int(statement, 0))
            let name = String(cString: sqlite3_column_text(statement, 1))
            let code = String(cString: sqlite3_column_text(statement, 2))
            let lat = sqlite3_column_type(statement, 3) == SQLITE_NULL ? nil : sqlite3_column_double(statement, 3)
            let lon = sqlite3_column_type(statement, 4) == SQLITE_NULL ? nil : sqlite3_column_double(statement, 4)
            list.append(Station(id: id, name: name, code: code, latitude: lat, longitude: lon))
        }
        return list
    }

    func fetchRouteSegments() throws -> [RouteSegment] {
        try open()
        let sql = "SELECT id, from_station_id, to_station_id, distance_km, duration_mins FROM route_segments"
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK else {
            throw DatabaseError.prepareFailed(String(cString: sqlite3_errmsg(db))))
        }
        defer { sqlite3_finalize(statement) }
        var list = [RouteSegment]()
        while sqlite3_step(statement) == SQLITE_ROW {
            let id = Int(sqlite3_column_int(statement, 0))
            let fromId = Int(sqlite3_column_int(statement, 1))
            let toId = Int(sqlite3_column_int(statement, 2))
            let dist = sqlite3_column_double(statement, 3)
            let dur = Int(sqlite3_column_int(statement, 4))
            list.append(RouteSegment(id: id, fromStationId: fromId, toStationId: toId, distanceKm: dist, durationMins: dur))
        }
        return list
    }
}

enum DatabaseError: LocalizedError {
    case openFailed(String)
    case prepareFailed(String)
    var errorDescription: String? {
        switch self {
        case .openFailed(let m): return "Database open failed: \(m)"
        case .prepareFailed(let m): return "Prepare failed: \(m)"
        }
    }
}
