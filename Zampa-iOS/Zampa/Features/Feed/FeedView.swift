import SwiftUI
import Combine
import FirebaseFirestore
import CoreLocation

struct FeedView: View {
    var onNavigateToProfile: () -> Void = {}

    @EnvironmentObject var appState: AppState
    @EnvironmentObject var tourManager: TourManager
    @ObservedObject var localization = LocalizationManager.shared

    @State private var menus: [Menu] = []
    @State private var isLoading: Bool = false
    @State private var searchText: String = ""
    @State private var lastDoc: DocumentSnapshot? = nil
    @State private var canLoadMore: Bool = true
    @State private var selectedCuisine: String? = nil
    @State private var maxPrice: Double? = nil
    @State private var maxDistanceKm: Double? = nil
    @State private var onlyFavorites: Bool = false
    @State private var sortOption: SortOption = .distance
    @State private var showingFilters = false
    @State private var showingLocationPrompt = false
    @State private var onlyOpen: Bool = false
    @State private var offerType: String? = nil
    @State private var merchantMap: [String: Merchant] = [:]
    @State private var viewMode: FeedViewMode = .list
    @State private var presentedMenu: Menu? = nil
    @State private var presentedMerchantId: String? = nil

    /// Wrapper para usar `String` como item en `.sheet(item:)`.
    private struct IdentifiableId: Identifiable { let id: String }
    enum SortOption { case distance, price }
    enum FeedViewMode { case list, map }

    var hasClientSideFilters: Bool { maxDistanceKm != nil || onlyFavorites || onlyOpen || offerType != nil }

    var body: some View {
        NavigationView {
            VStack(spacing: 0) {

                // ── HEADER ──────────────────────────────────────────────
                HStack(alignment: .center) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(greeting)
                            .font(.custom("Sora-SemiBold", size: 11))
                            .foregroundColor(.appPrimary)
                            .kerning(1.5)
                        Text(greeting == localization.t("feed_good_evening") ? localization.t("feed_dinner_question") : localization.t("feed_lunch_question"))
                            .font(.custom("Sora-Bold", size: 24))
                            .foregroundColor(.appTextPrimary)
                    }
                    Spacer()
                    Image("Logo")
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .frame(height: 36)
                }
                .padding(.horizontal, 20)
                .padding(.top, 16)
                .padding(.bottom, 16)

                // ── SECTION HEADER ──────────────────────────────────────
                HStack {
                    Text(sortOption == .price ? localization.t("feed_by_price") : localization.t("feed_nearby"))
                        .font(.custom("Sora-Bold", size: 17))
                        .foregroundColor(.appTextPrimary)
                    Spacer()
                    if !menus.isEmpty {
                        Text("\(sortedMenus.count) \(localization.t("feed_found"))")
                            .font(.appCaption)
                            .foregroundColor(.appTextSecondary)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 14)

                // ── SORT + FILTER ────────────────────────────────────────
                HStack(spacing: 10) {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            // Sort pills solo en vista lista: el mapa muestra posición/distancia visualmente.
                            if viewMode == .list {
                                SortPill(title: localization.t("feed_distance"), icon: "location.fill", isSelected: sortOption == .distance) {
                                    sortOption = .distance
                                }
                                SortPill(title: localization.t("feed_price"), icon: "banknote", isSelected: sortOption == .price) {
                                    sortOption = .price
                                }
                            }
                        }
                        .padding(.horizontal, 20)
                    }
                    Button(action: {
                        viewMode = (viewMode == .list) ? .map : .list
                    }) {
                        Image(systemName: viewMode == .list ? "map.fill" : "list.bullet")
                            .padding(10)
                            .background(RoundedRectangle(cornerRadius: 10).fill(
                                viewMode == .map ? Color.appPrimary : Color.appInputBackground
                            ))
                            .foregroundColor(viewMode == .map ? .white : .appTextPrimary)
                    }
                    .tourTarget(.mapToggle)
                    Button(action: { showingFilters = true }) {
                        ZStack(alignment: .topTrailing) {
                            Image(systemName: "slider.horizontal.3")
                                .padding(10)
                                .background(RoundedRectangle(cornerRadius: 10).fill(Color.appInputBackground))
                                .foregroundColor(.appTextPrimary)
                            if filtersActive {
                                Circle()
                                    .fill(Color.appPrimary)
                                    .frame(width: 8, height: 8)
                                    .offset(x: -2, y: 2)
                            }
                        }
                    }
                    .tourTarget(.filterButton)
                    .padding(.trailing, 20)
                }
                .padding(.bottom, 12)

