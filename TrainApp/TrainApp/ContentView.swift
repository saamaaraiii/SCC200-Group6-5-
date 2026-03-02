import SwiftUI
import MapKit
import CoreLocation
import PassKit
import UniformTypeIdentifiers

struct ContentView: View {
    @EnvironmentObject var appState: AppState
    @AppStorage("preferredAppearance") private var preferredAppearance = "dark"
    @StateObject private var locationManager = UserLocationManager()
    @State private var selectedTab: AppTab = .home
    @State private var originId: Int?
    @State private var destinationId: Int?
    @State private var transportMode: TransportMode = .train
    @State private var useBFS = false
    @State private var lastResult: RouteResult?
    @State private var lastResultError: String?
    @State private var searchText = ""
    @State private var taxiPulse = false
    @State private var plannedRoute: PlannedRoute?
    @State private var showPlannedRoute = false
    @State private var savedTrips: [SavedTrip] = []
    @State private var savedStops: [SavedStop] = []
    @State private var notifications: [AccountNotification] = []
    @State private var fullName = "Alex Traveller"
    @State private var email = "alex.traveller@example.com"
    @State private var phone = "+44 7700 900000"
    @State private var showWelcomeSplash = true
    @State private var splashScale: CGFloat = 0.92
    @State private var splashOpacity: Double = 0.0
    @State private var showPassImporter = false
    @State private var walletPresentation: WalletPassPresentation?
    @State private var walletMessage: String?
    @State private var departuresMessage: String?
    @State private var mapPosition: MapCameraPosition = .region(
        MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: 53.90, longitude: -2.95),
            span: MKCoordinateSpan(latitudeDelta: 0.85, longitudeDelta: 1.0)
        )
    )

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
        ZStack {
        TabView(selection: $selectedTab) {
            homeScreen
                .tabItem {
                    Label("Home", systemImage: "house.fill")
                }
                .tag(AppTab.home)

            mapScreen
                .tabItem {
                    Label("Map", systemImage: "map.fill")
                }
                .tag(AppTab.map)

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

            if showWelcomeSplash {
                splashOverlay
                    .transition(.opacity)
                    .zIndex(10)
            }
        }
        .tint(.blue)
        .toolbarBackground(.visible, for: .tabBar)
        .toolbarBackground(Color(red: 0.02, green: 0.07, blue: 0.14), for: .tabBar)
        .preferredColorScheme(preferredColorScheme)
        .onAppear {
            locationManager.requestPermissionIfNeeded()
            startSplashAnimation()
        }
        .fileImporter(
            isPresented: $showPassImporter,
            allowedContentTypes: [UTType(filenameExtension: "pkpass") ?? .data]
        ) { result in
            handlePassImport(result: result)
        }
        .sheet(item: $walletPresentation) { presentation in
            AddPassesSheet(passes: presentation.passes)
        }
        .alert("Apple Wallet", isPresented: Binding(
            get: { walletMessage != nil },
            set: { if !$0 { walletMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(walletMessage ?? "")
        }
        .alert("Departures", isPresented: Binding(
            get: { departuresMessage != nil },
            set: { if !$0 { departuresMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(departuresMessage ?? "")
        }
    }

    private var splashOverlay: some View {
        ZStack {
            LinearGradient(
                colors: [Color(red: 0.03, green: 0.11, blue: 0.28), Color.black],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            VStack(spacing: 14) {
                Image(systemName: "tram.fill")
                    .font(.system(size: 44, weight: .semibold))
                    .foregroundStyle(.blue)
                Text("Welcome to the Transport App")
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(.white)
            }
            .scaleEffect(splashScale)
            .opacity(splashOpacity)
        }
    }

    private func startSplashAnimation() {
        guard showWelcomeSplash else { return }

        splashScale = 0.92
        splashOpacity = 0.0

        withAnimation(.easeOut(duration: 0.45)) {
            splashScale = 1.0
            splashOpacity = 1.0
        }

        DispatchQueue.main.asyncAfter(deadline: .now() + 1.4) {
            withAnimation(.easeInOut(duration: 0.35)) {
                splashOpacity = 0.0
            }
        }

        DispatchQueue.main.asyncAfter(deadline: .now() + 1.8) {
            withAnimation(.easeOut(duration: 0.2)) {
                showWelcomeSplash = false
            }
        }
    }

    private var homeScreen: some View {
        NavigationStack {
            ZStack {
                screenGradient
                ScrollView {
                    VStack(spacing: 14) {
                        welcomeCard
                        operatorsCard
                        searchAllTrainsCard
                        transportModeCard
                        if transportMode == .taxi {
                            taxiCard
                        } else if transportMode == .train || transportMode == .bus {
                            nearbyStopMapCard
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
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(isPresented: $showPlannedRoute) {
                if let plan = plannedRoute {
                    PlannedRouteScreen(plan: plan, mapping: appState.mapping)
                } else {
                    EmptyView()
                }
            }
        }
    }

    private var welcomeCard: some View {
        HStack {
            Circle()
                .fill(Color.white.opacity(0.12))
                .frame(width: 40, height: 40)
                .overlay {
                    Image(systemName: "person.fill")
                        .foregroundStyle(.white)
                }

            VStack(alignment: .leading, spacing: 2) {
                Text("Welcome back")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Text(fullName)
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(.white)
            }

            Spacer()
            NavigationLink {
                SettingsScreen(
                    preferredAppearance: $preferredAppearance,
                    fullName: $fullName,
                    email: $email,
                    phone: $phone
                )
            } label: {
                Image(systemName: "gearshape.fill")
                    .font(.title2)
                    .foregroundStyle(.secondary)
                    .frame(width: 36, height: 36)
                    .background(
                        Circle().fill(Color.white.opacity(0.06))
                    )
            }
            .buttonStyle(.plain)
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

    private var searchAllTrainsCard: some View {
        NavigationLink {
            SearchTrainsScreen(stations: appState.stations)
        } label: {
            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass")
                    .font(.headline)
                    .foregroundStyle(.blue)
                    .frame(width: 24)

                Text("Search all trains")
                    .font(.headline)
                    .foregroundStyle(.white.opacity(0.9))

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.secondary)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 16)
            .background(
                RoundedRectangle(cornerRadius: 14)
                    .fill(Color.white.opacity(0.06))
            )
        }
        .buttonStyle(.plain)
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

    private var transportModeCard: some View {
        HStack(spacing: 10) {
            transportModeButton(mode: .train)
            transportModeButton(mode: .bus)
            transportModeButton(mode: .taxi)
            transportModeButton(mode: .walk)
        }
        .padding()
        .background(cardBackground)
    }

    private func transportModeButton(mode: TransportMode) -> some View {
        let isSelected = transportMode == mode
        return Button {
            transportMode = mode
        } label: {
            VStack(spacing: 10) {
                Image(systemName: mode.icon)
                    .font(.title3)
                Text(mode.title)
                    .font(.subheadline.weight(.semibold))
            }
            .foregroundStyle(isSelected ? .white : .secondary)
            .frame(maxWidth: .infinity)
            .frame(height: 86)
            .background(
                RoundedRectangle(cornerRadius: 14)
                    .fill(isSelected ? Color.blue.opacity(0.95) : Color.white.opacity(0.06))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(isSelected ? Color.blue : Color.white.opacity(0.08), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }

    private var algorithmCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Routing Mode")
                .font(.headline)
                .foregroundStyle(.white)

            Picker("Pathfinding", selection: $useBFS) {
                Text("Fastest").tag(false)
                Text("Fewest Stops").tag(true)
            }
            .pickerStyle(.segmented)
        }
        .padding()
        .background(cardBackground)
    }

    private var actionCard: some View {
        Button(action: openPlannedRoute) {
            Text("Find \(transportMode.title) Route")
                .font(.headline)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
        }
        .buttonStyle(.borderedProminent)
        .disabled(originId == nil || destinationId == nil || originId == destinationId)
        .padding()
        .background(cardBackground)
    }

    private func openPlannedRoute() {
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
        plannedRoute = PlannedRoute(
            mode: transportMode,
            stationIds: route.stationIds,
            durationMins: route.totalDurationMins ?? transportMode.defaultDurationMins
        )
        showPlannedRoute = true
    }

    private var taxiCard: some View {
        VStack(spacing: 14) {
            ZStack {
                RoundedRectangle(cornerRadius: 16)
                    .fill(Color.white.opacity(0.06))
                    .frame(height: 190)
                    .overlay(
                        gridOverlay
                    )

                Circle()
                    .fill(Color.blue.opacity(0.35))
                    .frame(width: 72, height: 72)
                    .scaleEffect(taxiPulse ? 1.1 : 0.9)
                    .opacity(taxiPulse ? 0.7 : 0.35)
                    .animation(.easeInOut(duration: 1.2).repeatForever(autoreverses: true), value: taxiPulse)

                Circle()
                    .fill(Color.blue)
                    .frame(width: 18, height: 18)
                    .overlay(
                        Circle().stroke(Color.white.opacity(0.8), lineWidth: 1)
                    )

                Circle()
                    .fill(Color.yellow.opacity(0.9))
                    .frame(width: 30, height: 30)
                    .overlay {
                        Image(systemName: "car.fill")
                            .font(.caption.weight(.bold))
                            .foregroundStyle(.black.opacity(0.8))
                    }
                    .offset(x: 88, y: -44)
            }
            .onAppear { taxiPulse = true }

            Button {
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: "person.fill")
                    Text("Request Taxi")
                        .font(.headline.weight(.semibold))
                }
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Color.blue)
                )
            }
            .buttonStyle(.plain)
        }
        .padding()
        .background(cardBackground)
    }

    private var nearbyStopMapCard: some View {
        VStack(spacing: 12) {
            ZStack(alignment: .bottom) {
                RoundedRectangle(cornerRadius: 16)
                    .fill(Color.white.opacity(0.06))
                    .frame(height: 220)
                    .overlay {
                        if let stop = nearestStopInfo {
                            Map(position: .constant(nearestStopCamera(for: stop.station))) {
                                if let lat = stop.station.latitude, let lon = stop.station.longitude {
                                    Marker(stop.station.name, coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lon))
                                        .tint(.blue)
                                }
                                UserAnnotation()
                            }
                            .mapStyle(.standard(elevation: .realistic))
                            .clipShape(RoundedRectangle(cornerRadius: 16))
                        } else {
                            ProgressView("Locating nearest stop...")
                                .tint(.white)
                        }
                    }

                if let stop = nearestStopInfo {
                    nearbyStopInfoBox(stop)
                        .padding(.horizontal, 12)
                        .padding(.bottom, 10)
                }
            }
        }
        .padding()
        .background(cardBackground)
    }

    private func nearbyStopInfoBox(_ stop: NearbyStopInfo) -> some View {
        let saved = isStopSaved(stop.station.id)
        return VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .center) {
                HStack(spacing: 7) {
                    Text(transportMode == .train ? "STATION" : "STOP")
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 7)
                        .padding(.vertical, 4)
                        .background(
                            RoundedRectangle(cornerRadius: 6)
                                .fill(transportMode == .train ? .blue : .green)
                        )
                    Text(stop.station.name)
                        .font(.headline.weight(.semibold))
                        .foregroundStyle(.white)
                }

                Spacer()

                Button {
                    toggleSavedStop(stop)
                } label: {
                    Image(systemName: saved ? "star.fill" : "star")
                        .font(.headline)
                        .foregroundStyle(saved ? .yellow : .white)
                }
                .buttonStyle(.plain)
            }

            Text("\(distanceText(for: stop.distanceMeters)) away")
                .font(.subheadline)
                .foregroundStyle(.secondary)

            HStack(spacing: 8) {
                Button {
                    openDirections(to: stop.station)
                } label: {
                    Label("Directions", systemImage: "arrow.triangle.turn.up.right.diamond.fill")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(
                            RoundedRectangle(cornerRadius: 10)
                                .fill(Color.blue)
                        )
                }
                .buttonStyle(.plain)

                Button {
                    showDepartures(for: stop.station)
                } label: {
                    Label("Departures", systemImage: "clock.fill")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(
                            RoundedRectangle(cornerRadius: 10)
                                .fill(Color.white.opacity(0.12))
                        )
                }
                .buttonStyle(.plain)
            }
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 14)
                .fill(Color.black.opacity(0.45))
                .overlay(
                    RoundedRectangle(cornerRadius: 14)
                        .stroke(Color.white.opacity(0.08), lineWidth: 1)
                )
        )
    }

    private var gridOverlay: some View {
        GeometryReader { geo in
            ZStack {
                Path { path in
                    path.move(to: CGPoint(x: 0, y: geo.size.height * 0.45))
                    path.addLine(to: CGPoint(x: geo.size.width, y: geo.size.height * 0.45))
                    path.move(to: CGPoint(x: geo.size.width * 0.5, y: 0))
                    path.addLine(to: CGPoint(x: geo.size.width * 0.5, y: geo.size.height))
                }
                .stroke(Color.white.opacity(0.08), lineWidth: 14)

                RoundedRectangle(cornerRadius: 10)
                    .fill(Color.white.opacity(0.04))
                    .frame(width: 90, height: 50)
                    .offset(x: -100, y: 46)
            }
        }
        .clipped()
    }

    private var routeStageCard: some View {
        let stationIds = displayStationIds

        return VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(transportMode.stageTitle)
                    .font(.headline)
                    .foregroundStyle(.white)
                Spacer()
                Image(systemName: "chevron.up")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
            }

            if transportMode == .train {
                HStack(spacing: 8) {
                    Text("AVANTI")
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 7)
                        .padding(.vertical, 4)
                        .background(Color.red, in: RoundedRectangle(cornerRadius: 4))
                    Text("Avanti West Coast")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }

            modeRoutePreview

            ForEach(Array(stationIds.enumerated()), id: \.offset) { index, stationId in
                HStack(alignment: .top, spacing: 10) {
                    Circle()
                        .fill(index == 0 ? transportMode.accentColor : (index == stationIds.count - 1 ? Color.orange : Color.blue))
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
                Text("Segments: \(max(stationIds.count - 1, 0))")
                Spacer()
                Text("ETA: \(displayDurationMins) min")
            }
            .font(.subheadline)
            .foregroundStyle(.secondary)
        }
        .padding()
        .background(cardBackground)
    }

    private var modeRoutePreview: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.white.opacity(0.04))
                .frame(height: 116)

            switch transportMode {
            case .train:
                trainPreview
            case .bus:
                busPreview
            case .walk:
                walkPreview
            case .taxi:
                EmptyView()
            }
        }
    }

    private var trainPreview: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let h = geo.size.height
            Path { path in
                path.move(to: CGPoint(x: 20, y: h * 0.78))
                path.addCurve(
                    to: CGPoint(x: w - 20, y: h * 0.25),
                    control1: CGPoint(x: w * 0.30, y: h * 0.55),
                    control2: CGPoint(x: w * 0.62, y: h * 0.18)
                )
            }
            .stroke(style: StrokeStyle(lineWidth: 3, lineCap: .round, dash: [3, 8]))
            .foregroundStyle(.blue)

            Circle().fill(Color.blue).frame(width: 8, height: 8).position(x: 20, y: h * 0.78)
            Circle().fill(Color.blue).frame(width: 8, height: 8).position(x: w * 0.42, y: h * 0.50)
            Circle().fill(Color.blue).frame(width: 8, height: 8).position(x: w - 20, y: h * 0.25)
        }
        .padding(.horizontal, 8)
    }

    private var busPreview: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let h = geo.size.height
            Path { path in
                path.move(to: CGPoint(x: 0, y: h * 0.32))
                path.addLine(to: CGPoint(x: w, y: h * 0.32))
                path.move(to: CGPoint(x: 0, y: h * 0.68))
                path.addLine(to: CGPoint(x: w, y: h * 0.68))
                path.move(to: CGPoint(x: w * 0.45, y: 0))
                path.addLine(to: CGPoint(x: w * 0.45, y: h))
            }
            .stroke(Color.white.opacity(0.14), lineWidth: 12)

            Path { path in
                path.move(to: CGPoint(x: 24, y: h * 0.68))
                path.addCurve(
                    to: CGPoint(x: w - 24, y: h * 0.32),
                    control1: CGPoint(x: w * 0.32, y: h * 0.70),
                    control2: CGPoint(x: w * 0.64, y: h * 0.34)
                )
            }
            .stroke(style: StrokeStyle(lineWidth: 3, lineCap: .round, dash: [5, 7]))
            .foregroundStyle(.green)

            Image(systemName: "bus.fill")
                .font(.headline)
                .foregroundStyle(.yellow)
                .position(x: w * 0.70, y: h * 0.42)
        }
        .padding(.horizontal, 8)
    }

    private var walkPreview: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let h = geo.size.height

            RoundedRectangle(cornerRadius: 10)
                .fill(Color.white.opacity(0.03))
                .frame(width: w * 0.92, height: h * 0.80)
                .position(x: w * 0.5, y: h * 0.5)

            Path { path in
                path.move(to: CGPoint(x: 22, y: h * 0.76))
                path.addCurve(
                    to: CGPoint(x: w - 24, y: h * 0.22),
                    control1: CGPoint(x: w * 0.28, y: h * 0.30),
                    control2: CGPoint(x: w * 0.65, y: h * 0.90)
                )
            }
            .stroke(style: StrokeStyle(lineWidth: 4, lineCap: .round, lineJoin: .round))
            .foregroundStyle(Color(red: 0.17, green: 0.56, blue: 1.0))

            Circle().fill(Color.green).frame(width: 9, height: 9).position(x: 22, y: h * 0.76)
            Circle().fill(Color.red).frame(width: 9, height: 9).position(x: w - 24, y: h * 0.22)
            Image(systemName: "figure.walk")
                .foregroundStyle(.white)
                .position(x: w * 0.52, y: h * 0.52)
        }
        .padding(.horizontal, 8)
    }

    private var displayStationIds: [Int] {
        if let result = lastResult, !result.stationIds.isEmpty {
            return result.stationIds
        }
        if let pre = stationId(code: "PRE"), let lan = stationId(code: "LAN") {
            return [pre, lan]
        }
        return Array(appState.stations.prefix(2).map(\.id))
    }

    private var displayDurationMins: Int {
        if let mins = lastResult?.totalDurationMins {
            return mins
        }
        return transportMode.defaultDurationMins
    }

    private func stationId(code: String) -> Int? {
        appState.stations.first(where: { $0.code == code })?.id
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
                            openDirectionsForTicket()
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
                            addToWalletTapped()
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
                        .buttonStyle(.plain)
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

    private var mapScreen: some View {
        NavigationStack {
            ZStack(alignment: .bottomTrailing) {
                Map(position: $mapPosition) {
                    ForEach(appState.stations, id: \.id) { station in
                        if let lat = station.latitude, let lon = station.longitude {
                            Marker(station.name, coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lon))
                                .tint(.blue)
                        }
                    }
                }
                .mapStyle(.standard(elevation: .realistic))
                .ignoresSafeArea()

                Button {
                    mapPosition = .region(
                        MKCoordinateRegion(
                            center: CLLocationCoordinate2D(latitude: 53.90, longitude: -2.95),
                            span: MKCoordinateSpan(latitudeDelta: 0.85, longitudeDelta: 1.0)
                        )
                    )
                } label: {
                    Image(systemName: "scope")
                        .font(.headline)
                        .foregroundStyle(.white)
                        .frame(width: 42, height: 42)
                        .background(Circle().fill(Color.black.opacity(0.65)))
                }
                .padding(.trailing, 16)
                .padding(.bottom, 18)
            }
            .navigationTitle("Map")
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
                            SavedStopsScreen(stops: savedStops)
                        } label: {
                            accountActionRow(title: "Saved Stops", icon: "star.fill")
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

    private var nearestStopInfo: NearbyStopInfo? {
        guard transportMode == .train || transportMode == .bus else { return nil }
        let stationsWithCoords = appState.stations.filter { $0.latitude != nil && $0.longitude != nil }
        guard !stationsWithCoords.isEmpty else { return nil }

        let referenceCoordinate = locationManager.location?.coordinate
            ?? CLLocationCoordinate2D(latitude: 54.0466, longitude: -2.8007)
        let referenceLocation = CLLocation(latitude: referenceCoordinate.latitude, longitude: referenceCoordinate.longitude)

        return stationsWithCoords
            .compactMap { station -> NearbyStopInfo? in
                guard let lat = station.latitude, let lon = station.longitude else { return nil }
                let stationLocation = CLLocation(latitude: lat, longitude: lon)
                let distance = referenceLocation.distance(from: stationLocation)
                return NearbyStopInfo(station: station, distanceMeters: distance)
            }
            .min(by: { $0.distanceMeters < $1.distanceMeters })
    }

    private func nearestStopCamera(for station: Station) -> MapCameraPosition {
        guard let lat = station.latitude, let lon = station.longitude else {
            return .region(
                MKCoordinateRegion(
                    center: CLLocationCoordinate2D(latitude: 54.0466, longitude: -2.8007),
                    span: MKCoordinateSpan(latitudeDelta: 0.04, longitudeDelta: 0.04)
                )
            )
        }

        return .region(
            MKCoordinateRegion(
                center: CLLocationCoordinate2D(latitude: lat, longitude: lon),
                span: MKCoordinateSpan(latitudeDelta: 0.02, longitudeDelta: 0.02)
            )
        )
    }

    private func distanceText(for meters: CLLocationDistance) -> String {
        if meters < 1000 {
            return "\(Int(meters.rounded()))m"
        }
        return String(format: "%.1fkm", meters / 1000)
    }

    private func isStopSaved(_ stationId: Int) -> Bool {
        savedStops.contains(where: { $0.stationId == stationId })
    }

    private func toggleSavedStop(_ stop: NearbyStopInfo) {
        if let idx = savedStops.firstIndex(where: { $0.stationId == stop.station.id }) {
            savedStops.remove(at: idx)
        } else {
            savedStops.insert(
                SavedStop(
                    stationId: stop.station.id,
                    name: stop.station.name,
                    code: stop.station.code,
                    mode: transportMode,
                    distanceText: distanceText(for: stop.distanceMeters)
                ),
                at: 0
            )
        }
    }

    private func showDepartures(for station: Station) {
        let modeText = transportMode == .train ? "train" : "bus"
        departuresMessage = "Next \(modeText) departures from \(station.name): 3 min, 11 min, 18 min."
    }

    private func openDirections(to station: Station) {
        guard let lat = station.latitude, let lon = station.longitude else { return }
        let destination = MKMapItem(placemark: MKPlacemark(coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lon)))
        destination.name = station.name

        var items: [MKMapItem] = []
        if let location = locationManager.location {
            let source = MKMapItem(placemark: MKPlacemark(coordinate: location.coordinate))
            source.name = "Current Location"
            items.append(source)
        }
        items.append(destination)

        MKMapItem.openMaps(with: items, launchOptions: [MKLaunchOptionsDirectionsModeKey: MKLaunchOptionsDirectionsModeTransit])
    }

    private func openDirectionsForTicket() {
        if let lancaster = appState.stations.first(where: { $0.code == "LAN" }) {
            openDirections(to: lancaster)
        } else if let firstStation = appState.stations.first {
            openDirections(to: firstStation)
        }
    }

    private func addToWalletTapped() {
        guard PKAddPassesViewController.canAddPasses() else {
            walletMessage = "This device cannot add passes to Apple Wallet."
            return
        }

        if let bundledPass = loadBundledPass() {
            walletPresentation = WalletPassPresentation(passes: [bundledPass])
            return
        }
        showPassImporter = true
    }

    private func loadBundledPass() -> PKPass? {
        guard
            let url = Bundle.main.url(forResource: "TrainTicket", withExtension: "pkpass"),
            let data = try? Data(contentsOf: url),
            let pass = try? PKPass(data: data)
        else {
            return nil
        }
        return pass
    }

    private func handlePassImport(result: Result<URL, Error>) {
        switch result {
        case .success(let url):
            do {
                let didAccess = url.startAccessingSecurityScopedResource()
                defer {
                    if didAccess {
                        url.stopAccessingSecurityScopedResource()
                    }
                }
                let data = try Data(contentsOf: url)
                let pass = try PKPass(data: data)
                walletPresentation = WalletPassPresentation(passes: [pass])
            } catch {
                walletMessage = "Could not read this pass. Please choose a valid signed .pkpass file."
            }
        case .failure:
            walletMessage = "No pass selected."
        }
    }

}

