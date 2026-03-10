import SwiftUI
import MapKit
import CoreLocation
import PassKit
import UniformTypeIdentifiers

struct ContentView: View {
    @EnvironmentObject var appState: AppState
    @Environment(\.colorScheme) private var systemColorScheme
    @AppStorage("preferredAppearance") private var preferredAppearance = "dark"
    @AppStorage("appLanguage") private var appLanguageRawValue = AppLanguage.en.rawValue
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
    @State private var splashTrainOffset: CGFloat = -220
    @State private var splashBusOffset: CGFloat = 220
    @State private var splashTrainTilt: Double = -24
    @State private var splashBusTilt: Double = 24
    @State private var splashVehiclesOpacity: Double = 0.0
    @State private var splashVehiclesScale: CGFloat = 1.0
    @State private var splashNexoOpacity: Double = 0.0
    @State private var splashNexoScale: CGFloat = 0.88
    @State private var splashImpactOpacity: Double = 0.0
    @State private var splashOverlayOpacity: Double = 1.0
    @State private var showPassImporter = false
    @State private var walletPresentation: WalletPassPresentation?
    @State private var walletMessage: String?
    @State private var departuresMessage: String?
    @State private var selectedMapStationId: Int?
    @State private var mapDepartureMode: TransportMode = .train
    @State private var mapPanelExpanded = false
    @State private var showDirectionsSheet = false
    @State private var directionsStation: Station?
    @State private var directionsMode: TransportMode = .train
    @State private var showTrackSheet = false
    @State private var walkFromQuery = ""
    @State private var walkToQuery = ""
    @State private var walkRoute: MKRoute?
    @State private var walkRouteError: String?
    @State private var walkMapPosition: MapCameraPosition = .automatic
    @FocusState private var walkFocusedField: WalkField?
    @State private var ticketReference = "TR-8829"
    @State private var ticketTravelDate = Date()
    @State private var ticketDurationMins = 42
    @State private var ticketRailcardUsed = true
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

    private var selectedMapStation: Station? {
        guard let selectedMapStationId else { return nil }
        return appState.stations.first(where: { $0.id == selectedMapStationId })
    }

    private var appLanguage: AppLanguage {
        AppLanguage(rawValue: appLanguageRawValue) ?? .en
    }

    private var usesLightPalette: Bool {
        switch preferredAppearance {
        case "light":
            return true
        case "dark":
            return false
        default:
            return systemColorScheme == .light
        }
    }

    private var primaryTextColor: Color {
        usesLightPalette ? Color.black : Color.white
    }

    private var secondaryTextColor: Color {
        usesLightPalette ? Color.black.opacity(0.62) : Color.secondary
    }

    private var cardFillColor: Color {
        usesLightPalette ? Color.white.opacity(0.96) : Color.white.opacity(0.08)
    }

    private var cardStrokeColor: Color {
        usesLightPalette ? Color.black.opacity(0.10) : Color.white.opacity(0.06)
    }

    private var innerFillColor: Color {
        usesLightPalette ? Color.black.opacity(0.05) : Color.white.opacity(0.05)
    }

    private func t(_ key: L.Key) -> String {
        L.text(key, lang: appLanguage)
    }

