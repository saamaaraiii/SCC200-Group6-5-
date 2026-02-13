import SwiftUI

struct ContentView: View {
    @EnvironmentObject var appState: AppState
    @State private var originId: Int?
    @State private var destinationId: Int?
    @State private var useBFS = false
    @State private var lastResult: RouteResult?
    @State private var lastResultError: String?

    var body: some View {
        NavigationStack {
            List {
                if let error = appState.errorMessage {
                    Section {
                        Text(error)
                            .foregroundStyle(.red)
                    }
                }

                Section("From & To") {
                    Picker("From", selection: Binding(
                        get: { originId ?? 0 },
                        set: { originId = $0 == 0 ? nil : $0 }
                    )) {
                        Text("Select station").tag(0)
                        ForEach(appState.stations, id: \.id) { s in
                            Text("\(s.name) (\(s.code))").tag(s.id)
                        }
                    }
                    Picker("To", selection: Binding(
                        get: { destinationId ?? 0 },
                        set: { destinationId = $0 == 0 ? nil : $0 }
                    )) {
                        Text("Select station").tag(0)
                        ForEach(appState.stations, id: \.id) { s in
                            Text("\(s.name) (\(s.code))").tag(s.id)
                        }
                    }
                }

                Section("Algorithm") {
                    Picker("Pathfinding", selection: $useBFS) {
                        Text("Dijkstra (shortest time)").tag(false)
                        Text("BFS (fewest changes)").tag(true)
                    }
                    .pickerStyle(.menu)
                }

                Section {
                    Button("Find route") {
                        findRoute()
                    }
                    .disabled(originId == nil || destinationId == nil || originId == destinationId)
                }

                if let err = lastResultError {
                    Section("Result") {
                        Text(err).foregroundStyle(.red)
                    }
                }

                if let result = lastResult {
                    Section("Route") {
                        Text(appState.mapping.formatPath(stationIds: result.stationIds))
                            .font(.subheadline)
                        if let mins = result.totalDurationMins {
                            Text("Total time: \(mins) min")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Text("\(result.segmentCount) leg(s)")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .navigationTitle("Train App")
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private func findRoute() {
        lastResultError = nil
        lastResult = nil
        guard let o = originId, let d = destinationId, o != d else {
            lastResultError = "Select different from and to stations."
            return
        }
        if let route = appState.findRoute(originId: o, destinationId: d, useBFS: useBFS) {
            lastResult = route
        } else {
            lastResultError = "No route found."
        }
    }
}

#Preview {
    ContentView()
        .environmentObject(AppState())
}