private enum AppTab: Hashable {
    case home
    case map
    case tickets
    case account
}

private struct PlannedRoute {
    let mode: TransportMode
    let stationIds: [Int]
    let durationMins: Int
}

private struct PlannedRouteScreen: View {
    @Environment(\.dismiss) private var dismiss
    let plan: PlannedRoute
    let mapping: StationMapping

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color(red: 0.03, green: 0.11, blue: 0.28), Color.black],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Button {
                        dismiss()
                    } label: {
                        Text("Back")
                            .font(.headline)
                            .foregroundStyle(.white)
                    }
                    Spacer()
                }

                Text("\(plan.mode.stageTitle)")
                    .font(.title2.weight(.semibold))
                    .foregroundStyle(.white)

                VStack(alignment: .leading, spacing: 12) {
                    ForEach(Array(plan.stationIds.enumerated()), id: \.offset) { index, stationId in
                        HStack(spacing: 10) {
                            Circle()
                                .fill(index == 0 ? plan.mode.accentColor : Color.white.opacity(0.4))
                                .frame(width: 10, height: 10)
                            Text(mapping.name(for: stationId))
                                .foregroundStyle(.white)
                            Spacer()
                            Text(mapping.code(for: stationId))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
                .padding()
                .background(
                    RoundedRectangle(cornerRadius: 16)
                        .fill(Color.white.opacity(0.08))
                )

                HStack {
                    Text("Estimated time")
                        .foregroundStyle(.secondary)
                    Spacer()
                    Text("\(plan.durationMins) min")
                        .font(.headline)
                        .foregroundStyle(.white)
                }

                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.top, 16)
        }
        .navigationBarBackButtonHidden(true)
        .toolbar(.hidden, for: .navigationBar)
    }
}