    var body: some View {
        ZStack {
        TabView(selection: $selectedTab) {
            homeScreen
                .tabItem {
                    Label(t(.home), systemImage: "house.fill")
                }
                .tag(AppTab.home)

            mapScreen
                .tabItem {
                    Label(t(.map), systemImage: "map.fill")
                }
                .tag(AppTab.map)

            ticketsScreen
                .tabItem {
                    Label(t(.tickets), systemImage: "ticket.fill")
                }
                .tag(AppTab.tickets)

                accountScreen
                    .tabItem {
                        Label(t(.account), systemImage: "person.fill")
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
        .toolbarBackground(usesLightPalette ? Color.white : Color(red: 0.02, green: 0.07, blue: 0.14), for: .tabBar)
        .toolbarColorScheme(usesLightPalette ? .light : .dark, for: .tabBar)
        .toolbarColorScheme(usesLightPalette ? .light : .dark, for: .navigationBar)
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
        .alert(t(.appleWallet), isPresented: Binding(
            get: { walletMessage != nil },
            set: { if !$0 { walletMessage = nil } }
        )) {
            Button(t(.ok), role: .cancel) {}
        } message: {
            Text(walletMessage ?? "")
        }
        .alert(t(.departures), isPresented: Binding(
            get: { departuresMessage != nil },
            set: { if !$0 { departuresMessage = nil } }
        )) {
            Button(t(.ok), role: .cancel) {}
        } message: {
            Text(departuresMessage ?? "")
        }
        .sheet(isPresented: $showDirectionsSheet) {
            if let station = directionsStation {
                DirectionsSheet(
                    station: station,
                    mode: $directionsMode,
                    userLocation: locationManager.location?.coordinate
                )
            }
        }
        .sheet(isPresented: $showTrackSheet) {
            TrackSheet(
                stations: appState.stations,
                mapping: appState.mapping
            )
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

            GeometryReader { geo in
                ZStack {
                    Circle()
                        .fill(Color.white.opacity(0.28))
                        .frame(width: 120, height: 120)
                        .blur(radius: 14)
                        .scaleEffect(splashImpactOpacity > 0 ? 1.15 : 0.72)
                        .opacity(splashImpactOpacity)

                    Capsule()
                        .fill(Color.blue.opacity(0.18))
                        .frame(width: 220, height: 20)
                        .blur(radius: 10)
                        .opacity(splashVehiclesOpacity)

                    HStack(spacing: 52) {
                        splashVehicle(symbol: "tram.fill", tint: .cyan, tilt: splashTrainTilt)
                            .offset(x: splashTrainOffset)

                        splashVehicle(symbol: "bus.fill", tint: .blue, tilt: splashBusTilt)
                            .offset(x: splashBusOffset)
                    }
                    .opacity(splashVehiclesOpacity)
                    .scaleEffect(splashVehiclesScale)

                    Text("Nexo")
                        .font(.system(size: 56, weight: .heavy, design: .rounded))
                        .foregroundStyle(.white)
                        .shadow(color: .blue.opacity(0.4), radius: 14)
                        .opacity(splashNexoOpacity)
                        .scaleEffect(splashNexoScale)
                }
                .frame(width: geo.size.width, height: geo.size.height)
            }
        }
        .opacity(splashOverlayOpacity)
    }

    private func startSplashAnimation() {
        guard showWelcomeSplash else { return }

        splashTrainOffset = -220
        splashBusOffset = 220
        splashTrainTilt = -24
        splashBusTilt = 24
        splashVehiclesOpacity = 0.0
        splashVehiclesScale = 1.0
        splashNexoOpacity = 0.0
        splashNexoScale = 0.88
        splashImpactOpacity = 0.0
        splashOverlayOpacity = 1.0

        withAnimation(.easeOut(duration: 0.35)) {
            splashVehiclesOpacity = 1.0
        }

        withAnimation(.interpolatingSpring(stiffness: 90, damping: 12).delay(0.1)) {
            splashTrainOffset = -10
            splashBusOffset = 10
            splashTrainTilt = -6
            splashBusTilt = 6
        }

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.82) {
            withAnimation(.easeOut(duration: 0.16)) {
                splashImpactOpacity = 1.0
            }
            withAnimation(.easeIn(duration: 0.20).delay(0.10)) {
                splashImpactOpacity = 0.0
            }

            withAnimation(.easeInOut(duration: 0.28)) {
                splashVehiclesScale = 0.72
                splashVehiclesOpacity = 0.0
            }
            withAnimation(.spring(response: 0.42, dampingFraction: 0.72).delay(0.06)) {
                splashNexoOpacity = 1.0
                splashNexoScale = 1.0
            }
        }

        DispatchQueue.main.asyncAfter(deadline: .now() + 1.8) {
            withAnimation(.easeInOut(duration: 0.35)) {
                splashOverlayOpacity = 0.0
            }
        }

        DispatchQueue.main.asyncAfter(deadline: .now() + 2.15) {
            withAnimation(.easeOut(duration: 0.2)) {
                showWelcomeSplash = false
            }
        }
    }

    private func splashVehicle(symbol: String, tint: Color, tilt: Double) -> some View {
        ZStack {
            Image(systemName: symbol)
                .font(.system(size: 42, weight: .bold))
                .foregroundStyle(Color.black.opacity(0.40))
                .offset(x: 8, y: 8)
                .blur(radius: 2)

            Image(systemName: symbol)
                .font(.system(size: 42, weight: .bold))
                .foregroundStyle(tint)
                .symbolRenderingMode(.palette)
                .rotation3DEffect(.degrees(tilt), axis: (x: 0, y: 1, z: 0), perspective: 0.6)
                .shadow(color: tint.opacity(0.35), radius: 12, x: 0, y: 8)
        }
    }

    private var homeScreen: some View {
        NavigationStack {
            ZStack {
                screenGradient
                ScrollView {
                    VStack(spacing: 14) {
                        homeBrandLogo
                        welcomeCard
                        searchAllTrainsCard
                        transportModeCard
                        if transportMode == .taxi {
                            taxiCard
                        } else if transportMode == .train || transportMode == .bus {
                            nearbyStopMapCard
                        } else if transportMode == .walk {
                            walkPlannerCard
                        }
                        if let err = lastResultError {
                            messageCard(text: err, color: .red)
                        }
                        if let appError = appState.errorMessage {
                            messageCard(text: appError, color: .red)
                        }
                        experiencesCard
                    }
                    .padding(.horizontal, 14)
                    .padding(.bottom, 24)
                }
                .scrollIndicators(.hidden)
            }
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(isPresented: $showPlannedRoute) {
                if let plan = plannedRoute {
                    PlannedRouteScreen(plan: plan, mapping: appState.mapping, language: appLanguage)
                } else {
                    EmptyView()
                }
            }
        }
    }

    private var welcomeCard: some View {
        HStack {
            Circle()
                .fill(usesLightPalette ? Color.black.opacity(0.08) : Color.white.opacity(0.12))
                .frame(width: 40, height: 40)
                .overlay {
                    Image(systemName: "person.fill")
                        .foregroundStyle(usesLightPalette ? .black : .white)
                }

            VStack(alignment: .leading, spacing: 2) {
                Text(t(.welcomeBack))
                    .font(.caption)
                    .foregroundStyle(secondaryTextColor)
                Text(fullName)
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(primaryTextColor)
            }

            Spacer()
            NavigationLink {
                SettingsScreen(
                    preferredAppearance: $preferredAppearance,
                    appLanguageRawValue: $appLanguageRawValue,
                    fullName: $fullName,
                    email: $email,
                    phone: $phone
                )
            } label: {
                Image(systemName: "gearshape.fill")
                    .font(.title2)
                    .foregroundStyle(secondaryTextColor)
                    .frame(width: 36, height: 36)
                    .background(
                        Circle().fill(usesLightPalette ? Color.black.opacity(0.06) : Color.white.opacity(0.06))
                    )
            }
            .buttonStyle(.plain)
        }
        .padding()
        .background(cardBackground)
    }

    private var homeBrandLogo: some View {
        Image("nexoLogo")
            .resizable()
            .renderingMode(.original)
            .interpolation(.high)
            .scaledToFit()
            .frame(maxWidth: .infinity)
            .frame(height: 120)
            .padding(.top, 6)
            .padding(.bottom, 2)
    }

    private var searchAllTrainsCard: some View {
        NavigationLink {
            SearchTrainsScreen(stations: appState.stations, language: appLanguage, savedTrips: $savedTrips)
        } label: {
            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass")
                    .font(.headline)
                    .foregroundStyle(.blue)
                    .frame(width: 24)

                Text(t(.searchAllTrains))
                    .font(.headline)
                    .foregroundStyle(primaryTextColor)

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(secondaryTextColor)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 16)
            .background(
                RoundedRectangle(cornerRadius: 14)
                    .fill(innerFillColor)
            )
        }
        .buttonStyle(.plain)
        .padding()
        .background(cardBackground)
    }

    private var experiencesCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(t(.recommended))
                    .font(.headline)
                    .foregroundStyle(primaryTextColor)
                Spacer()
                HStack(spacing: 6) {
                    Image(systemName: "mappin.circle.fill")
                    Text("TripAdvisor API")
                }
                .font(.caption.weight(.semibold))
                .foregroundStyle(.green)
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(HomeExperience.mock) { item in
                        VStack(alignment: .leading, spacing: 0) {
                            AsyncImage(url: item.imageURL) { phase in
                                switch phase {
                                case .success(let image):
                                    image
                                        .resizable()
                                        .scaledToFill()
                                default:
                                    LinearGradient(
                                        colors: [Color.blue.opacity(0.35), Color.black.opacity(0.55)],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    )
                                    .overlay(
                                        Image(systemName: "photo")
                                            .font(.title2)
                                            .foregroundStyle(.white.opacity(0.7))
                                    )
                                }
                            }
                            .frame(height: 112)
                            .clipped()

                            VStack(alignment: .leading, spacing: 6) {
                                Text(item.title)
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(primaryTextColor)
                                    .lineLimit(1)
                                Text(item.location)
                                    .font(.caption)
                                    .foregroundStyle(secondaryTextColor)
                                HStack {
                                    Text(item.price)
                                        .font(.caption.weight(.semibold))
                                        .foregroundStyle(.cyan)
                                    Spacer()
                                    Text(t(.book))
                                        .font(.caption.weight(.bold))
                                        .foregroundStyle(.white)
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 5)
                                        .background(
                                            RoundedRectangle(cornerRadius: 8)
                                                .fill(Color.blue)
                                        )
                                }
                            }
                            .padding(10)
                            .background(usesLightPalette ? Color.black.opacity(0.04) : Color.white.opacity(0.05))
                        }
                        .frame(width: 205)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                        .overlay(
                            RoundedRectangle(cornerRadius: 14)
                                .stroke(usesLightPalette ? Color.black.opacity(0.10) : Color.white.opacity(0.08), lineWidth: 1)
                        )
                    }
                }
            }
        }
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
        let foregroundColor: Color = {
            if isSelected { return .white }
            return usesLightPalette ? Color.black.opacity(0.65) : .secondary
        }()
        let backgroundColor: Color = {
            if isSelected { return Color.blue.opacity(0.95) }
            return usesLightPalette ? Color.black.opacity(0.05) : Color.white.opacity(0.06)
        }()
        let borderColor: Color = {
            if isSelected { return .blue }
            return usesLightPalette ? Color.black.opacity(0.10) : Color.white.opacity(0.08)
        }()

        return Button {
            transportMode = mode
        } label: {
            VStack(spacing: 10) {
                Image(systemName: mode.icon)
                    .font(.title3)
                Text(mode.title(in: appLanguage))
                    .font(.subheadline.weight(.semibold))
            }
            .foregroundStyle(foregroundColor)
            .frame(maxWidth: .infinity)
            .frame(height: 86)
            .background(
                RoundedRectangle(cornerRadius: 14)
                    .fill(backgroundColor)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(borderColor, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }

    private var algorithmCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("\(t(.route))")
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
            Text("\(t(.find)) \(transportMode.title(in: appLanguage)) \(t(.route))")
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
                    Text("\(t(.find)) \(t(.taxi))")
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
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.white.opacity(0.06))
                .frame(height: 340)
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
                        ProgressView(t(.locatingNearestStop))
                            .tint(.white)
                    }
                }

            if let stop = nearestStopInfo {
                nearbyStopInfoBox(stop)
            }
        }
        .padding()
        .background(cardBackground)
    }

    private func nearbyStopInfoBox(_ stop: NearbyStopInfo) -> some View {
        let saved = isStopSaved(stop.station.id)
        return VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .center) {
                HStack(spacing: 7) {
                    Text(transportMode == .train ? t(.station) : t(.stop))
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 7)
                        .padding(.vertical, 4)
                        .background(
                            RoundedRectangle(cornerRadius: 6)
                                .fill(transportMode == .train ? .blue : .green)
                        )
                    Text(stop.station.name)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(primaryTextColor)
                }

                Spacer()

                Button {
                    toggleSavedStop(stop)
                } label: {
                    Image(systemName: saved ? "star.fill" : "star")
                        .font(.headline)
                        .foregroundStyle(saved ? .yellow : (usesLightPalette ? Color.black.opacity(0.7) : .white))
                }
                .buttonStyle(.plain)
            }

            Text("\(distanceText(for: stop.distanceMeters)) \(t(.away))")
                .font(.caption)
                .foregroundStyle(secondaryTextColor)

            HStack(spacing: 8) {
                Button {
                    directionsStation = stop.station
                    directionsMode = transportMode
                    showDirectionsSheet = true
                } label: {
                    Label(t(.directions), systemImage: "arrow.triangle.turn.up.right.diamond.fill")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(
                            RoundedRectangle(cornerRadius: 10)
                                .fill(Color.blue)
                        )
                }
                .buttonStyle(.plain)

                Button {
                    showDepartures(for: stop.station)
                } label: {
                    Label(t(.departures), systemImage: "clock.fill")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(usesLightPalette ? Color.black : Color.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(
                            RoundedRectangle(cornerRadius: 10)
                                .fill(usesLightPalette ? Color.black.opacity(0.08) : Color.white.opacity(0.12))
                        )
                }
                .buttonStyle(.plain)
            }
        }
        .padding(10)
        .background(
            RoundedRectangle(cornerRadius: 14)
                .fill(usesLightPalette ? Color.white.opacity(0.90) : Color.black.opacity(0.45))
                .overlay(
                    RoundedRectangle(cornerRadius: 14)
                        .stroke(usesLightPalette ? Color.black.opacity(0.10) : Color.white.opacity(0.08), lineWidth: 1)
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

    private var walkPlannerCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Walk Route")
                .font(.headline)
                .foregroundStyle(primaryTextColor)

            VStack(spacing: 6) {
                HStack(spacing: 8) {
                    Image(systemName: "location.fill")
                        .foregroundStyle(.blue)
                    TextField("From (current location or stop)", text: $walkFromQuery)
                        .focused($walkFocusedField, equals: .from)
                        .textInputAutocapitalization(.words)
                        .autocorrectionDisabled(true)
                        .foregroundStyle(primaryTextColor)
                        .submitLabel(.done)
                        .onSubmit { walkFocusedField = nil }
                }
                .padding(12)
                .background(innerFillColor)

                if walkFocusedField == .from && !walkFromSuggestions.isEmpty {
                    walkSuggestionsList(walkFromSuggestions) { station in
                        walkFromQuery = station.name
                        walkFocusedField = nil
                    }
                }
            }

            VStack(spacing: 6) {
                HStack(spacing: 8) {
                    Image(systemName: "mappin.and.ellipse")
                        .foregroundStyle(.blue)
                    TextField("To (destination)", text: $walkToQuery)
                        .focused($walkFocusedField, equals: .to)
                        .textInputAutocapitalization(.words)
                        .autocorrectionDisabled(true)
                        .foregroundStyle(primaryTextColor)
                        .submitLabel(.done)
                        .onSubmit { walkFocusedField = nil }
                }
                .padding(12)
                .background(innerFillColor)

                if walkFocusedField == .to && !walkToSuggestions.isEmpty {
                    walkSuggestionsList(walkToSuggestions) { station in
                        walkToQuery = station.name
                        walkFocusedField = nil
                    }
                }
            }

            Button {
                Task { await calculateWalkRoute() }
            } label: {
                Text("Find Walk Route")
                    .font(.headline.weight(.semibold))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(
                        RoundedRectangle(cornerRadius: 12)
                            .fill(Color.blue)
                    )
            }
            .buttonStyle(.plain)

            if let route = walkRoute {
                ZStack(alignment: .bottomLeading) {
                    Map(position: $walkMapPosition) {
                        MapPolyline(route.polyline)
                            .stroke(Color.blue, lineWidth: 4)
                    }
                    .frame(height: 220)
                    .clipShape(RoundedRectangle(cornerRadius: 14))

                    VStack(alignment: .leading, spacing: 4) {
                        Text("Estimated time")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Text("\(Int(route.expectedTravelTime / 60)) min")
                            .font(.headline.weight(.semibold))
                            .foregroundStyle(.white)
                    }
                    .padding(10)
                    .background(
                        RoundedRectangle(cornerRadius: 10)
                            .fill(Color.black.opacity(0.65))
                    )
                    .padding(12)
                }
            } else if let error = walkRouteError {
                Text(error)
                    .font(.caption)
                    .foregroundStyle(.red)
            }
        }
        .padding()
        .background(cardBackground)
        .onTapGesture {
            walkFocusedField = nil
        }
    }

    private var routeStageCard: some View {
        let stationIds = displayStationIds

        return VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(transportMode.stageTitle(in: appLanguage))
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
                Text("\(t(.segments)): \(max(stationIds.count - 1, 0))")
                Spacer()
                Text("ETA: \(displayDurationMins) \(t(.min))")
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
                                Text(t(.yourTicket))
                                    .font(.title3.weight(.semibold))
                                    .foregroundStyle(primaryTextColor)

                                VStack(spacing: 0) {
                                    RoundedRectangle(cornerRadius: 18)
                                        .fill(Color(red: 0.86, green: 0.65, blue: 0.49))
                                        .frame(height: 170)
                                        .overlay {
                                            VStack(spacing: 8) {
                                                Image(systemName: "qrcode")
                                                    .font(.system(size: 48))
                                                    .foregroundStyle(.black.opacity(0.85))
                                                Text(t(.readyToScan))
                                                    .font(.caption.weight(.semibold))
                                                    .foregroundStyle(.black.opacity(0.75))
                                            }
                                        }
                                        .padding(.horizontal, 28)
                                        .padding(.top, 24)

                                    Divider()
                                        .overlay(Color.white.opacity(0.08))
                                        .padding(.horizontal, 18)
                                        .padding(.vertical, 18)

                                    VStack(alignment: .leading, spacing: 16) {
                                        Text("Preston → Lancaster")
                                            .font(.title3.weight(.semibold))
                                            .foregroundStyle(.white)

                                        HStack(spacing: 16) {
                                            ticketInfoCell(
                                                label: "REFERENCE",
                                                value: ticketReference
                                            )
                                            ticketInfoCell(
                                                label: "DATE",
                                                value: ticketDateText(ticketTravelDate)
                                            )
                                        }

                                        HStack(spacing: 16) {
                                            ticketInfoCell(
                                                label: "DURATION",
                                                value: "\(ticketDurationMins) \(t(.min))"
                                            )
                                            ticketInfoCell(
                                                label: "RAILCARD",
                                                value: ticketRailcardUsed ? "Used" : "Not used"
                                            )
                                        }
                                    }
                                    .padding(.horizontal, 20)
                                    .padding(.bottom, 22)
                                }
                                .background(
                                    RoundedRectangle(cornerRadius: 24)
                                        .fill(Color.black.opacity(0.65))
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 24)
                                                .stroke(Color.white.opacity(0.08), lineWidth: 1)
                                        )
                                )
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
                            showTrackSheet = true
                        } label: {
                            Text("Track")
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
                                Text(t(.addToAppleWallet))
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
            .navigationTitle(t(.tickets))
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private var mapScreen: some View {
        NavigationStack {
            ZStack(alignment: .bottomTrailing) {
                Map(position: $mapPosition, selection: $selectedMapStationId) {
                    ForEach(appState.stations, id: \.id) { station in
                        if let lat = station.latitude, let lon = station.longitude {
                            Marker(station.name, coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lon))
                                .tint(.blue)
                                .tag(station.id)
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
            .safeAreaInset(edge: .bottom) {
                if let station = selectedMapStation {
                    mapStationArrivalsCard(for: station)
                        .padding(.horizontal, 12)
                        .padding(.bottom, 8)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
            .animation(.easeInOut(duration: 0.25), value: selectedMapStationId)
            .onChange(of: selectedMapStationId) { _, newValue in
                if newValue != nil {
                    mapDepartureMode = .train
                    mapPanelExpanded = false
                }
            }
            .navigationTitle(t(.map))
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private func mapStationArrivalsCard(for station: Station) -> some View {
        let arrivals = upcomingMapArrivals(for: station)
            .filter { $0.mode == mapDepartureMode }
        let displayedArrivals = mapPanelExpanded ? arrivals : Array(arrivals.prefix(4))
        let isSaved = isStopSaved(station.id)

        return VStack(alignment: .leading, spacing: 12) {
            Capsule()
                .fill(Color.white.opacity(0.35))
                .frame(width: 36, height: 4)
                .frame(maxWidth: .infinity)

            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(station.name)
                        .font(.headline)
                        .foregroundStyle(primaryTextColor)
                    Text("\(t(.departures)) • \(displayedArrivals.count)")
                        .font(.caption)
                        .foregroundStyle(secondaryTextColor)
                }
                Spacer()
                Button {
                    selectedMapStationId = nil
                } label: {
                    Image(systemName: "xmark")
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(usesLightPalette ? Color.black.opacity(0.8) : Color.white.opacity(0.9))
                        .frame(width: 28, height: 28)
                        .background(Circle().fill(usesLightPalette ? Color.black.opacity(0.08) : Color.white.opacity(0.12)))
                }
                .buttonStyle(.plain)
            }

            HStack(spacing: 14) {
                Spacer()
                mapDepartureModeButton(mode: .train)
                mapDepartureModeButton(mode: .bus)
                Button {
                    toggleSavedStopFromMap(station)
                } label: {
                    Image(systemName: isSaved ? "star.fill" : "star")
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(isSaved ? .yellow : (usesLightPalette ? Color.black.opacity(0.7) : .white))
                        .frame(width: 40, height: 40)
                        .background(
                            RoundedRectangle(cornerRadius: 8)
                                .fill(usesLightPalette ? Color.black.opacity(0.06) : Color.white.opacity(0.08))
                        )
                }
                .buttonStyle(.plain)
                Spacer()
            }

            VStack(spacing: 8) {
                ForEach(displayedArrivals) { item in
                    HStack(spacing: 10) {
                        Text(item.service)
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(.white)
                            .frame(width: 42, height: 30)
                            .background(
                                RoundedRectangle(cornerRadius: 8)
                                    .fill(item.color.opacity(0.8))
                            )

                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.destination)
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(primaryTextColor)
                            Text(item.operatorName)
                                .font(.caption)
                                .foregroundStyle(secondaryTextColor)
                        }

                        Spacer()

                        Text(mapEtaText(for: item))
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(.cyan)
                            .multilineTextAlignment(.trailing)
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 10)
                    .background(
                        RoundedRectangle(cornerRadius: 12)
                            .fill(usesLightPalette ? Color.black.opacity(0.04) : Color.white.opacity(0.06))
                    )
                }
            }

            if mapPanelExpanded {
                Button {
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "clock.arrow.circlepath")
                        Text(t(.viewLaterDepartures))
                    }
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.blue)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(
                        RoundedRectangle(cornerRadius: 10)
                            .fill(Color.white.opacity(0.08))
                    )
                }
                .buttonStyle(.plain)
            }
        }
        .padding(12)
        .frame(maxHeight: mapPanelExpanded ? UIScreen.main.bounds.height * 0.78 : 320, alignment: .top)
        .background(
            RoundedRectangle(cornerRadius: 18)
                .fill(usesLightPalette ? Color.white : Color(red: 0.06, green: 0.11, blue: 0.20))
                .overlay(
                    RoundedRectangle(cornerRadius: 18)
                        .stroke(usesLightPalette ? Color.black.opacity(0.10) : Color.white.opacity(0.08), lineWidth: 1)
                )
        )
        .gesture(
            DragGesture(minimumDistance: 15)
                .onEnded { value in
                    if value.translation.height < -40 {
                        mapPanelExpanded = true
                    } else if value.translation.height > 40 {
                        mapPanelExpanded = false
                    }
                }
        )
    }

    private func mapDepartureModeButton(mode: TransportMode) -> some View {
        let selected = mapDepartureMode == mode
        return Button {
            mapDepartureMode = mode
        } label: {
            Image(systemName: mode == .train ? "tram.fill" : "bus.fill")
                .font(.title3.weight(.semibold))
                .foregroundStyle(selected ? .white : (usesLightPalette ? Color.black.opacity(0.65) : .secondary))
                .frame(width: 40, height: 40)
                .background(
                    RoundedRectangle(cornerRadius: 8)
                        .fill(selected ? Color.blue : (usesLightPalette ? Color.black.opacity(0.06) : Color.white.opacity(0.08)))
                )
        }
        .buttonStyle(.plain)
    }

    private var accountScreen: some View {
        NavigationStack {
            ZStack {
                screenGradient
                VStack(alignment: .leading, spacing: 14) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(t(.studentAccount))
                            .font(.title3.weight(.semibold))
                            .foregroundStyle(primaryTextColor)
                        Text("UPSA Exchange - Lancaster University")
                            .foregroundStyle(secondaryTextColor)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding()
                    .background(cardBackground)

                    VStack(alignment: .leading, spacing: 12) {
                        Text(t(.quickActions))
                            .font(.headline)
                            .foregroundStyle(primaryTextColor)

                        NavigationLink {
                            SavedTripsScreen(trips: savedTrips, language: appLanguage)
                        } label: {
                            accountActionRow(title: t(.savedTrips), icon: "bookmark.fill")
                        }

                        NavigationLink {
                            SavedStopsScreen(stops: savedStops, language: appLanguage)
                        } label: {
                            accountActionRow(title: t(.savedStops), icon: "star.fill")
                        }

                        NavigationLink {
                            NotificationsScreen(notifications: notifications, language: appLanguage)
                        } label: {
                            accountActionRow(title: t(.notifications), icon: "bell.fill")
                        }

                        NavigationLink {
                            SettingsScreen(
                                preferredAppearance: $preferredAppearance,
                                appLanguageRawValue: $appLanguageRawValue,
                                fullName: $fullName,
                                email: $email,
                                phone: $phone
                            )
                        } label: {
                            accountActionRow(title: t(.settings), icon: "gearshape.fill")
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
            .navigationTitle(t(.account))
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
                .foregroundStyle(primaryTextColor)
            Spacer()
            Image(systemName: "chevron.right")
                .font(.headline)
                .foregroundStyle(secondaryTextColor)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 16)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 14)
                .fill(innerFillColor)
        )
    }

    private var cardBackground: some View {
        RoundedRectangle(cornerRadius: 18)
            .fill(usesLightPalette ? cardFillColor : Color.white.opacity(0.08))
            .overlay(
                RoundedRectangle(cornerRadius: 18)
                    .stroke(usesLightPalette ? cardStrokeColor : Color.white.opacity(0.06), lineWidth: 1)
            )
    }

    private var innerBackground: some View {
        RoundedRectangle(cornerRadius: 12)
            .fill(innerFillColor)
    }

    private var screenGradient: some View {
        LinearGradient(
            colors: usesLightPalette
                ? [Color.white, Color(red: 0.94, green: 0.95, blue: 0.97)]
                : [Color(red: 0.03, green: 0.11, blue: 0.28), Color.black],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
        .ignoresSafeArea()
    }

    private func ticketInfoCell(label: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.caption.weight(.bold))
                .foregroundStyle(Color.white.opacity(0.82))
            Text(value)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.white)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func ticketDateText(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "d MMM yyyy"
        return formatter.string(from: date)
    }

    private func calculateWalkRoute() async {
        walkRouteError = nil
        walkRoute = nil

        let fromQuery = walkFromQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        let toQuery = walkToQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !toQuery.isEmpty else {
            walkRouteError = "Enter a destination."
            return
        }

        let sourceItem: MKMapItem?
        if fromQuery.isEmpty || fromQuery.lowercased().contains("current") {
            if let loc = locationManager.location?.coordinate {
                sourceItem = MKMapItem(placemark: MKPlacemark(coordinate: loc))
            } else {
                sourceItem = nil
            }
        } else {
            sourceItem = await mapItem(for: fromQuery)
        }

        guard let source = sourceItem else {
            walkRouteError = "Could not find the starting location."
            return
        }

        guard let destination = await mapItem(for: toQuery) else {
            walkRouteError = "Could not find the destination."
            return
        }

        let request = MKDirections.Request()
        request.source = source
        request.destination = destination
        request.transportType = .walking

        do {
            let response = try await MKDirections(request: request).calculate()
            if let route = response.routes.first {
                walkRoute = route
                walkMapPosition = .rect(route.polyline.boundingMapRect)
            } else {
                walkRouteError = "No walking route found."
            }
        } catch {
            walkRouteError = "Route calculation failed."
        }
    }

    private func mapItem(for query: String) async -> MKMapItem? {
        let request = MKLocalSearch.Request()
        request.naturalLanguageQuery = query
        do {
            let response = try await MKLocalSearch(request: request).start()
            return response.mapItems.first
        } catch {
            return nil
        }
    }

    private var walkFromSuggestions: [Station] {
        stationMatches(for: walkFromQuery)
    }

    private var walkToSuggestions: [Station] {
        stationMatches(for: walkToQuery)
    }

    private func stationMatches(for query: String) -> [Station] {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !q.isEmpty else { return [] }
        return appState.stations.filter {
            $0.name.localizedCaseInsensitiveContains(q) ||
            $0.code.localizedCaseInsensitiveContains(q)
        }
    }

    private func walkSuggestionsList(_ items: [Station], onSelect: @escaping (Station) -> Void) -> some View {
        VStack(spacing: 0) {
            ForEach(items.prefix(6), id: \.id) { station in
                Button {
                    onSelect(station)
                } label: {
                    HStack {
                        Text(station.name)
                            .foregroundStyle(primaryTextColor)
                        Spacer()
                        Text(station.code)
                            .font(.caption)
                            .foregroundStyle(secondaryTextColor)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                }
                .buttonStyle(.plain)

                if station.id != items.prefix(6).last?.id {
                    Divider().overlay(usesLightPalette ? Color.black.opacity(0.08) : Color.white.opacity(0.08))
                }
            }
        }
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(usesLightPalette ? Color.white : Color.black.opacity(0.28))
        )
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

    private func toggleSavedStopFromMap(_ station: Station) {
        if let idx = savedStops.firstIndex(where: { $0.stationId == station.id }) {
            savedStops.remove(at: idx)
        } else {
            savedStops.insert(
                SavedStop(
                    stationId: station.id,
                    name: station.name,
                    code: station.code,
                    mode: mapDepartureMode,
                    distanceText: "--"
                ),
                at: 0
            )
        }
    }

    private func showDepartures(for station: Station) {
        selectedTab = .map
        selectedMapStationId = station.id
        mapDepartureMode = transportMode == .bus ? .bus : .train
        mapPanelExpanded = true
        if let lat = station.latitude, let lon = station.longitude {
            mapPosition = .region(
                MKCoordinateRegion(
                    center: CLLocationCoordinate2D(latitude: lat, longitude: lon),
                    span: MKCoordinateSpan(latitudeDelta: 0.08, longitudeDelta: 0.08)
                )
            )
        }
    }

    private func openDirections(to station: Station) {
        guard let lat = station.latitude, let lon = station.longitude else { return }
        let destination = MKMapItem(placemark: MKPlacemark(coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lon)))
        destination.name = station.name

        var items: [MKMapItem] = []
        if let location = locationManager.location {
            let source = MKMapItem(placemark: MKPlacemark(coordinate: location.coordinate))
            source.name = t(.currentLocation)
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
            walletMessage = t(.walletUnavailable)
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
                walletMessage = t(.walletInvalidPass)
            }
        case .failure:
            walletMessage = t(.walletNoPass)
        }
    }

    private func upcomingMapArrivals(for station: Station) -> [MapArrival] {
        let trainServices = ["A1", "N4", "T2", "W8", "R6"]
        let busServices = ["38", "14", "22", "19", "46"]
        let destinations = ["Lancaster", "Preston", "Blackpool North", "Penrith", "Warrington"]
        let trainOperators = ["Avanti West Coast", "Northern", "TransPennine", "West Midlands", "Regional Rail"]
        let busOperators = ["Stagecoach", "Stagecoach", "National Express", "Local Bus", "Metroline"]
        let colors: [Color] = [.red, .blue, .purple, .orange, .green]
        let baseOffset = station.id % 4
        let etaPattern: [Double] = [0.0, 0.6, 3.0, 7.0, 12.0, 18.0]

        let trainItems = (0..<6).map { idx in
            let i = (idx + baseOffset) % trainServices.count
            return MapArrival(
                service: trainServices[i],
                destination: destinations[i],
                operatorName: trainOperators[i],
                etaMinutes: etaPattern[idx],
                color: colors[i],
                mode: .train
            )
        }

        let busItems = (0..<6).map { idx in
            let i = (idx + baseOffset) % busServices.count
            return MapArrival(
                service: busServices[i],
                destination: destinations[i],
                operatorName: busOperators[i],
                etaMinutes: etaPattern[idx],
                color: colors[i],
                mode: .bus
            )
        }

        return trainItems + busItems
    }

    private func mapEtaText(for item: MapArrival) -> String {
        if item.etaMinutes <= 0.0 {
            return "\(item.mode.title(in: appLanguage)) \(t(.atTheStation))"
        }
        if item.etaMinutes < 1.0 {
            return t(.due)
        }
        return "\(Int(ceil(item.etaMinutes))) \(t(.min))"
    }

}

