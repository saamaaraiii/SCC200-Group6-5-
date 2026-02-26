import SwiftUI

struct ContentView: View {
    @EnvironmentObject var appState: AppState
    @AppStorage("preferredAppearance") private var preferredAppearance = "dark"
    @State private var selectedTab: AppTab = .home
    @State private var originId: Int?
    @State private var destinationId: Int?
    @State private var useBFS = false
    @State private var lastResult: RouteResult?
    @State private var lastResultError: String?
    @State private var searchText = ""
    @State private var savedTrips: [SavedTrip] = []
    @State private var notifications: [AccountNotification] = []
    @State private var fullName = "Alex Traveller"
    @State private var email = "alex.traveller@example.com"
    @State private var phone = "+44 7700 900000"

    var filteredStations: [Station] {
        guard !searchText.isEmpty else { return appState.stations }
        return appState.stations.filter {
            $0.name.localizedCaseInsensitiveContains(searchText) ||
            $0.code.localizedCaseInsensitiveContains(searchText)
        }
    }

    private var preferredColorScheme: ColorScheme? {
        switch preferredAppearance {
        case "light": return .light
        case "dark": return .dark
        default: return nil
        }
    }

    var body: some View {
        TabView(selection: $selectedTab) {
            homeScreen
                .tabItem {
                    Label("Home", systemImage: "house.fill")
                }
                .tag(AppTab.home)

            ticketsScreen
                .tabItem {
                    Label("Tickets", systemImage: "ticket.fill")
                }
                .tag(AppTab.tickets)

            accountScreen
                .tabItem {
                    Label("Account", systemImage: "person.fill")
                }
                .tag(AppTab.account)
        }
        .tint(.blue)
        .toolbarBackground(.visible, for: .tabBar)
        .toolbarBackground(Color(red: 0.02, green: 0.07, blue: 0.14), for: .tabBar)
        .preferredColorScheme(preferredColorScheme)
    }

    private var homeScreen: some View {
        NavigationStack {
            ZStack {
                screenGradient
                ScrollView {
                    VStack(spacing: 14) {
                        headerCard
                        operatorsCard
                        searchCard
                        stationPickerCard
                        algorithmCard
                        actionCard
                        if let result = lastResult {
                            routeCard(result: result)
                        }
                        if let err = lastResultError {
                            messageCard(text: err, color: .red)
                        }
                        if let appError = appState.errorMessage {
                            messageCard(text: appError, color: .red)
                        }
                    }
                    .padding(.horizontal, 14)
                    .padding(.bottom, 24)
                }
                .scrollIndicators(.hidden)
            }
            .navigationTitle("Train Stage")
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private var headerCard: some View {
        HStack {
            VStack(alignment: .leading, spacing: 6) {
                Text("Current Location")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Text("Lancashire Network")
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(.white)
            }
            Spacer()
            Image(systemName: "tram.fill")
                .font(.title2)
                .foregroundStyle(.blue)
        }
        .padding()
        .background(cardBackground)
    }

    private var operatorsCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Local Operators (NOC)")
                .font(.headline)
                .foregroundStyle(.white)

            Text("ARCT, BLAC, KLCO, SCCU, SCMY, NUTT")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(cardBackground)
    }

    private var searchCard: some View {
        VStack(spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .foregroundStyle(.blue)
                TextField("Search stop or code...", text: $searchText)
                    .foregroundStyle(.white)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled(true)
            }
            .padding(12)
            .background(innerBackground)

            if !searchText.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(filteredStations.prefix(6), id: \.id) { station in
                        HStack {
                            Text(station.name)
                                .foregroundStyle(.white)
                            Spacer()
                            Text(station.code)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    if filteredStations.isEmpty {
                        Text("No matching stops")
                            .foregroundStyle(.secondary)
                    }
                }
                .padding(12)
                .background(innerBackground)
            }
        }
        .padding()
        .background(cardBackground)
    }

