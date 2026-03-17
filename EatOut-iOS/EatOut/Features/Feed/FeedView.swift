import SwiftUI
import FirebaseFirestore
import CoreLocation

struct FeedView: View {
    var onNavigateToProfile: () -> Void = {}

    @EnvironmentObject var appState: AppState

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
    @State private var onlyOpen: Bool = false
    @State private var merchantMap: [String: Merchant] = [:]
    enum SortOption { case distance, price }

    var hasClientSideFilters: Bool { maxDistanceKm != nil || onlyFavorites || onlyOpen }

    var body: some View {
        NavigationView {
            VStack(spacing: 0) {

                // ── HEADER ──────────────────────────────────────────────
                HStack(alignment: .center) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(greeting)
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundColor(.appPrimary)
                            .kerning(1.5)
                        Text(greeting == "BUENAS NOCHES" ? "¿Dónde cenamos hoy?" : "¿Qué comemos hoy?")
                            .font(.system(size: 24, weight: .bold))
                            .foregroundColor(.appTextPrimary)
                    }
                    Spacer()
                    Button(action: onNavigateToProfile) {
                        Image(systemName: "person.circle")
                            .font(.system(size: 28))
                            .foregroundColor(.appTextPrimary)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 16)
                .padding(.bottom, 16)

                // ── SECTION HEADER ──────────────────────────────────────
                HStack {
                    Text(sortOption == .price ? "Ofertas por precio" : "Ofertas cerca de ti")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundColor(.appTextPrimary)
                    Spacer()
                    if !menus.isEmpty {
                        Text("\(sortedMenus.count) encontrados")
                            .font(.caption)
                            .foregroundColor(.appTextSecondary)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 14)

                // ── SORT + FILTER ────────────────────────────────────────
                HStack(spacing: 10) {
                    SortPill(title: "Distancia", icon: "location.fill", isSelected: sortOption == .distance) {
                        sortOption = .distance
                    }
                    SortPill(title: "Precio", icon: "banknote", isSelected: sortOption == .price) {
                        sortOption = .price
                    }
                    Spacer()
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
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 12)

                // ── CONTENT ──────────────────────────────────────────────
                if isLoading && menus.isEmpty {
                    ProgressView("Buscando menús...")
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if sortedMenus.isEmpty {
                    VStack(spacing: 16) {
                        Image(systemName: "fork.knife")
                            .font(.system(size: 60))
                            .foregroundColor(.appTextSecondary.opacity(0.3))
                        Text("No encontramos menús con estos filtros")
                            .font(.appSubheadline)
                            .foregroundColor(.appTextSecondary)
                        Button("Limpiar filtros") {
                            selectedCuisine = nil
                            maxPrice = nil
                            maxDistanceKm = nil
                            onlyFavorites = false
                            onlyOpen = false
                            loadMenus()
                        }
                        .foregroundColor(.appPrimary)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color.appBackground)
                } else {
                    ScrollView {
                        LazyVStack(spacing: 0) {
                            ForEach(sortedMenus) { menu in
                                MenuCard(menu: menu, onMerchantLoaded: { id, merchant in
                                    merchantMap[id] = merchant
                                })
                                .padding(.horizontal, 16)
                                .padding(.vertical, 8)
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
                    onlyOpen: onlyOpen
                ) { cuisine, price, distance, favOnly, openOnly in
                    self.selectedCuisine = cuisine
                    self.maxPrice = price
                    self.maxDistanceKm = distance
                    self.onlyFavorites = favOnly
                    self.onlyOpen = openOnly
                    loadMenus()
                }
            }
            .onAppear {
                if menus.isEmpty {
                    loadMenus()
                } else {
                    backgroundRefresh()
                }
            }
        }
    }

    private var greeting: String {
        let hour = Calendar.current.component(.hour, from: Date())
        if hour < 12 { return "BUENOS DÍAS" }
        else if hour < 20 { return "BUENAS TARDES" }
        else { return "BUENAS NOCHES" }
    }

    private var filtersActive: Bool {
        selectedCuisine != nil || (maxPrice != nil && maxPrice! < 100) || maxDistanceKm != nil || onlyFavorites || onlyOpen
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
        if sortOption == .price {
            result.sort { $0.priceTotal < $1.priceTotal }
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
                    cuisineFilter: selectedCuisine,
                    maxPrice: maxPrice
                )
                var filtered = result.menus

                if onlyFavorites {
                    filtered = filtered.filter { favIds.contains($0.businessId) }
                }

                if let maxDist = maxDistanceKm,
                   let userLoc = appState.locationManager.location {
                    filtered = await applyDistanceFilter(menus: filtered, userLoc: userLoc, maxDistKm: maxDist)
                }

                await MainActor.run {
                    self.menus = filtered
                    self.lastDoc = result.lastDoc
                    self.canLoadMore = result.menus.count == 20 && !hasClientSideFilters
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
                    lastDocument: lastDoc,
                    cuisineFilter: selectedCuisine,
                    maxPrice: maxPrice
                )
                await MainActor.run {
                    self.menus.append(contentsOf: result.menus)
                    self.lastDoc = result.lastDoc
                    self.canLoadMore = result.menus.count == 20
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
                cuisineFilter: selectedCuisine,
                maxPrice: maxPrice
            )
            var filtered = result.menus

            if onlyFavorites {
                filtered = filtered.filter { favIds.contains($0.businessId) }
            }

            if let maxDist = maxDistanceKm,
               let userLoc = appState.locationManager.location {
                filtered = await applyDistanceFilter(menus: filtered, userLoc: userLoc, maxDistKm: maxDist)
            }

            self.menus = filtered
            self.lastDoc = result.lastDoc
            self.canLoadMore = result.menus.count == 20 && !hasClientSideFilters
        } catch {}
    }

    /// Silently fetches fresh menus in the background (no spinner).
    /// Called when re-entering the Feed tab with data already loaded.
    private func backgroundRefresh() {
        Task {
            do {
                var favIds: Set<String> = []
                if onlyFavorites {
                    let favs = (try? await FirebaseService.shared.getFavorites()) ?? []
                    favIds = Set(favs.map { $0.businessId })
                }

                let result = try await MenuService.shared.getMenus(
                    cuisineFilter: selectedCuisine,
                    maxPrice: maxPrice
                )
                var filtered = result.menus

                if onlyFavorites {
                    filtered = filtered.filter { favIds.contains($0.businessId) }
                }

                if let maxDist = maxDistanceKm,
                   let userLoc = appState.locationManager.location {
                    filtered = await applyDistanceFilter(menus: filtered, userLoc: userLoc, maxDistKm: maxDist)
                }

                await MainActor.run {
                    self.menus = filtered
                    self.lastDoc = result.lastDoc
                    self.canLoadMore = result.menus.count == 20 && !hasClientSideFilters
                }
            } catch {}
        }
    }

    private func applyDistanceFilter(menus: [Menu], userLoc: CLLocation, maxDistKm: Double) async -> [Menu] {
        let uniqueIds = Set(menus.map { $0.businessId })
        var localMerchantMap: [String: Merchant] = [:]

        await withTaskGroup(of: (String, Merchant?).self) { group in
            for id in uniqueIds {
                group.addTask {
                    let m = try? await FirebaseService.shared.getMerchantProfile(merchantId: id)
                    return (id, m)
                }
            }
            for await (id, merchant) in group {
                if let m = merchant { localMerchantMap[id] = m }
            }
        }

        return menus.filter { menu in
            guard let merchant = localMerchantMap[menu.businessId],
                  let addr = merchant.address else { return false }
            let mLoc = CLLocation(latitude: addr.lat, longitude: addr.lng)
            return userLoc.distance(from: mLoc) / 1000.0 <= maxDistKm
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
                    .font(.system(size: 12))
                Text(title)
                    .font(.system(size: 14, weight: .semibold))
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

// MARK: - Menu Card

struct MenuCard: View {
    let menu: Menu
    @EnvironmentObject var appState: AppState
    @State private var merchant: Merchant? = nil
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
                                    .overlay(
                                        Image(systemName: "photo")
                                            .font(.system(size: 36))
                                            .foregroundColor(.appTextSecondary.opacity(0.3))
                                    )
                            }
                        } else {
                            Rectangle().fill(Color.appInputBackground)
                                .overlay(
                                    Image(systemName: "photo")
                                        .font(.system(size: 36))
                                        .foregroundColor(.appTextSecondary.opacity(0.3))
                                )
                        }
                    }
                    .frame(height: 210)
                    .clipped()
                    .overlay(
                        LinearGradient(
                            gradient: Gradient(colors: [Color.clear, Color.black.opacity(0.6)]),
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )

                    // TOP LEFT: Destacado badge
                    if merchant?.planTier == "pro" || menu.isMerchantPro == true {
                        HStack(spacing: 4) {
                            Image(systemName: "star.fill")
                                .font(.system(size: 9))
                            Text("Destacado")
                                .font(.system(size: 11, weight: .bold))
                        }
                        .foregroundColor(.white)
                        .padding(.horizontal, 9)
                        .padding(.vertical, 5)
                        .background(Color.appPrimary)
                        .cornerRadius(8)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(12)
                    }

                    // TOP RIGHT: Price badge
                    Text(menu.formattedPrice)
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(.black)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .background(Color.white.opacity(0.95))
                        .cornerRadius(10)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                        .padding(12)

                    // BOTTOM LEFT: Distance chip
                    if let distance = calculateDistance() {
                        HStack(spacing: 4) {
                            Image(systemName: "location.fill")
                                .font(.system(size: 9))
                            Text(distance)
                                .font(.system(size: 12, weight: .medium))
                        }
                        .foregroundColor(.white)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(Color.black.opacity(0.5))
                        .cornerRadius(8)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
                        .padding(12)
                    }

                    // BOTTOM RIGHT: Open status chip
                    let open = isOpen()
                    HStack(spacing: 4) {
                        Text(open ? "Abierto" : "Cerrado")
                            .font(.system(size: 11, weight: .bold))
                    }
                    .foregroundColor(.white)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(open ? Color.green : Color.gray.opacity(0.7))
                    .cornerRadius(8)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
                    .padding(12)
                }
                .frame(height: 210)
                .cornerRadius(16)
                .clipped()

                // ── INFO ─────────────────────────────────────────────────
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(merchant?.name ?? "")
                                .font(.system(size: 17, weight: .bold))
                                .foregroundColor(.appTextPrimary)
                                .lineLimit(1)
                            if let cuisines = merchant?.cuisineTypes, let first = cuisines.first {
                                Text(first)
                                    .font(.caption)
                                    .foregroundColor(.appTextSecondary)
                            }
                        }
                        Spacer()
                    }

                    Text("Ver oferta →")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(.appPrimary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(Color.appPrimary.opacity(0.12))
                        .cornerRadius(10)
                }
                .padding(14)
            }
            .background(Color.appSurface)
            .cornerRadius(16)
            .shadow(color: Color.black.opacity(0.07), radius: 10, x: 0, y: 3)
        }
        .buttonStyle(.plain)
        .onAppear { loadMerchant() }
    }

    private func isOpen() -> Bool {
        guard let schedule = merchant?.schedule else { return false }
        let weekday = Calendar.current.component(.weekday, from: Date())
        let keys = ["sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"]
        let todayKey = keys[weekday - 1]
        guard let entry = schedule.first(where: { $0.day == todayKey }) else { return false }
        let fmt = DateFormatter()
        fmt.dateFormat = "HH:mm"
        let now = fmt.string(from: Date())
        return now >= entry.open && now <= entry.close
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
        guard let userLoc = appState.locationManager.location,
              let addr = merchant?.address else { return nil }
        let merchantLoc = CLLocation(latitude: addr.lat, longitude: addr.lng)
        let meters = userLoc.distance(from: merchantLoc)
        return meters < 1000 ? "\(Int(meters))m" : String(format: "%.1f km", meters / 1000)
    }
}

#Preview {
    FeedView()
        .environmentObject(AppState())
}