private enum AppTab: Hashable {
    case home
    case map
    case tickets
    case account
}

private enum AppLanguage: String, CaseIterable {
    case en
    case es
    case fr
    case de
    case zh

    var displayName: String {
        switch self {
        case .en: return "English"
        case .es: return "Español"
        case .fr: return "Français"
        case .de: return "Deutsch"
        case .zh: return "中文"
        }
    }
}

private enum L {
    enum Key: String {
        case home, map, tickets, account
        case welcomeSplash, welcomeBack
        case searchAllTrains, recommended, book
        case station, stop, away, directions, departures
        case find, route, segments, min
        case yourTicket, readyToScan, getDirections, addToAppleWallet
        case studentAccount, quickActions, savedTrips, savedStops, notifications, settings
        case back, estimatedTime
        case planJourneyTitle, planJourneySubtitle, searchTrains, `where`, origin, destination
        case typeDeparture, typeArrival, when, single, returnLabel, openReturn
        case travelDates, outbound, notRequired, outboundDate, returnDate, returnDateOptional
        case localOperators, passengers, railcard
        case personalData, fullName, email, phone, appearance, theme, dark, light, system, language
        case noTrips, noNotifications, noStops
        case locatingNearestStop, appleWallet, ok
        case currentLocation, walletUnavailable, walletInvalidPass, walletNoPass
        case viewLaterDepartures, due, atTheStation
        case train, bus, taxi, walk, trainStage, busStage, taxiStage, walkStage
    }