    private var stationPickerCard: some View {
        VStack(spacing: 12) {
            Text("Route")
                .font(.headline)
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity, alignment: .leading)

            Picker("From", selection: Binding(
                get: { originId ?? 0 },
                set: { originId = $0 == 0 ? nil : $0 }
            )) {
                Text("From station").tag(0)
                ForEach(appState.stations, id: \.id) { station in
                    Text("\(station.name) (\(station.code))").tag(station.id)
                }
            }
            .pickerStyle(.menu)
            .tint(.white)

            Picker("To", selection: Binding(
                get: { destinationId ?? 0 },
                set: { destinationId = $0 == 0 ? nil : $0 }
            )) {
                Text("To station").tag(0)
                ForEach(appState.stations, id: \.id) { station in
                    Text("\(station.name) (\(station.code))").tag(station.id)
                }
            }
            .pickerStyle(.menu)
            .tint(.white)
        }
        .padding()
        .background(cardBackground)
    }

    private var algorithmCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Routing Mode")
                .font(.headline)
                .foregroundStyle(.white)

            Picker("Pathfinding", selection: $useBFS) {
                Text("Fastest (Dijkstra)").tag(false)
                Text("Fewest Stops (BFS)").tag(true)
            }
            .pickerStyle(.segmented)
        }
        .padding()
        .background(cardBackground)
    }

    private var actionCard: some View {
        Button(action: findRoute) {
            Text("Find Route")
                .font(.headline)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
        }
        .buttonStyle(.borderedProminent)
        .disabled(originId == nil || destinationId == nil || originId == destinationId)
        .padding()
        .background(cardBackground)
    }

    private func routeCard(result: RouteResult) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Suggested Journey")
                .font(.headline)
                .foregroundStyle(.white)

            ForEach(Array(result.stationIds.enumerated()), id: \.offset) { index, stationId in
                HStack(alignment: .top, spacing: 10) {
                    Circle()
                        .fill(index == 0 ? Color.green : (index == result.stationIds.count - 1 ? Color.orange : Color.blue))
                        .frame(width: 10, height: 10)
                        .padding(.top, 5)

                    VStack(alignment: .leading, spacing: 2) {
                        Text(appState.mapping.name(for: stationId))
                            .foregroundStyle(.white)
                        Text(appState.mapping.code(for: stationId))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }

            Divider().overlay(.white.opacity(0.1))

            HStack {
                Text("Segments: \(result.segmentCount)")
                Spacer()
                if let mins = result.totalDurationMins {
                    Text("ETA: \(mins) min")
                }
            }
            .font(.subheadline)
            .foregroundStyle(.secondary)
        }
        .padding()
        .background(cardBackground)
    }

    private func messageCard(text: String, color: Color) -> some View {
        Text(text)
            .foregroundStyle(color)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding()
            .background(cardBackground)
    }

    private var ticketsScreen: some View {
        NavigationStack {
            ZStack {
                screenGradient
                VStack(spacing: 0) {
                    ScrollView {
                        VStack(spacing: 14) {
                            VStack(alignment: .leading, spacing: 10) {
                                Text("Your Ticket")
                                    .font(.title3.weight(.semibold))
                                    .foregroundStyle(.white)
                                RoundedRectangle(cornerRadius: 20)
                                    .fill(Color.white.opacity(0.05))
                                    .frame(height: 190)
                                    .overlay {
                                        VStack(spacing: 10) {
                                            Image(systemName: "qrcode")
                                                .font(.system(size: 50))
                                                .foregroundStyle(.white)
                                            Text("Preston → Lancaster")
                                                .foregroundStyle(.white)
                                            Text("Ready to scan")
                                                .font(.caption)
                                                .foregroundStyle(.secondary)
                                        }
                                    }
                            }
                            .padding()
                            .background(cardBackground)

                            Spacer(minLength: 120)
                        }
                        .padding(.horizontal, 14)
                        .padding(.top, 12)
                    }

                    VStack(spacing: 10) {
                        Button {
                        } label: {
                            Text("Get Directions")
                                .font(.headline.weight(.semibold))
                                .foregroundStyle(.white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 14)
                                .background(
                                    RoundedRectangle(cornerRadius: 14)
                                        .fill(Color.blue)
                                )
                        }

                        Button {
                        } label: {
                            HStack(spacing: 8) {
                                Image(systemName: "wallet.pass.fill")
                                    .font(.headline)
                                Text("Add to Apple Wallet")
                                    .font(.headline.weight(.semibold))
                            }
                            .foregroundStyle(.black)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(
                                RoundedRectangle(cornerRadius: 14)
                                    .fill(Color.white)
                            )
                        }
                    }
                    .padding(.horizontal, 14)
                    .padding(.top, 10)
                    .padding(.bottom, 12)
                    .background(
                        LinearGradient(
                            colors: [Color.black.opacity(0.0), Color.black.opacity(0.45)],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                        .ignoresSafeArea(edges: .bottom)
                    )
                }
            }
            .navigationTitle("Tickets")
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private var accountScreen: some View {
        NavigationStack {
            ZStack {
                screenGradient
                VStack(alignment: .leading, spacing: 14) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Student Account")
                            .font(.title3.weight(.semibold))
                            .foregroundStyle(.white)
                        Text("UPSA Exchange - Lancaster University")
                            .foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding()
                    .background(cardBackground)

                    VStack(alignment: .leading, spacing: 12) {
                        Text("Quick Actions")
                            .font(.headline)
                            .foregroundStyle(.white)

                        NavigationLink {
                            SavedTripsScreen(trips: savedTrips)
                        } label: {
                            accountActionRow(title: "Saved Trips", icon: "bookmark.fill")
                        }

                        NavigationLink {
                            NotificationsScreen(notifications: notifications)
                        } label: {
                            accountActionRow(title: "Notifications", icon: "bell.fill")
                        }

                        NavigationLink {
                            SettingsScreen(
                                preferredAppearance: $preferredAppearance,
                                fullName: $fullName,
                                email: $email,
                                phone: $phone
                            )
                        } label: {
                            accountActionRow(title: "Settings", icon: "gearshape.fill")
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding()
                    .background(cardBackground)

                    Spacer()
                }
                .padding(.horizontal, 14)
                .padding(.top, 12)
            }
            .navigationTitle("Account")
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private func accountActionRow(title: String, icon: String) -> some View {
        HStack(spacing: 14) {
            Image(systemName: icon)
                .font(.title3)
                .foregroundStyle(.blue)
                .frame(width: 26)
            Text(title)
                .font(.title3.weight(.semibold))
                .foregroundStyle(.white)
            Spacer()
            Image(systemName: "chevron.right")
                .font(.headline)
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 16)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 14)
                .fill(Color.white.opacity(0.06))
        )
    }

    private var cardBackground: some View {
        RoundedRectangle(cornerRadius: 18)
            .fill(Color.white.opacity(0.08))
            .overlay(
                RoundedRectangle(cornerRadius: 18)
                    .stroke(Color.white.opacity(0.06), lineWidth: 1)
            )
    }

    private var innerBackground: some View {
        RoundedRectangle(cornerRadius: 12)
            .fill(Color.white.opacity(0.05))
    }

    private var screenGradient: some View {
        LinearGradient(
            colors: [Color(red: 0.03, green: 0.11, blue: 0.28), Color.black],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
        .ignoresSafeArea()
    }

    private func findRoute() {
        lastResultError = nil
        lastResult = nil

        guard !appState.stations.isEmpty else {
            lastResultError = "Still loading stations. Try again in a second."
            return
        }

        guard let origin = originId, let destination = destinationId, origin != destination else {
            lastResultError = "Select different origin and destination stations."
            return
        }

        guard let route = appState.findRoute(originId: origin, destinationId: destination, useBFS: useBFS) else {
            lastResultError = "No route found with current data."
            return
        }

        lastResult = route
    }
}

private enum AppTab: Hashable {
    case home
    case tickets
    case account
}

private struct SavedTrip: Identifiable {
    let id = UUID()
    let title: String
    let subtitle: String
}

private struct AccountNotification: Identifiable {
    let id = UUID()
    let title: String
    let subtitle: String
}

private struct SavedTripsScreen: View {
    let trips: [SavedTrip]

    var body: some View {
        List {
            if trips.isEmpty {
                Text("No trips to show")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(trips) { trip in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(trip.title).font(.headline)
                        Text(trip.subtitle)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 6)
                }
            }
        }
        .navigationTitle("Saved Trips")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct NotificationsScreen: View {
    let notifications: [AccountNotification]

    var body: some View {
        List {
            if notifications.isEmpty {
                Text("No notifications to show")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(notifications) { notification in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(notification.title).font(.headline)
                        Text(notification.subtitle)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 6)
                }
            }
        }
        .navigationTitle("Notifications")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct SettingsScreen: View {
    @Binding var preferredAppearance: String
    @Binding var fullName: String
    @Binding var email: String
    @Binding var phone: String

    var body: some View {
        Form {
            Section("Personal Data") {
                TextField("Full Name", text: $fullName)
                TextField("Email", text: $email)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled(true)
                TextField("Phone", text: $phone)
            }

            Section("Appearance") {
                Picker("Theme", selection: $preferredAppearance) {
                    Text("Dark").tag("dark")
                    Text("Light").tag("light")
                    Text("System").tag("system")
                }
                .pickerStyle(.segmented)
            }
        }
        .navigationTitle("Settings")
        .navigationBarTitleDisplayMode(.inline)
    }
}

#Preview {
    ContentView()
        .environmentObject(AppState())
}