                // ── CONTENT ──────────────────────────────────────────────
                if isLoading && menus.isEmpty {
                    ProgressView(localization.t("feed_searching"))
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if sortedMenus.isEmpty {
                    VStack(spacing: 16) {
                        Image(systemName: "fork.knife")
                            .font(.custom("Sora-Regular", size: 60))
                            .foregroundColor(.appTextSecondary.opacity(0.3))
                        Text(localization.t("feed_no_results"))
                            .font(.appSubheadline)
                            .foregroundColor(.appTextSecondary)
                        Button(localization.t("feed_clear_filters")) {
                            selectedCuisine = nil
                            maxPrice = nil
                            maxDistanceKm = nil
                            onlyFavorites = false
                            onlyOpen = false
                            offerType = nil
                            loadMenus()
                        }
                        .foregroundColor(.appPrimary)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color.appBackground)
                } else if viewMode == .map {
                    FeedMapView(
                        menus: sortedMenus,
                        merchantMap: merchantMap,
                        userLocation: appState.locationManager.location,
                        onNavigateToDetail: { offerId in
                            presentedMenu = sortedMenus.first { $0.id == offerId }
                        },
                        onNavigateToMerchant: { merchantId in
                            presentedMerchantId = merchantId
                        }
                    )
                } else {
                    ScrollView {
                        LazyVStack(spacing: 0) {
                            ForEach(sortedMenus) { menu in
                                MenuCard(menu: menu, onMerchantLoaded: { id, merchant in
                                    merchantMap[id] = merchant
                                })
                                .padding(.horizontal, 16)
                                .padding(.vertical, 8)
                                .tourTarget(.feedCard, when: menu.id == sortedMenus.first?.id)
                                .tourTarget(.favoriteHint, when: menu.id == sortedMenus.first?.id)
                                .onAppear {
                                    if menu == menus.last && canLoadMore {
                                        loadMoreMenus()
                                    }
                                }
                            }
                            if isLoading {
                                HStack {
                                    Spacer()
                                    ProgressView()
                                    Spacer()
                                }
                                .padding()
                            }
                        }
                    }
                    .background(Color.appBackground)
                    .refreshable { await refreshMenus() }
                }
            }
            .background(Color.appBackground.ignoresSafeArea())
            .navigationBarHidden(true)
            .sheet(isPresented: $showingFilters) {
                FilterView(
                    selectedCuisine: selectedCuisine,
                    maxPrice: maxPrice ?? 30,
                    maxDistanceKm: maxDistanceKm,
                    onlyFavorites: onlyFavorites,
                    onlyOpen: onlyOpen,
                    offerType: offerType
                ) { cuisine, price, distance, favOnly, openOnly, type in
                    self.selectedCuisine = cuisine
                    self.maxPrice = price
                    self.maxDistanceKm = distance
                    self.onlyFavorites = favOnly
                    self.onlyOpen = openOnly
                    self.offerType = type
                    loadMenus()
                }
            }
            .onAppear {
                if menus.isEmpty {
                    loadMenus()
                } else {
                    backgroundRefresh()
                }
                // Prompt location permission if not yet determined
                if appState.locationManager.authorizationStatus == .notDetermined {
                    showingLocationPrompt = true
                }
            }
            .fullScreenCover(isPresented: $showingLocationPrompt) {
                LocationConfigView()
                    .environmentObject(appState)
            }
            .sheet(item: $presentedMenu) { menu in
                NavigationView {
                    MenuDetailView(menu: menu)
                        .environmentObject(appState)
                        .toolbar {
                            ToolbarItem(placement: .navigationBarTrailing) {
                                Button(action: { presentedMenu = nil }) {
                                    Image(systemName: "xmark.circle.fill")
                                        .foregroundColor(.appTextSecondary)
                                }
                            }
                        }
                }
            }
            .sheet(item: Binding(
                get: { presentedMerchantId.map { IdentifiableId(id: $0) } },
                set: { presentedMerchantId = $0?.id }
            )) { wrapper in
                NavigationView {
                    MerchantProfileView(merchantId: wrapper.id)
                        .environmentObject(appState)
                        .toolbar {
                            ToolbarItem(placement: .navigationBarTrailing) {
                                Button(action: { presentedMerchantId = nil }) {
                                    Image(systemName: "xmark.circle.fill")
                                        .foregroundColor(.appTextSecondary)
                                }
                            }
                        }
                }
            }
        }
    }

    private var greeting: String {
        let hour = Calendar.current.component(.hour, from: Date())
        if hour < 12 { return localization.t("feed_good_morning") }
        else if hour < 20 { return localization.t("feed_good_afternoon") }
        else { return localization.t("feed_good_evening") }
    }

    private var filtersActive: Bool {
        selectedCuisine != nil || (maxPrice != nil && maxPrice! < 100) || maxDistanceKm != nil || onlyFavorites || onlyOpen || offerType != nil
    }

    private var filteredMenus: [Menu] {
        guard !searchText.isEmpty else { return menus }
        return menus.filter {
            $0.title.localizedCaseInsensitiveContains(searchText) ||
            ($0.description?.localizedCaseInsensitiveContains(searchText) ?? false)
        }
    }

    private var sortedMenus: [Menu] {
        var result = filteredMenus

        // Apply user dietary preferences
        let prefs = appState.dietaryPreferences
        if !prefs.isEmpty {
            result = result.filter { prefs.allows($0) }
        }

        if onlyOpen {
            result = result.filter { menu in
                guard let m = merchantMap[menu.businessId] else { return true }
                return isOpenNow(schedule: m.schedule)
            }
        }
        if let type = offerType {
            result = result.filter { menu in
                if type == OfferTypes.ofertaPermanente {
                    return menu.isPermanent || menu.offerType == OfferTypes.ofertaPermanente
                }
                return menu.offerType == type
            }
        }
        if sortOption == .price {
            result.sort { $0.priceTotal < $1.priceTotal }
        } else if sortOption == .distance, let userLoc = appState.locationManager.location {
            result.sort { a, b in
                let distA = distanceForMenu(a, userLoc: userLoc)
                let distB = distanceForMenu(b, userLoc: userLoc)
                return distA < distB
            }
        }
        return result
    }

    private func isOpenNow(schedule: [ScheduleEntry]?) -> Bool {
        guard let schedule = schedule else { return false }
        let weekday = Calendar.current.component(.weekday, from: Date())
        let keys = ["sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"]
        let todayKey = keys[weekday - 1]
        guard let entry = schedule.first(where: { $0.day == todayKey }) else { return false }
        let fmt = DateFormatter()
        fmt.dateFormat = "HH:mm"
        let now = fmt.string(from: Date())
        return now >= entry.open && now <= entry.close
    }

    private func fetchMerchants(for menus: [Menu]) async -> [String: Merchant] {
        let ids = Set(menus.map { $0.businessId })
        let idsToFetch = ids.filter { merchantMap[$0] == nil }
        var result: [String: Merchant] = [:]
        await withTaskGroup(of: (String, Merchant?).self) { group in
            for id in idsToFetch {
                group.addTask {
                    let m = try? await FirebaseService.shared.getMerchantProfile(merchantId: id)
                    return (id, m)
                }
            }
            for await (id, merchant) in group {
                if let m = merchant { result[id] = m }
            }
        }
        return result
    }

    private func distanceForMenu(_ menu: Menu, userLoc: CLLocation) -> Double {
        guard let addr = merchantMap[menu.businessId]?.address else { return Double.greatestFiniteMagnitude }
        return userLoc.distance(from: CLLocation(latitude: addr.lat, longitude: addr.lng))
    }

    private func loadMenus() {
        isLoading = true
        lastDoc = nil
        canLoadMore = true

        Task {
            do {
                var favIds: Set<String> = []
                if onlyFavorites {
                    let favs = (try? await FirebaseService.shared.getFavorites()) ?? []
                    favIds = Set(favs.map { $0.businessId })
                }

                let result = try await MenuService.shared.getMenus(
                    limit: 100,
                    cuisineFilter: selectedCuisine,
                    maxPrice: maxPrice
                )
                var filtered = result.menus

                if onlyFavorites {
                    filtered = filtered.filter { favIds.contains($0.businessId) }
                }

                // Pre-fetch merchants for distance sorting
                let merchants = await fetchMerchants(for: filtered)

                if let maxDist = maxDistanceKm,
                   let userLoc = appState.locationManager.location {
                    filtered = filtered.filter { menu in
                        guard let addr = merchants[menu.businessId]?.address else { return false }
                        let mLoc = CLLocation(latitude: addr.lat, longitude: addr.lng)
                        return userLoc.distance(from: mLoc) / 1000.0 <= maxDist
                    }
                }

                await MainActor.run {
                    self.merchantMap.merge(merchants) { _, new in new }
                    self.menus = filtered
                    self.lastDoc = result.lastDoc
                    self.canLoadMore = result.menus.count == 100 && !hasClientSideFilters
                    self.isLoading = false
                }
            } catch {
                await MainActor.run { self.isLoading = false }
            }
        }
    }

    private func loadMoreMenus() {
        guard !isLoading && canLoadMore && !hasClientSideFilters else { return }
        isLoading = true

        Task {
            do {
                let result = try await MenuService.shared.getMenus(
                    limit: 100,
                    lastDocument: lastDoc,
                    cuisineFilter: selectedCuisine,
                    maxPrice: maxPrice
                )
                let merchants = await fetchMerchants(for: result.menus)
                await MainActor.run {
                    self.merchantMap.merge(merchants) { _, new in new }
                    self.menus.append(contentsOf: result.menus)
                    self.lastDoc = result.lastDoc
                    self.canLoadMore = result.menus.count == 100
                    self.isLoading = false
                }
            } catch {
                await MainActor.run { self.isLoading = false }
            }
        }
    }

    private func refreshMenus() async {
        do {
            var favIds: Set<String> = []
            if onlyFavorites {
                let favs = (try? await FirebaseService.shared.getFavorites()) ?? []
                favIds = Set(favs.map { $0.businessId })
            }

            let result = try await MenuService.shared.getMenus(
                limit: 100,
                cuisineFilter: selectedCuisine,
                maxPrice: maxPrice
            )
            var filtered = result.menus

            if onlyFavorites {
                filtered = filtered.filter { favIds.contains($0.businessId) }
            }

            let merchants = await fetchMerchants(for: filtered)

            if let maxDist = maxDistanceKm,
               let userLoc = appState.locationManager.location {
                filtered = filtered.filter { menu in
                    guard let addr = merchants[menu.businessId]?.address else { return false }
                    let mLoc = CLLocation(latitude: addr.lat, longitude: addr.lng)
                    return userLoc.distance(from: mLoc) / 1000.0 <= maxDist
                }
            }

            self.merchantMap.merge(merchants) { _, new in new }
            self.menus = filtered
            self.lastDoc = result.lastDoc
            self.canLoadMore = result.menus.count == 100 && !hasClientSideFilters
        } catch {}
    }

    private func backgroundRefresh() {
        Task {
            do {
                var favIds: Set<String> = []
                if onlyFavorites {
                    let favs = (try? await FirebaseService.shared.getFavorites()) ?? []
                    favIds = Set(favs.map { $0.businessId })
                }

                let result = try await MenuService.shared.getMenus(
                    limit: 100,
                    cuisineFilter: selectedCuisine,
                    maxPrice: maxPrice
                )
                var filtered = result.menus

                if onlyFavorites {
                    filtered = filtered.filter { favIds.contains($0.businessId) }
                }

                let merchants = await fetchMerchants(for: filtered)

                if let maxDist = maxDistanceKm,
                   let userLoc = appState.locationManager.location {
                    filtered = filtered.filter { menu in
                        guard let addr = merchants[menu.businessId]?.address else { return false }
                        let mLoc = CLLocation(latitude: addr.lat, longitude: addr.lng)
                        return userLoc.distance(from: mLoc) / 1000.0 <= maxDist
                    }
                }

                await MainActor.run {
                    self.merchantMap.merge(merchants) { _, new in new }
                    self.menus = filtered
                    self.lastDoc = result.lastDoc
                    self.canLoadMore = result.menus.count == 100 && !hasClientSideFilters
                }
            } catch {}
        }
    }
}