    static func text(_ key: Key, lang: AppLanguage) -> String {
        table[lang]?[key.rawValue] ?? table[.en]?[key.rawValue] ?? key.rawValue
    }

    static func passengerCount(_ count: Int, lang: AppLanguage) -> String {
        switch lang {
        case .en:
            return "\(count) passenger" + (count == 1 ? "" : "s")
        case .es:
            return "\(count) pasajero" + (count == 1 ? "" : "s")
        case .fr:
            return "\(count) passager" + (count == 1 ? "" : "s")
        case .de:
            return "\(count) Fahrgast" + (count == 1 ? "" : "e")
        case .zh:
            return "\(count) 位乘客"
        }
    }

    private static let table: [AppLanguage: [String: String]] = [
        .en: [
            "home": "Home", "map": "Map", "tickets": "Tickets", "account": "Account",
            "welcomeSplash": "Welcome to the Transport App", "welcomeBack": "Welcome back",
            "searchAllTrains": "Search all trains", "recommended": "Recommended", "book": "Book",
            "station": "STATION", "stop": "STOP", "away": "away", "directions": "Directions", "departures": "Departures",
            "find": "Find", "route": "Route", "segments": "Segments", "min": "min",
            "yourTicket": "Your Ticket", "readyToScan": "Ready to scan", "getDirections": "Get Directions", "addToAppleWallet": "Add to Apple Wallet",
            "studentAccount": "Student Account", "quickActions": "Quick Actions", "savedTrips": "Saved Trips", "savedStops": "Saved Stops", "notifications": "Notifications", "settings": "Settings",
            "back": "Back", "estimatedTime": "Estimated time",
            "planJourneyTitle": "Plan Your Rail Journey", "planJourneySubtitle": "Enter where you are going and customise your trip", "searchTrains": "Search Trains", "where": "Where", "origin": "Origin", "destination": "Destination",
            "typeDeparture": "Type departure station", "typeArrival": "Type arrival station", "when": "When", "single": "Single", "returnLabel": "Return", "openReturn": "Open Return",
            "travelDates": "Travel Dates", "outbound": "Outbound", "notRequired": "Not required", "outboundDate": "Outbound Date", "returnDate": "Return Date", "returnDateOptional": "Return Date (optional guidance)",
            "localOperators": "Local Operators (NOC)", "passengers": "Passengers", "railcard": "Railcard",
            "personalData": "Personal Data", "fullName": "Full Name", "email": "Email", "phone": "Phone", "appearance": "Appearance", "theme": "Theme", "dark": "Dark", "light": "Light", "system": "System", "language": "Language",
            "noTrips": "No trips to show", "noNotifications": "No notifications to show", "noStops": "No stops to show",
            "locatingNearestStop": "Locating nearest stop...", "appleWallet": "Apple Wallet", "ok": "OK",
            "currentLocation": "Current Location", "walletUnavailable": "This device cannot add passes to Apple Wallet.", "walletInvalidPass": "Could not read this pass. Please choose a valid signed .pkpass file.", "walletNoPass": "No pass selected.",
            "viewLaterDepartures": "View later departures", "due": "Due", "atTheStation": "at the station",
            "train": "Train", "bus": "Bus", "taxi": "Taxi", "walk": "Walk", "trainStage": "Train Stage", "busStage": "Bus Stage", "taxiStage": "Taxi Stage", "walkStage": "Walk Stage"
        ],
        .es: [
            "home": "Inicio", "map": "Mapa", "tickets": "Billetes", "account": "Cuenta",
            "welcomeSplash": "Bienvenido a la aplicación de transporte", "welcomeBack": "Bienvenido de nuevo",
            "searchAllTrains": "Buscar todos los trenes", "recommended": "Recomendado", "book": "Reservar",
            "station": "ESTACIÓN", "stop": "PARADA", "away": "de distancia", "directions": "Direcciones", "departures": "Salidas",
            "find": "Buscar", "route": "ruta", "segments": "Tramos", "min": "min",
            "yourTicket": "Tu billete", "readyToScan": "Listo para escanear", "getDirections": "Obtener direcciones", "addToAppleWallet": "Añadir a Apple Wallet",
            "studentAccount": "Cuenta de estudiante", "quickActions": "Acciones rápidas", "savedTrips": "Viajes guardados", "savedStops": "Paradas guardadas", "notifications": "Notificaciones", "settings": "Ajustes",
            "back": "Volver", "estimatedTime": "Tiempo estimado",
            "planJourneyTitle": "Planifica tu viaje en tren", "planJourneySubtitle": "Indica a dónde vas y personaliza tu viaje", "searchTrains": "Buscar trenes", "where": "Dónde", "origin": "Origen", "destination": "Destino",
            "typeDeparture": "Escribe estación de salida", "typeArrival": "Escribe estación de llegada", "when": "Cuándo", "single": "Solo ida", "returnLabel": "Ida y vuelta", "openReturn": "Vuelta abierta",
            "travelDates": "Fechas de viaje", "outbound": "Ida", "notRequired": "No requerido", "outboundDate": "Fecha de ida", "returnDate": "Fecha de vuelta", "returnDateOptional": "Fecha de vuelta (opcional)",
            "localOperators": "Operadores locales (NOC)", "passengers": "Pasajeros", "railcard": "Railcard",
            "personalData": "Datos personales", "fullName": "Nombre completo", "email": "Correo", "phone": "Teléfono", "appearance": "Apariencia", "theme": "Tema", "dark": "Oscuro", "light": "Claro", "system": "Sistema", "language": "Idioma",
            "noTrips": "No hay viajes para mostrar", "noNotifications": "No hay notificaciones para mostrar", "noStops": "No hay paradas para mostrar",
            "locatingNearestStop": "Buscando parada más cercana...", "appleWallet": "Apple Wallet", "ok": "OK",
            "currentLocation": "Ubicación actual", "walletUnavailable": "Este dispositivo no puede añadir pases a Apple Wallet.", "walletInvalidPass": "No se pudo leer el pase. Selecciona un .pkpass válido y firmado.", "walletNoPass": "No se ha seleccionado ningún pase.",
            "viewLaterDepartures": "Ver salidas posteriores", "due": "Inminente", "atTheStation": "en la estación",
            "train": "Tren", "bus": "Bus", "taxi": "Taxi", "walk": "Andando", "trainStage": "Etapa de tren", "busStage": "Etapa de bus", "taxiStage": "Etapa de taxi", "walkStage": "Etapa a pie"
        ],
        .fr: [
            "home": "Accueil", "map": "Carte", "tickets": "Billets", "account": "Compte",
            "welcomeSplash": "Bienvenue dans l'application de transport", "welcomeBack": "Bon retour",
            "searchAllTrains": "Rechercher tous les trains", "recommended": "Recommandé", "book": "Réserver",
            "station": "GARE", "stop": "ARRÊT", "away": "de distance", "directions": "Itinéraire", "departures": "Départs",
            "find": "Trouver", "route": "trajet", "segments": "Segments", "min": "min",
            "yourTicket": "Votre billet", "readyToScan": "Prêt à scanner", "getDirections": "Obtenir l'itinéraire", "addToAppleWallet": "Ajouter à Apple Wallet",
            "studentAccount": "Compte étudiant", "quickActions": "Actions rapides", "savedTrips": "Trajets enregistrés", "savedStops": "Arrêts enregistrés", "notifications": "Notifications", "settings": "Paramètres",
            "back": "Retour", "estimatedTime": "Temps estimé",
            "planJourneyTitle": "Planifiez votre voyage en train", "planJourneySubtitle": "Entrez votre destination et personnalisez votre trajet", "searchTrains": "Rechercher des trains", "where": "Où", "origin": "Origine", "destination": "Destination",
            "typeDeparture": "Saisir la gare de départ", "typeArrival": "Saisir la gare d'arrivée", "when": "Quand", "single": "Aller simple", "returnLabel": "Aller-retour", "openReturn": "Retour ouvert",
            "travelDates": "Dates de voyage", "outbound": "Aller", "notRequired": "Non requis", "outboundDate": "Date aller", "returnDate": "Date retour", "returnDateOptional": "Date retour (optionnelle)",
            "localOperators": "Opérateurs locaux (NOC)", "passengers": "Passagers", "railcard": "Railcard",
            "personalData": "Données personnelles", "fullName": "Nom complet", "email": "E-mail", "phone": "Téléphone", "appearance": "Apparence", "theme": "Thème", "dark": "Sombre", "light": "Clair", "system": "Système", "language": "Langue",
            "noTrips": "Aucun trajet à afficher", "noNotifications": "Aucune notification à afficher", "noStops": "Aucun arrêt à afficher",
            "locatingNearestStop": "Recherche de l'arrêt le plus proche...", "appleWallet": "Apple Wallet", "ok": "OK",
            "currentLocation": "Position actuelle", "walletUnavailable": "Cet appareil ne peut pas ajouter de pass à Apple Wallet.", "walletInvalidPass": "Impossible de lire ce pass. Veuillez choisir un fichier .pkpass signé valide.", "walletNoPass": "Aucun pass sélectionné.",
            "viewLaterDepartures": "Voir les départs suivants", "due": "Imminent", "atTheStation": "à la station",
            "train": "Train", "bus": "Bus", "taxi": "Taxi", "walk": "Marche", "trainStage": "Étape train", "busStage": "Étape bus", "taxiStage": "Étape taxi", "walkStage": "Étape à pied"
        ],
        .de: [
            "home": "Start", "map": "Karte", "tickets": "Tickets", "account": "Konto",
            "welcomeSplash": "Willkommen in der Transport-App", "welcomeBack": "Willkommen zurück",
            "searchAllTrains": "Alle Züge suchen", "recommended": "Empfohlen", "book": "Buchen",
            "station": "BAHNHOF", "stop": "HALTESTELLE", "away": "entfernt", "directions": "Route", "departures": "Abfahrten",
            "find": "Finde", "route": "Route", "segments": "Abschnitte", "min": "Min",
            "yourTicket": "Ihr Ticket", "readyToScan": "Scanbereit", "getDirections": "Route anzeigen", "addToAppleWallet": "Zu Apple Wallet hinzufügen",
            "studentAccount": "Studentenkonto", "quickActions": "Schnellaktionen", "savedTrips": "Gespeicherte Reisen", "savedStops": "Gespeicherte Halte", "notifications": "Benachrichtigungen", "settings": "Einstellungen",
            "back": "Zurück", "estimatedTime": "Geschätzte Zeit",
            "planJourneyTitle": "Plane deine Zugreise", "planJourneySubtitle": "Gib dein Ziel ein und passe die Reise an", "searchTrains": "Züge suchen", "where": "Wohin", "origin": "Start", "destination": "Ziel",
            "typeDeparture": "Abfahrtsbahnhof eingeben", "typeArrival": "Ankunftsbahnhof eingeben", "when": "Wann", "single": "Einfach", "returnLabel": "Hin und zurück", "openReturn": "Offene Rückfahrt",
            "travelDates": "Reisedaten", "outbound": "Hin", "notRequired": "Nicht erforderlich", "outboundDate": "Hinreisedatum", "returnDate": "Rückreisedatum", "returnDateOptional": "Rückreisedatum (optional)",
            "localOperators": "Lokale Betreiber (NOC)", "passengers": "Passagiere", "railcard": "Railcard",
            "personalData": "Persönliche Daten", "fullName": "Vollständiger Name", "email": "E-Mail", "phone": "Telefon", "appearance": "Darstellung", "theme": "Design", "dark": "Dunkel", "light": "Hell", "system": "System", "language": "Sprache",
            "noTrips": "Keine Reisen vorhanden", "noNotifications": "Keine Benachrichtigungen vorhanden", "noStops": "Keine Haltestellen vorhanden",
            "locatingNearestStop": "Nächste Haltestelle wird gesucht...", "appleWallet": "Apple Wallet", "ok": "OK",
            "currentLocation": "Aktueller Standort", "walletUnavailable": "Dieses Gerät kann keine Pässe zu Apple Wallet hinzufügen.", "walletInvalidPass": "Dieser Pass konnte nicht gelesen werden. Bitte eine gültige signierte .pkpass-Datei wählen.", "walletNoPass": "Kein Pass ausgewählt.",
            "viewLaterDepartures": "Spätere Abfahrten anzeigen", "due": "Sofort", "atTheStation": "an der Station",
            "train": "Zug", "bus": "Bus", "taxi": "Taxi", "walk": "Zu Fuß", "trainStage": "Zugabschnitt", "busStage": "Busabschnitt", "taxiStage": "Taxiabschnitt", "walkStage": "Fußweg"
        ],
        .zh: [
            "home": "首页", "map": "地图", "tickets": "车票", "account": "账户",
            "welcomeSplash": "欢迎使用交通应用", "welcomeBack": "欢迎回来",
            "searchAllTrains": "搜索所有火车", "recommended": "推荐", "book": "预订",
            "station": "车站", "stop": "站点", "away": "距离", "directions": "路线", "departures": "发车",
            "find": "查找", "route": "路线", "segments": "路段", "min": "分钟",
            "yourTicket": "您的车票", "readyToScan": "准备扫码", "getDirections": "获取路线", "addToAppleWallet": "添加到 Apple Wallet",
            "studentAccount": "学生账户", "quickActions": "快捷操作", "savedTrips": "已保存行程", "savedStops": "已保存站点", "notifications": "通知", "settings": "设置",
            "back": "返回", "estimatedTime": "预计时间",
            "planJourneyTitle": "规划你的火车行程", "planJourneySubtitle": "输入目的地并自定义行程", "searchTrains": "搜索火车", "where": "出行地点", "origin": "出发地", "destination": "目的地",
            "typeDeparture": "输入出发站", "typeArrival": "输入到达站", "when": "时间", "single": "单程", "returnLabel": "往返", "openReturn": "开放返程",
            "travelDates": "出行日期", "outbound": "去程", "notRequired": "不需要", "outboundDate": "去程日期", "returnDate": "返程日期", "returnDateOptional": "返程日期（可选）",
            "localOperators": "本地运营商 (NOC)", "passengers": "乘客", "railcard": "Railcard",
            "personalData": "个人资料", "fullName": "姓名", "email": "邮箱", "phone": "电话", "appearance": "外观", "theme": "主题", "dark": "深色", "light": "浅色", "system": "系统", "language": "语言",
            "noTrips": "没有可显示的行程", "noNotifications": "没有可显示的通知", "noStops": "没有可显示的站点",
            "locatingNearestStop": "正在定位最近站点...", "appleWallet": "Apple Wallet", "ok": "确定",
            "currentLocation": "当前位置", "walletUnavailable": "此设备无法将票券添加到 Apple Wallet。", "walletInvalidPass": "无法读取该票券，请选择有效且已签名的 .pkpass 文件。", "walletNoPass": "未选择票券。",
            "viewLaterDepartures": "查看稍后班次", "due": "即将到达", "atTheStation": "已到站",
            "train": "火车", "bus": "公交", "taxi": "出租车", "walk": "步行", "trainStage": "火车阶段", "busStage": "公交阶段", "taxiStage": "出租车阶段", "walkStage": "步行阶段"
        ]
    ]
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
    let language: AppLanguage

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
                        Text(L.text(.back, lang: language))
                            .font(.headline)
                            .foregroundStyle(.white)
                    }
                    Spacer()
                }

                Text(plan.mode.stageTitle(in: language))
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
                    Text(L.text(.estimatedTime, lang: language))
                        .foregroundStyle(.secondary)
                    Spacer()
                    Text("\(plan.durationMins) \(L.text(.min, lang: language))")
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
    @Environment(\.colorScheme) private var colorScheme
    let stations: [Station]
    let language: AppLanguage
    @Binding var savedTrips: [SavedTrip]
    @State private var originId: Int?
    @State private var destinationId: Int?
    @State private var originQuery = ""
    @State private var destinationQuery = ""
    @State private var tripType: TripType = .single
    @State private var outboundDate: Date = .now
    @State private var returnDate: Date = Calendar.current.date(byAdding: .day, value: 1, to: .now) ?? .now
    @State private var passengerCount: Int = 1
    @State private var selectedRailcard: String
    @FocusState private var focusedField: SearchField?

    init(stations: [Station], language: AppLanguage, savedTrips: Binding<[SavedTrip]>) {
        self.stations = stations
        self.language = language
        self._savedTrips = savedTrips
        _selectedRailcard = State(initialValue: RailcardOption.options(for: language).first ?? "No Railcard")
    }

    private var usesLightPalette: Bool {
        colorScheme == .light
    }

    private var primaryTextColor: Color {
        usesLightPalette ? .black : .white
    }

    private var secondaryTextColor: Color {
        usesLightPalette ? Color.black.opacity(0.60) : .secondary
    }

    private var cardFillColor: Color {
        usesLightPalette ? Color.white : Color.white.opacity(0.08)
    }

    private var cardStrokeColor: Color {
        usesLightPalette ? Color.black.opacity(0.10) : Color.white.opacity(0.06)
    }

    private var fieldFillColor: Color {
        usesLightPalette ? Color.black.opacity(0.06) : Color.white.opacity(0.06)
    }

    private var hasValidWhere: Bool {
        guard let originId, let destinationId else { return false }
        return originId != destinationId
    }

    var body: some View {
        ZStack {
            LinearGradient(
                colors: usesLightPalette
                    ? [Color.white, Color(red: 0.95, green: 0.96, blue: 0.98)]
                    : [Color(red: 0.02, green: 0.09, blue: 0.20), Color.black],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            ScrollView {
                VStack(spacing: 16) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(L.text(.planJourneyTitle, lang: language))
                            .font(.custom("AvenirNext-Bold", size: 28))
                            .foregroundStyle(primaryTextColor)
                        Text(L.text(.planJourneySubtitle, lang: language))
                            .font(.custom("AvenirNext-Regular", size: 14))
                            .foregroundStyle(secondaryTextColor)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)

                    whereCard
                    localOperatorsCard

                    if hasValidWhere {
                        savedRouteCard
                        whenCard
                        travelDatesCard
                        passengersCard
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
                .padding(.bottom, 26)
            }
        }
        .navigationTitle(L.text(.searchTrains, lang: language))
        .navigationBarTitleDisplayMode(.inline)
    }

    private var whereCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(L.text(.where, lang: language))
                .font(.custom("AvenirNext-DemiBold", size: 20))
                .foregroundStyle(primaryTextColor)

            stationSearchField(
                title: L.text(.origin, lang: language),
                placeholder: L.text(.typeDeparture, lang: language),
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
                title: L.text(.destination, lang: language),
                placeholder: L.text(.typeArrival, lang: language),
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
            Text(L.text(.when, lang: language))
                .font(.custom("AvenirNext-DemiBold", size: 20))
                .foregroundStyle(primaryTextColor)

            HStack(spacing: 8) {
                ForEach(TripType.allCases, id: \.self) { type in
                    Button {
                        tripType = type
                    } label: {
                        Text(type.shortTitle(in: language))
                            .font(.custom("AvenirNext-DemiBold", size: 13))
                            .foregroundStyle(tripType == type ? .white : secondaryTextColor)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .background(
                                RoundedRectangle(cornerRadius: 10)
                                    .fill(tripType == type ? Color.blue : fieldFillColor)
                            )
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(14)
        .background(sectionCard)
    }

    private var savedRouteCard: some View {
        let saved = isRouteSaved
        return HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(routeTitle)
                    .font(.custom("AvenirNext-DemiBold", size: 16))
                    .foregroundStyle(primaryTextColor)
                Text(routeSubtitle)
                    .font(.custom("AvenirNext-Medium", size: 12))
                    .foregroundStyle(secondaryTextColor)
            }

            Spacer()

            Button {
                toggleSavedRoute()
            } label: {
                Image(systemName: saved ? "star.fill" : "star")
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(saved ? .yellow : primaryTextColor)
                    .frame(width: 34, height: 34)
                    .background(
                        RoundedRectangle(cornerRadius: 10)
                            .fill(fieldFillColor)
                    )
            }
            .buttonStyle(.plain)
        }
        .padding(14)
        .background(sectionCard)
    }

    private var travelDatesCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(L.text(.travelDates, lang: language))
                .font(.custom("AvenirNext-DemiBold", size: 20))
                .foregroundStyle(primaryTextColor)

            HStack(spacing: 8) {
                dateSelectionBox(title: L.text(.outbound, lang: language), value: formattedDate(outboundDate))
                dateSelectionBox(
                    title: L.text(.returnLabel, lang: language),
                    value: tripType == .single ? L.text(.notRequired, lang: language) : formattedDate(returnDate)
                )
            }

            DatePicker(
                L.text(.outboundDate, lang: language),
                selection: $outboundDate,
                in: Date()...,
                displayedComponents: .date
            )
            .datePickerStyle(.graphical)
            .tint(.blue)
            .onChange(of: outboundDate) { _, newDate in
                if returnDate < newDate {
                    returnDate = Calendar.current.date(byAdding: .day, value: 1, to: newDate) ?? newDate
                }
            }

            if tripType != .single {
                DatePicker(
                    tripType == .openReturn ? L.text(.returnDateOptional, lang: language) : L.text(.returnDate, lang: language),
                    selection: $returnDate,
                    in: outboundDate...,
                    displayedComponents: .date
                )
                .datePickerStyle(.graphical)
                .tint(.blue)
            }
        }
        .padding(14)
        .background(sectionCard)
    }

    private var localOperatorsCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(L.text(.localOperators, lang: language))
                .font(.custom("AvenirNext-DemiBold", size: 18))
                .foregroundStyle(primaryTextColor)
            Text("ARCT, BLAC, KLCO, SCCU, SCMY, NUTT")
                .font(.custom("AvenirNext-Medium", size: 14))
                .foregroundStyle(secondaryTextColor)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(sectionCard)
    }

    private var passengersCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(L.text(.passengers, lang: language))
                .font(.custom("AvenirNext-DemiBold", size: 20))
                .foregroundStyle(primaryTextColor)

            HStack {
                Text(L.passengerCount(passengerCount, lang: language))
                    .font(.custom("AvenirNext-Medium", size: 16))
                    .foregroundStyle(primaryTextColor)
                Spacer()
                Stepper("", value: $passengerCount, in: 1...12)
                    .labelsHidden()
            }
            .padding(12)
            .background(fieldCard)

            VStack(alignment: .leading, spacing: 8) {
                Text(L.text(.railcard, lang: language))
                    .font(.custom("AvenirNext-Medium", size: 14))
                    .foregroundStyle(secondaryTextColor)

                Picker(L.text(.railcard, lang: language), selection: $selectedRailcard) {
                    ForEach(RailcardOption.options(for: language), id: \.self) { railcard in
                        Text(railcard).tag(railcard)
                    }
                }
                .pickerStyle(.menu)
                .tint(primaryTextColor)
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
                .foregroundStyle(secondaryTextColor)

            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass")
                    .foregroundStyle(.blue)
                TextField(placeholder, text: text)
                    .focused($focusedField, equals: field)
                    .textInputAutocapitalization(.words)
                    .autocorrectionDisabled(true)
                    .foregroundStyle(primaryTextColor)
            }
            .padding(12)
            .background(fieldCard)
        }
    }

    private var originStation: Station? {
        stations.first(where: { $0.id == originId })
    }

    private var destinationStation: Station? {
        stations.first(where: { $0.id == destinationId })
    }

    private var routeTitle: String {
        let origin = originStation?.name ?? L.text(.origin, lang: language)
        let destination = destinationStation?.name ?? L.text(.destination, lang: language)
        return "\(origin) → \(destination)"
    }

    private var routeSubtitle: String {
        "\(formattedDate(outboundDate)) • \(L.passengerCount(passengerCount, lang: language))"
    }

    private var isRouteSaved: Bool {
        savedTrips.contains(where: { $0.title == routeTitle })
    }

    private func toggleSavedRoute() {
        if let idx = savedTrips.firstIndex(where: { $0.title == routeTitle }) {
            savedTrips.remove(at: idx)
        } else {
            savedTrips.insert(
                SavedTrip(title: routeTitle, subtitle: routeSubtitle),
                at: 0
            )
        }
    }

    private func dateSelectionBox(title: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.custom("AvenirNext-Medium", size: 13))
                .foregroundStyle(secondaryTextColor)
            Text(value)
                .font(.custom("AvenirNext-DemiBold", size: 15))
                .foregroundStyle(primaryTextColor)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(10)
        .background(fieldCard)
    }

    private func formattedDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "EEE, d MMM yyyy"
        return formatter.string(from: date)
    }

    private func suggestionsList(_ items: [Station], onSelect: @escaping (Station) -> Void) -> some View {
        VStack(spacing: 0) {
            ForEach(items.prefix(6), id: \.id) { station in
                Button {
                    onSelect(station)
                } label: {
                    HStack {
                        Text(station.name)
                            .foregroundStyle(primaryTextColor)
                        Spacer()
                        Text(station.code)
                            .font(.caption)
                            .foregroundStyle(secondaryTextColor)
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
                .fill(usesLightPalette ? Color.white : Color.black.opacity(0.28))
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
            .fill(cardFillColor)
            .overlay(
                RoundedRectangle(cornerRadius: 18)
                    .stroke(cardStrokeColor, lineWidth: 1)
            )
    }

    private var fieldCard: some View {
        RoundedRectangle(cornerRadius: 12)
            .fill(fieldFillColor)
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

    func shortTitle(in language: AppLanguage) -> String {
        switch self {
        case .single: return L.text(.single, lang: language)
        case .return: return L.text(.returnLabel, lang: language)
        case .openReturn: return L.text(.openReturn, lang: language)
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

    static func options(for language: AppLanguage) -> [String] {
        switch language {
        case .en:
            return [
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
        case .es:
            return [
                "Sin Railcard",
                "Railcard 16-25",
                "Railcard 26-30",
                "Railcard Senior",
                "Railcard Two Together",
                "Railcard Family & Friends",
                "Railcard Personas con Discapacidad",
                "16-17 Saver",
                "Railcard Veterans",
                "Railcard Network"
            ]
        case .fr:
            return [
                "Sans Railcard",
                "Railcard 16-25",
                "Railcard 26-30",
                "Railcard Senior",
                "Railcard Two Together",
                "Railcard Family & Friends",
                "Railcard Personnes handicapées",
                "16-17 Saver",
                "Railcard Veterans",
                "Railcard Network"
            ]
        case .de:
            return [
                "Keine Railcard",
                "Railcard 16-25",
                "Railcard 26-30",
                "Senior Railcard",
                "Two Together Railcard",
                "Family & Friends Railcard",
                "Railcard für Menschen mit Behinderung",
                "16-17 Saver",
                "Veterans Railcard",
                "Network Railcard"
            ]
        case .zh:
            return [
                "无 Railcard",
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
    }
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

    func title(in language: AppLanguage) -> String {
        switch self {
        case .train: return L.text(.train, lang: language)
        case .bus: return L.text(.bus, lang: language)
        case .taxi: return L.text(.taxi, lang: language)
        case .walk: return L.text(.walk, lang: language)
        }
    }

    func stageTitle(in language: AppLanguage) -> String {
        switch self {
        case .train: return L.text(.trainStage, lang: language)
        case .bus: return L.text(.busStage, lang: language)
        case .taxi: return L.text(.taxiStage, lang: language)
        case .walk: return L.text(.walkStage, lang: language)
        }
    }
}

private enum WalkField {
    case from
    case to
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

private struct MapArrival: Identifiable {
    let id = UUID()
    let service: String
    let destination: String
    let operatorName: String
    let etaMinutes: Double
    let color: Color
    let mode: TransportMode
}

private struct HomeExperience: Identifiable {
    let id = UUID()
    let title: String
    let location: String
    let price: String
    let imageURL: URL?

    static let mock: [HomeExperience] = [
        HomeExperience(
            title: "Lake District Cruise",
            location: "Windermere",
            price: "£45/person",
            imageURL: URL(string: "https://images.unsplash.com/photo-1501785888041-af3ef285b470?w=1200")
        ),
        HomeExperience(
            title: "Historic Lancaster Walk",
            location: "Lancaster",
            price: "£18/person",
            imageURL: URL(string: "https://images.unsplash.com/photo-1469474968028-56623f02e42e?w=1200")
        ),
        HomeExperience(
            title: "Blackpool Tower Day",
            location: "Blackpool",
            price: "£29/person",
            imageURL: URL(string: "https://images.unsplash.com/photo-1449824913935-59a10b8d2000?w=1200")
        )
    ]
}

private struct SavedTripsScreen: View {
    let trips: [SavedTrip]
    let language: AppLanguage

    var body: some View {
        List {
            if trips.isEmpty {
                Text(L.text(.noTrips, lang: language))
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
        .navigationTitle(L.text(.savedTrips, lang: language))
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct NotificationsScreen: View {
    let notifications: [AccountNotification]
    let language: AppLanguage

    var body: some View {
        List {
            if notifications.isEmpty {
                Text(L.text(.noNotifications, lang: language))
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
        .navigationTitle(L.text(.notifications, lang: language))
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct SavedStopsScreen: View {
    let stops: [SavedStop]
    let language: AppLanguage

    var body: some View {
        List {
            if stops.isEmpty {
                Text(L.text(.noStops, lang: language))
                    .foregroundStyle(.secondary)
            } else {
                ForEach(stops) { stop in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(stop.name)
                            .font(.headline)
                        Text("\(stop.code) • \(stop.mode.title(in: language)) • \(stop.distanceText)")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 6)
                }
            }
        }
        .navigationTitle(L.text(.savedStops, lang: language))
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct SettingsScreen: View {
    @Environment(\.colorScheme) private var systemColorScheme
    @Binding var preferredAppearance: String
    @Binding var appLanguageRawValue: String
    @Binding var fullName: String
    @Binding var email: String
    @Binding var phone: String

    private var language: AppLanguage {
        AppLanguage(rawValue: appLanguageRawValue) ?? .en
    }

    private var usesLightPalette: Bool {
        switch preferredAppearance {
        case "light":
            return true
        case "dark":
            return false
        default:
            return systemColorScheme == .light
        }
    }

    private var primaryTextColor: Color {
        usesLightPalette ? .black : .white
    }

    var body: some View {
        ZStack {
            LinearGradient(
                colors: usesLightPalette
                    ? [Color.white, Color(red: 0.95, green: 0.96, blue: 0.98)]
                    : [Color(red: 0.03, green: 0.11, blue: 0.28), Color.black],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            ScrollView {
                VStack(spacing: 14) {
                    settingsSection(title: L.text(.personalData, lang: language), icon: "person.text.rectangle.fill") {
                        settingsField(L.text(.fullName, lang: language), text: $fullName)
                        settingsField(L.text(.email, lang: language), text: $email)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled(true)
                        settingsField(L.text(.phone, lang: language), text: $phone)
                    }

                    settingsSection(title: L.text(.language, lang: language), icon: "globe") {
                        Picker(L.text(.language, lang: language), selection: $appLanguageRawValue) {
                            ForEach(AppLanguage.allCases, id: \.rawValue) { option in
                                Text(option.displayName).tag(option.rawValue)
                            }
                        }
                        .tint(primaryTextColor)
                        .padding(12)
                        .background(
                            RoundedRectangle(cornerRadius: 12)
                                .fill(usesLightPalette ? Color.black.opacity(0.06) : Color.white.opacity(0.06))
                        )
                    }

                    settingsSection(title: L.text(.appearance, lang: language), icon: "paintbrush.pointed.fill") {
                        Picker(L.text(.theme, lang: language), selection: $preferredAppearance) {
                            Text(L.text(.dark, lang: language)).tag("dark")
                            Text(L.text(.light, lang: language)).tag("light")
                            Text(L.text(.system, lang: language)).tag("system")
                        }
                        .pickerStyle(.segmented)
                        .tint(.blue)
                    }
                }
                .padding(.horizontal, 14)
                .padding(.top, 12)
                .padding(.bottom, 24)
            }
        }
        .navigationTitle(L.text(.settings, lang: language))
        .navigationBarTitleDisplayMode(.inline)
    }

    private func settingsSection<Content: View>(title: String, icon: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 10) {
                Image(systemName: icon)
                    .font(.headline)
                    .foregroundStyle(.blue)
                    .frame(width: 26, height: 26)
                    .background(Circle().fill(Color.white.opacity(0.08)))
                Text(title)
                    .font(.headline.weight(.semibold))
                    .foregroundStyle(primaryTextColor)
            }
            content()
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(usesLightPalette ? Color.white.opacity(0.95) : Color.white.opacity(0.08))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(usesLightPalette ? Color.black.opacity(0.10) : Color.white.opacity(0.06), lineWidth: 1)
                )
        )
    }

    private func settingsField(_ title: String, text: Binding<String>) -> some View {
        TextField(title, text: text)
            .foregroundStyle(primaryTextColor)
            .padding(12)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(usesLightPalette ? Color.black.opacity(0.06) : Color.white.opacity(0.06))
            )
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

private struct DirectionsSheet: View {
    @Environment(\.dismiss) private var dismiss
    let station: Station
    @Binding var mode: TransportMode
    let userLocation: CLLocationCoordinate2D?
    @State private var route: MKRoute?
    @State private var fallbackLine: MKPolyline?
    @State private var mapPosition: MapCameraPosition = .automatic

    var body: some View {
        ZStack(alignment: .top) {
            Map(position: $mapPosition) {
                if let route {
                    MapPolyline(route.polyline)
                        .stroke(.blue, lineWidth: 5)
                }
                if route == nil, let fallbackLine {
                    MapPolyline(fallbackLine)
                        .stroke(.blue.opacity(0.7), style: StrokeStyle(lineWidth: 4, lineCap: .round, dash: [8, 6]))
                }
                if let lat = station.latitude, let lon = station.longitude {
                    Marker(station.name, coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lon))
                }
                if userLocation != nil {
                    UserAnnotation()
                }
            }
            .mapStyle(.standard(elevation: .realistic))
            .ignoresSafeArea()

            VStack(spacing: 12) {
                HStack {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark")
                            .font(.headline)
                            .foregroundStyle(.white)
                            .frame(width: 36, height: 36)
                            .background(Circle().fill(Color.black.opacity(0.6)))
                    }
                    Spacer()
                }

                HStack(spacing: 10) {
                    directionsModeButton(mode: .train)
                    directionsModeButton(mode: .bus)
                }
                .padding(10)
                .background(
                    RoundedRectangle(cornerRadius: 14)
                        .fill(Color.black.opacity(0.55))
                )

                Spacer()
            }
            .padding(.top, 10)
            .padding(.horizontal, 16)
        }
        .safeAreaInset(edge: .bottom) {
            directionsBottomCard
                .padding(.horizontal, 14)
                .padding(.bottom, 10)
        }
        .onAppear {
            Task { await calculateRoute() }
        }
        .onChange(of: mode) { _, _ in
            Task { await calculateRoute() }
        }
    }

    private func directionsModeButton(mode: TransportMode) -> some View {
        let selected = self.mode == mode
        return Button {
            self.mode = mode
        } label: {
            HStack(spacing: 8) {
                Image(systemName: mode == .train ? "tram.fill" : "bus.fill")
                Text(mode == .train ? "Train" : "Bus")
                    .font(.subheadline.weight(.semibold))
            }
            .foregroundStyle(selected ? .white : .white.opacity(0.7))
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .background(
                RoundedRectangle(cornerRadius: 10)
                    .fill(selected ? Color.blue.opacity(0.9) : Color.white.opacity(0.12))
            )
        }
        .buttonStyle(.plain)
    }

    private func calculateRoute() async {
        guard let lat = station.latitude, let lon = station.longitude else { return }

        let request = MKDirections.Request()
        if let userLocation {
            request.source = MKMapItem(placemark: MKPlacemark(coordinate: userLocation))
        }
        request.destination = MKMapItem(placemark: MKPlacemark(coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lon)))
        request.transportType = mode == .walk ? .walking : .transit

        do {
            let response = try await MKDirections(request: request).calculate()
            route = response.routes.first
            if let route {
                mapPosition = .rect(route.polyline.boundingMapRect)
                fallbackLine = nil
            }
        } catch {
            route = nil
            fallbackLine = fallbackPolyline(to: CLLocationCoordinate2D(latitude: lat, longitude: lon))
            if let fallbackLine {
                mapPosition = .rect(fallbackLine.boundingMapRect)
            }
        }
    }

    private func fallbackPolyline(to destination: CLLocationCoordinate2D) -> MKPolyline? {
        if let userLocation {
            return MKPolyline(coordinates: [userLocation, destination], count: 2)
        }
        let offset = CLLocationCoordinate2D(latitude: destination.latitude + 0.01, longitude: destination.longitude - 0.01)
        return MKPolyline(coordinates: [offset, destination], count: 2)
    }

    private var directionsBottomCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(station.name)
                .font(.headline.weight(.semibold))
                .foregroundStyle(.white)
            if let route {
                HStack {
                    Text(timeString(route.expectedTravelTime))
                    Spacer()
                    Text(distanceString(route.distance))
                }
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.white.opacity(0.9))
                Text(mode == .train ? "Transit (Train)" : "Transit (Bus)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else {
                Text("Calculating route…")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.black.opacity(0.75))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color.white.opacity(0.08), lineWidth: 1)
                )
        )
    }

    private func timeString(_ seconds: TimeInterval) -> String {
        let mins = Int(round(seconds / 60))
        if mins < 60 { return "\(mins) min" }
        let hrs = mins / 60
        let rem = mins % 60
        return "\(hrs) hr \(rem) min"
    }

    private func distanceString(_ meters: CLLocationDistance) -> String {
        if meters < 1000 { return "\(Int(meters)) m" }
        return String(format: "%.1f km", meters / 1000)
    }
}

private struct TrackSheet: View {
    @Environment(\.dismiss) private var dismiss
    let stations: [Station]
    let mapping: StationMapping
    @State private var mapPosition: MapCameraPosition = .automatic
    @State private var routePolyline: MKPolyline?
    @State private var trainCoordinate: CLLocationCoordinate2D?
    @State private var progress: Double = 0.0
    @State private var timer: Timer?
    @State private var pulse = false

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Map(position: $mapPosition) {
                if let routePolyline {
                    MapPolyline(routePolyline)
                        .stroke(Color.blue, lineWidth: 5)
                }
                ForEach(trackingStations, id: \.id) { station in
                    if let lat = station.latitude, let lon = station.longitude {
                        Annotation("", coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lon)) {
                            Circle()
                                .fill(Color.white)
                                .frame(width: 8, height: 8)
                                .overlay(
                                    Circle()
                                        .stroke(Color.blue.opacity(0.85), lineWidth: 2)
                                )
                        }
                    }
                }
                if let trainCoordinate {
                    Annotation("", coordinate: trainCoordinate) {
                        ZStack {
                            Circle()
                                .fill(Color.blue.opacity(0.25))
                                .frame(width: 44, height: 44)
                            Circle()
                                .fill(Color.blue)
                                .frame(width: 18, height: 18)
                                .overlay(
                                    Image(systemName: "tram.fill")
                                        .font(.caption2)
                                        .foregroundStyle(.white)
                                )
                        }
                    }
                }
            }
            .mapStyle(.standard(elevation: .realistic))
            .ignoresSafeArea()

            HStack(spacing: 12) {
                Button {
                    dismiss()
                } label: {
                    Image(systemName: "xmark")
                        .font(.headline)
                        .foregroundStyle(.white)
                        .frame(width: 36, height: 36)
                        .background(Circle().fill(Color.black.opacity(0.6)))
                }
                Spacer()
            }
            .padding(.top, 10)
            .padding(.horizontal, 16)

            trackSidebar
        }
        .onAppear {
            buildRoute()
            startTimer()
            withAnimation(.easeInOut(duration: 0.8).repeatForever(autoreverses: true)) {
                pulse = true
            }
        }
        .onDisappear {
            timer?.invalidate()
        }
    }

    private var trackingStops: [TrackStop] {
        let stops = trackingStations
        guard !stops.isEmpty else { return [] }
        let times = ["Now", "6 min", "14 min", "22 min", "35 min", "48 min"]
        let startIndex = max(min(currentStopIndex, stops.count - 1), 0)
        return Array(stops[startIndex...]).enumerated().map { idx, station in
            TrackStop(id: station.id, name: station.name, eta: times[min(idx, times.count - 1)])
        }
    }

    private var trackingStations: [Station] {
        let preferred = ["PRE", "LAN", "BLK", "PNR"]
        let byCode = preferred.compactMap { code in
            stations.first(where: { $0.code == code && $0.latitude != nil && $0.longitude != nil })
        }
        if byCode.count >= 2 { return byCode }
        return stations.filter { $0.latitude != nil && $0.longitude != nil }.prefix(4).map { $0 }
    }

    private var currentStopIndex: Int {
        let count = trackingStations.count
        guard count > 1 else { return 0 }
        let idx = Int(floor(progress * Double(count - 1)))
        return min(max(idx, 0), count - 2)
    }

    private var currentStation: Station? {
        let stations = trackingStations
        guard currentStopIndex < stations.count else { return nil }
        return stations[currentStopIndex]
    }

    private var nextStation: Station? {
        let stations = trackingStations
        let idx = currentStopIndex + 1
        guard idx < stations.count else { return nil }
        return stations[idx]
    }

    private var destinationStation: Station? {
        trackingStations.last
    }

    private func buildRoute() {
        let coords = trackingStations.compactMap { station -> CLLocationCoordinate2D? in
            guard let lat = station.latitude, let lon = station.longitude else { return nil }
            return CLLocationCoordinate2D(latitude: lat, longitude: lon)
        }
        guard coords.count >= 2 else { return }
        routePolyline = MKPolyline(coordinates: coords, count: coords.count)
        trainCoordinate = coords.first
        mapPosition = .rect(routePolyline?.boundingMapRect ?? MKMapRect.world)
    }

    private func startTimer() {
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 0.06, repeats: true) { _ in
            progress += 0.0018
            if progress > 1.0 { progress = 0.0 }
            if let routePolyline {
                trainCoordinate = coordinate(on: routePolyline, fraction: progress)
            }
        }
    }

    private func coordinate(on polyline: MKPolyline, fraction: Double) -> CLLocationCoordinate2D {
        let points = polyline.points()
        let count = polyline.pointCount
        guard count > 1 else { return polyline.coordinate }

        var distances: [CLLocationDistance] = []
        distances.reserveCapacity(count)
        distances.append(0)
        for i in 1..<count {
            let p0 = points[i - 1]
            let p1 = points[i]
            let d = p0.distance(to: p1)
            distances.append(distances[i - 1] + d)
        }
        let total = distances.last ?? 1
        let target = total * fraction
        for i in 1..<count {
            if distances[i] >= target {
                let prev = distances[i - 1]
                let seg = max(distances[i] - prev, 0.001)
                let t = (target - prev) / seg
                let p0 = points[i - 1]
                let p1 = points[i]
                let x = p0.x + (p1.x - p0.x) * t
                let y = p0.y + (p1.y - p0.y) * t
                return MKMapPoint(x: x, y: y).coordinate
            }
        }
        return points[count - 1].coordinate
    }

    private var trackSidebar: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(spacing: 8) {
                Circle()
                    .fill(Color.green)
                    .frame(width: 8, height: 8)
                    .overlay(
                        Circle()
                            .stroke(Color.green.opacity(0.6), lineWidth: 6)
                            .scaleEffect(pulse ? 1.2 : 0.6)
                            .opacity(pulse ? 0.0 : 0.8)
                    )
                Text("LIVE")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.green)
            }

            VStack(alignment: .leading, spacing: 6) {
                Text("Train 38")
                    .font(.headline.weight(.semibold))
                    .foregroundStyle(.white)
                Text("Avanti West Coast")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            VStack(alignment: .leading, spacing: 10) {
                if let currentStation {
                    HStack(spacing: 8) {
                        Image(systemName: "location.fill")
                            .font(.caption)
                            .foregroundStyle(.blue)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Current")
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                            Text(currentStation.name)
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(.white)
                        }
                    }
                }
                if let nextStation {
                    HStack(spacing: 8) {
                        Image(systemName: "arrowtriangle.right.fill")
                            .font(.caption)
                            .foregroundStyle(.cyan)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Next")
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                            Text(nextStation.name)
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(.white)
                        }
                    }
                }
                if let destinationStation {
                    HStack(spacing: 8) {
                        Image(systemName: "flag.checkered")
                            .font(.caption)
                            .foregroundStyle(.orange)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Destination")
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                            Text(destinationStation.name)
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(.white)
                        }
                    }
                }
            }

            Divider()
                .background(Color.white.opacity(0.12))

            Text("Upcoming stops")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)

            ForEach(trackingStops, id: \.id) { stop in
                HStack {
                    Text(stop.name)
                        .foregroundStyle(.white)
                        .font(.subheadline)
                    Spacer()
                    Text(stop.eta)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.cyan)
                }
            }

            Spacer(minLength: 8)
        }
        .padding(.vertical, 20)
        .padding(.horizontal, 14)
        .frame(width: 230, alignment: .topLeading)
        .frame(maxHeight: .infinity, alignment: .topLeading)
        .background(
            RoundedRectangle(cornerRadius: 20)
                .fill(Color.black.opacity(0.78))
                .overlay(
                    RoundedRectangle(cornerRadius: 20)
                        .stroke(Color.white.opacity(0.08), lineWidth: 1)
                )
        )
        .padding(.top, 70)
        .padding(.trailing, 12)
    }
}

private struct TrackStop: Identifiable {
    let id: Int
    let name: String
    let eta: String
}

#Preview {
    ContentView()
        .environmentObject(AppState())
}