private struct SearchTrainsScreen: View {
    let stations: [Station]
    @State private var originId: Int?
    @State private var destinationId: Int?
    @State private var originQuery = ""
    @State private var destinationQuery = ""
    @State private var tripType: TripType = .single
    @State private var passengerCount: Int = 1
    @State private var selectedRailcard: String = "No Railcard"
    @FocusState private var focusedField: SearchField?

    private var hasValidWhere: Bool {
        guard let originId, let destinationId else { return false }
        return originId != destinationId
    }

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color(red: 0.02, green: 0.09, blue: 0.20), Color.black],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            ScrollView {
                VStack(spacing: 16) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Plan Your Rail Journey")
                            .font(.custom("AvenirNext-Bold", size: 28))
                            .foregroundStyle(.white)
                        Text("Enter where you are going and customise your trip")
                            .font(.custom("AvenirNext-Regular", size: 14))
                            .foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)

                    whereCard

                    if hasValidWhere {
                        whenCard
                        passengersCard
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
                .padding(.bottom, 26)
            }
        }
        .navigationTitle("Search Trains")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var whereCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Where")
                .font(.custom("AvenirNext-DemiBold", size: 20))
                .foregroundStyle(.white)

            stationSearchField(
                title: "Origin",
                placeholder: "Type departure station",
                text: $originQuery,
                field: .origin
            )

            if focusedField == .origin && !originSuggestions.isEmpty {
                suggestionsList(originSuggestions) { station in
                    originQuery = station.name
                    originId = station.id
                    focusedField = nil
                }
            }

            stationSearchField(
                title: "Destination",
                placeholder: "Type arrival station",
                text: $destinationQuery,
                field: .destination
            )

            if focusedField == .destination && !destinationSuggestions.isEmpty {
                suggestionsList(destinationSuggestions) { station in
                    destinationQuery = station.name
                    destinationId = station.id
                    focusedField = nil
                }
            }
        }
        .padding(14)
        .background(sectionCard)
        .onChange(of: originQuery) { _, newValue in
            originId = exactMatch(for: newValue)?.id
        }
        .onChange(of: destinationQuery) { _, newValue in
            destinationId = exactMatch(for: newValue)?.id
        }
    }

    private var whenCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("When")
                .font(.custom("AvenirNext-DemiBold", size: 20))
                .foregroundStyle(.white)

            HStack(spacing: 8) {
                ForEach(TripType.allCases, id: \.self) { type in
                    Button {
                        tripType = type
                    } label: {
                        Text(type.shortTitle)
                            .font(.custom("AvenirNext-DemiBold", size: 13))
                            .foregroundStyle(tripType == type ? .white : .secondary)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .background(
                                RoundedRectangle(cornerRadius: 10)
                                    .fill(tripType == type ? Color.blue : Color.white.opacity(0.06))
                            )
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(14)
        .background(sectionCard)
    }

    private var passengersCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Passengers")
                .font(.custom("AvenirNext-DemiBold", size: 20))
                .foregroundStyle(.white)

            HStack {
                Text("\(passengerCount) passenger\(passengerCount == 1 ? "" : "s")")
                    .font(.custom("AvenirNext-Medium", size: 16))
                    .foregroundStyle(.white)
                Spacer()
                Stepper("", value: $passengerCount, in: 1...12)
                    .labelsHidden()
            }
            .padding(12)
            .background(fieldCard)

            VStack(alignment: .leading, spacing: 8) {
                Text("Railcard")
                    .font(.custom("AvenirNext-Medium", size: 14))
                    .foregroundStyle(.secondary)

                Picker("Railcard", selection: $selectedRailcard) {
                    ForEach(RailcardOption.all, id: \.self) { railcard in
                        Text(railcard).tag(railcard)
                    }
                }
                .pickerStyle(.menu)
                .tint(.white)
                .padding(12)
                .background(fieldCard)
            }
        }
        .padding(14)
        .background(sectionCard)
    }

    private func stationSearchField(
        title: String,
        placeholder: String,
        text: Binding<String>,
        field: SearchField
    ) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.custom("AvenirNext-Medium", size: 14))
                .foregroundStyle(.secondary)

            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass")
                    .foregroundStyle(.blue)
                TextField(placeholder, text: text)
                    .focused($focusedField, equals: field)
                    .textInputAutocapitalization(.words)
                    .autocorrectionDisabled(true)
                    .foregroundStyle(.white)
            }
            .padding(12)
            .background(fieldCard)
        }
    }

    private func suggestionsList(_ items: [Station], onSelect: @escaping (Station) -> Void) -> some View {
        VStack(spacing: 0) {
            ForEach(items.prefix(6), id: \.id) { station in
                Button {
                    onSelect(station)
                } label: {
                    HStack {
                        Text(station.name)
                            .foregroundStyle(.white)
                        Spacer()
                        Text(station.code)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                }
                .buttonStyle(.plain)

                if station.id != items.prefix(6).last?.id {
                    Divider().overlay(Color.white.opacity(0.08))
                }
            }
        }
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.black.opacity(0.28))
        )
    }

    private func exactMatch(for text: String) -> Station? {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        return stations.first {
            $0.name.caseInsensitiveCompare(trimmed) == .orderedSame ||
            $0.code.caseInsensitiveCompare(trimmed) == .orderedSame
        }
    }

    private var originSuggestions: [Station] {
        stationMatches(for: originQuery)
    }

    private var destinationSuggestions: [Station] {
        stationMatches(for: destinationQuery)
    }

    private func stationMatches(for query: String) -> [Station] {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !q.isEmpty else { return [] }
        return stations.filter {
            $0.name.localizedCaseInsensitiveContains(q) ||
            $0.code.localizedCaseInsensitiveContains(q)
        }
    }

    private var sectionCard: some View {
        RoundedRectangle(cornerRadius: 18)
            .fill(Color.white.opacity(0.08))
            .overlay(
                RoundedRectangle(cornerRadius: 18)
                    .stroke(Color.white.opacity(0.06), lineWidth: 1)
            )
    }

    private var fieldCard: some View {
        RoundedRectangle(cornerRadius: 12)
            .fill(Color.white.opacity(0.06))
    }
}