// MARK: - Sort Pill

struct SortPill: View {
    let title: String
    let icon: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.custom("Sora-Regular", size: 12))
                Text(title)
                    .font(.custom("Sora-SemiBold", size: 14))
                    .lineLimit(1)
            }
            .foregroundColor(isSelected ? .white : .appTextPrimary)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(
                RoundedRectangle(cornerRadius: 22)
                    .fill(isSelected ? Color.appPrimary : Color.appInputBackground)
            )
        }
    }
}

// MARK: - Inline Chip

struct InlineChip: View {
    let icon: String?
    let label: String
    let foreground: Color
    let background: Color

    var body: some View {
        HStack(spacing: 4) {
            if let icon = icon {
                Image(systemName: icon)
                    .font(.custom("Sora-Regular", size: 10))
            }
            Text(label)
                .font(.custom("Sora-SemiBold", size: 11))
                .lineLimit(1)
        }
        .foregroundColor(foreground)
        .padding(.horizontal, 8)
        .padding(.vertical, 5)
        .background(background)
        .cornerRadius(6)
    }
}

// MARK: - Menu Card

struct MenuCard: View {
    let menu: Menu
    @EnvironmentObject var appState: AppState
    @ObservedObject var localization = LocalizationManager.shared
    @State private var merchant: Merchant? = nil
    @State private var userLocation: CLLocation? = nil
    var onMerchantLoaded: ((String, Merchant) -> Void)? = nil

