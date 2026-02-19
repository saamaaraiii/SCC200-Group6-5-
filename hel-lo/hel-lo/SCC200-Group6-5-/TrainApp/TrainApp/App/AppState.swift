import Foundation

/// Holds Phase 1 services: DB, parser, pathfinding, mapping. Initialize once at launch.
final class AppState: ObservableObject {
    let database: DatabaseManager
    let parser: DBParser
    let pathfinding: PathfindingService
    @Published private(set) var mapping: StationMapping
    @Published private(set) var stations: [Station] = []
    @Published var errorMessage: String?

    init() {
        database = DatabaseManager()
        parser = DBParser(databaseManager: database)
        pathfinding = PathfindingService(graph: RouteGraph())
        mapping = StationMapping(stations: [])
    }

    /// Call on app launch: init DB from schema/seed, load stations and graph.
    func initialize() {
        do {
            try database.initializeIfNeeded(schemaSQL: ResourceLoader.loadSchema(), seedSQL: ResourceLoader.loadSeed())
            let (stations, graph) = try parser.loadStationsAndGraph(weightByDuration: true)
            self.stations = stations
            self.mapping = StationMapping(stations: stations)
            pathfinding.replaceGraph(graph)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func findRoute(originId: Int, destinationId: Int, useBFS: Bool) -> RouteResult? {
        if useBFS {
            return pathfinding.findPathBFS(from: originId, to: destinationId)
        } else {
            return pathfinding.findPathDijkstra(from: originId, to: destinationId, useDuration: true)
        }
    }
}