private enum SearchField {
    case origin
    case destination
}

private enum TripType: CaseIterable {
    case single
    case `return`
    case openReturn

    var title: String {
        switch self {
        case .single: return "Single Trip"
        case .return: return "Return Trip"
        case .openReturn: return "Open Return Trip"
        }
    }

    var shortTitle: String {
        switch self {
        case .single: return "Single"
        case .return: return "Return"
        case .openReturn: return "Open Return"
        }
    }
}

private enum RailcardOption {
    static let all: [String] = [
        "No Railcard",
        "16-25 Railcard",
        "26-30 Railcard",
        "Senior Railcard",
        "Two Together Railcard",
        "Family & Friends Railcard",
        "Disabled Persons Railcard",
        "16-17 Saver",
        "Veterans Railcard",
        "Network Railcard"
    ]
}

private enum TransportMode: String, CaseIterable {
    case train
    case bus
    case taxi
    case walk

    var title: String {
        switch self {
        case .train: return "Train"
        case .bus: return "Bus"
        case .taxi: return "Taxi"
        case .walk: return "Walk"
        }
    }

    var icon: String {
        switch self {
        case .train: return "tram.fill"
        case .bus: return "bus.fill"
        case .taxi: return "car.fill"
        case .walk: return "figure.walk"
        }
    }