    var body: some View {
        NavigationLink(destination: MenuDetailView(menu: menu)) {
            VStack(alignment: .leading, spacing: 0) {

                // ── IMAGE ────────────────────────────────────────────────
                ZStack(alignment: .top) {
                    Group {
                        if let photoUrl = menu.photoUrls.first, let url = URL(string: photoUrl) {
                            CachedAsyncImage(url: url) { image in
                                image.resizable().aspectRatio(contentMode: .fill)
                            } placeholder: {
                                Rectangle().fill(Color.appInputBackground)
                            }
                        } else {
                            Rectangle().fill(Color.appInputBackground)
                        }
                    }
                    .frame(height: 190)
                    .clipped()

                    // TOP: offer type + price chip (right)
                    HStack(alignment: .top) {
                        Spacer()
                        VStack(alignment: .trailing, spacing: 4) {
                            if let offerType = menu.offerType, !offerType.isEmpty {
                                Text(OfferTypes.label(for: offerType))
                                    .font(.custom("Sora-Bold", size: 10))
                                    .foregroundColor(.white)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(offerTypeColor(offerType))
                                    .cornerRadius(6)
                                    .shadow(color: Color.black.opacity(0.2), radius: 3, x: 0, y: 1)
                            }
                            Text(menu.formattedPrice)
                                .font(.custom("Sora-Bold", size: 17))
                                .foregroundColor(Color(red: 0.102, green: 0.102, blue: 0.180))
                                .padding(.horizontal, 12)
                                .padding(.vertical, 7)
                                .background(Color.white)
                                .cornerRadius(10)
                                .shadow(color: Color.black.opacity(0.15), radius: 4, x: 0, y: 2)
                        }
                    }
                    .padding(12)
                }
                .frame(height: 190)
                .clipped()

                // ── INFO ─────────────────────────────────────────────────
                VStack(alignment: .leading, spacing: 10) {
                    HStack(alignment: .center, spacing: 8) {
                        Text(merchant?.name ?? "")
                            .font(.custom("Sora-Bold", size: 17))
                            .foregroundColor(.appTextPrimary)
                            .lineLimit(1)
                        Spacer()
                        Image(systemName: "chevron.right")
                            .font(.custom("Sora-SemiBold", size: 13))
                            .foregroundColor(.appTextSecondary)
                    }

                    // Chip row: status · distance · cuisine (wraps if needed, no scroll)
                    let status = scheduleStatus()
                    let statusChip = InlineChip(
                        icon: "clock.fill",
                        label: status.label,
                        foreground: status.isOpen ? .white : .appTextSecondary,
                        background: status.isOpen ? Color.green : Color.appInputBackground
                    )
                    let distanceChip = calculateDistance().map { distance in
                        InlineChip(
                            icon: "location.fill",
                            label: distance,
                            foreground: .appTextSecondary,
                            background: Color.appInputBackground
                        )
                    }
                    let cuisineChip: InlineChip? = (merchant?.cuisineTypes?.first).map { cuisine in
                        InlineChip(
                            icon: "fork.knife",
                            label: cuisine,
                            foreground: .appTextSecondary,
                            background: Color.appInputBackground
                        )
                    }
                    ViewThatFits(in: .horizontal) {
                        // Try single row first
                        HStack(spacing: 6) {
                            statusChip
                            if let d = distanceChip { d }
                            if let c = cuisineChip { c }
                        }
                        // Falls back to two rows
                        VStack(alignment: .leading, spacing: 6) {
                            statusChip
                            HStack(spacing: 6) {
                                if let d = distanceChip { d }
                                if let c = cuisineChip { c }
                            }
                        }
                    }
                }
                .padding(14)
            }
            .background(Color.appSurface)
            .cornerRadius(16)
            .shadow(color: Color.black.opacity(0.07), radius: 10, x: 0, y: 3)
        }
        .buttonStyle(.plain)
        .onAppear {
            loadMerchant()
            userLocation = appState.locationManager.location
        }
        .onReceive(appState.locationManager.$location) { loc in
            userLocation = loc
        }
    }