    var stageTitle: String {
        switch self {
        case .train: return "Train Stage"
        case .bus: return "Bus Stage"
        case .taxi: return "Taxi Stage"
        case .walk: return "Walk Stage"
        }
    }

    var accentColor: Color {
        switch self {
        case .train: return .blue
        case .bus: return .green
        case .taxi: return .yellow
        case .walk: return .orange
        }
    }

    var defaultDurationMins: Int {
        switch self {
        case .train: return 18
        case .bus: return 45
        case .taxi: return 22
        case .walk: return 32
        }
    }
}

private struct SavedTrip: Identifiable {
    let id = UUID()
    let title: String
    let subtitle: String
}

private struct SavedStop: Identifiable {
    let id = UUID()
    let stationId: Int
    let name: String
    let code: String
    let mode: TransportMode
    let distanceText: String
}

private struct AccountNotification: Identifiable {
    let id = UUID()
    let title: String
    let subtitle: String
}

private struct NearbyStopInfo {
    let station: Station
    let distanceMeters: CLLocationDistance
}

private struct WalletPassPresentation: Identifiable {
    let id = UUID()
    let passes: [PKPass]
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

private struct SavedStopsScreen: View {
    let stops: [SavedStop]

    var body: some View {
        List {
            if stops.isEmpty {
                Text("No stops to show")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(stops) { stop in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(stop.name)
                            .font(.headline)
                        Text("\(stop.code) • \(stop.mode.title) • \(stop.distanceText)")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 6)
                }
            }
        }
        .navigationTitle("Saved Stops")
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

private struct AddPassesSheet: UIViewControllerRepresentable {
    let passes: [PKPass]

    func makeUIViewController(context: Context) -> UIViewController {
        if let controller = PKAddPassesViewController(passes: passes) {
            return controller
        }
        return UIViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

private final class UserLocationManager: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var location: CLLocation?

    private let manager = CLLocationManager()
    private var didRequestPermission = false

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
    }

    func requestPermissionIfNeeded() {
        guard !didRequestPermission else { return }
        didRequestPermission = true

        switch manager.authorizationStatus {
        case .authorizedAlways, .authorizedWhenInUse:
            manager.startUpdatingLocation()
        case .notDetermined:
            manager.requestWhenInUseAuthorization()
        case .denied, .restricted:
            break
        @unknown default:
            break
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        switch manager.authorizationStatus {
        case .authorizedAlways, .authorizedWhenInUse:
            manager.startUpdatingLocation()
        default:
            break
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        location = locations.last
    }
}

#Preview {
    ContentView()
        .environmentObject(AppState())
}