    private func offerTypeColor(_ type: String) -> Color {
        // Color unificado rojo anaranjado para todas las pastillas de tipo de oferta.
        Color(red: 0.91, green: 0.365, blue: 0.247)
    }

    private struct ScheduleInfo {
        let isOpen: Bool
        let label: String
    }

    private func scheduleStatus() -> ScheduleInfo {
        guard let schedule = merchant?.schedule, !schedule.isEmpty else {
            return ScheduleInfo(isOpen: false, label: localization.t("feed_no_schedule"))
        }
        let weekday = Calendar.current.component(.weekday, from: Date())
        let keys = ["sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"]
        let todayKey = keys[weekday - 1]
        let fmt = DateFormatter()
        fmt.dateFormat = "HH:mm"
        let now = fmt.string(from: Date())

        if let entry = schedule.first(where: { $0.day == todayKey }) {
            if now >= entry.open && now <= entry.close {
                return ScheduleInfo(isOpen: true, label: "\(localization.t("feed_open_closes")) \(formatHHmm(entry.close))")
            } else if now < entry.open {
                return ScheduleInfo(isOpen: false, label: "\(localization.t("feed_opens_at")) \(formatHHmm(entry.open))")
            }
        }
        // Closed today or past closing — find next opening
        for offset in 1...7 {
            let nextIdx = (weekday - 1 + offset) % 7
            let nextDay = keys[nextIdx]
            if let entry = schedule.first(where: { $0.day == nextDay }) {
                let dayLabels = [
                    localization.t("feed_day_sun"), localization.t("feed_day_mon"),
                    localization.t("feed_day_tue"), localization.t("feed_day_wed"),
                    localization.t("feed_day_thu"), localization.t("feed_day_fri"),
                    localization.t("feed_day_sat")
                ]
                if offset == 1 {
                    return ScheduleInfo(isOpen: false, label: "\(localization.t("feed_opens_tomorrow")) \(formatHHmm(entry.open))")
                }
                return ScheduleInfo(isOpen: false, label: "\(localization.t("feed_opens_day")) \(dayLabels[nextIdx]) \(formatHHmm(entry.open))")
            }
        }
        return ScheduleInfo(isOpen: false, label: localization.t("feed_closed"))
    }

    /// Normaliza el string de horario a "HH:mm" (24h) independientemente del
    /// formato de entrada (p.ej. "10:00:00Z" o ISO completo).
    private func formatHHmm(_ time: String) -> String {
        let parts = time.components(separatedBy: ":")
        guard parts.count >= 2,
              let h = Int(parts[0].suffix(2)),
              let m = Int(parts[1].prefix(2)) else { return time }
        return String(format: "%02d:%02d", h, m)
    }

    private func loadMerchant() {
        guard merchant == nil else { return }
        Task {
            if let m = try? await FirebaseService.shared.getMerchantProfile(merchantId: menu.businessId) {
                await MainActor.run {
                    self.merchant = m
                    onMerchantLoaded?(menu.businessId, m)
                }
            }
        }
    }

    private func calculateDistance() -> String? {
        guard let userLoc = userLocation,
              let addr = merchant?.address else { return nil }
        let merchantLoc = CLLocation(latitude: addr.lat, longitude: addr.lng)
        let meters = userLoc.distance(from: merchantLoc)
        return meters < 1000 ? "\(Int(meters))m" : String(format: "%.1f km", meters / 1000)
    }
}

#Preview {
    FeedView()
        .environmentObject(AppState())
        .environmentObject(TourManager())
}
