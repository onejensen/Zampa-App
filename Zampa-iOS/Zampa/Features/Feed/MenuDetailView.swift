import SwiftUI
import CoreLocation

struct MenuDetailView: View {
    let menuId: String
    /// `true` cuando la vista se presenta modalmente (sheet desde Favoritos o
    /// deep link). En ese caso añadimos una X en la toolbar leading porque el
    /// sheet no trae back chevron nativo y los usuarios no saben cómo cerrarlo.
    let presentedAsSheet: Bool
    @State private var menu: Menu? = nil
    @Environment(\.presentationMode) var presentationMode
    @EnvironmentObject var appState: AppState
    @State private var merchant: Merchant? = nil
    @State private var isFavorite: Bool = false
    @State private var isLoading: Bool = true
    @State private var showingFullImage = false
    @State private var selectedTag: String? = nil
    @State private var showingDirectionsDialog = false

    init(menu: Menu, presentedAsSheet: Bool = false) {
        self.menuId = menu.id
        self.presentedAsSheet = presentedAsSheet
        self._menu = State(initialValue: menu)
    }

    init(menuId: String, presentedAsSheet: Bool = false) {
        self.menuId = menuId
        self.presentedAsSheet = presentedAsSheet
    }

    var body: some View {
        Group {
            if isLoading && menu == nil {
                ZStack {
                    Color.appBackground.ignoresSafeArea()
                    ProgressView("Cargando oferta...")
                }
            } else if let menu = menu {
                ScrollView(showsIndicators: false) {
                    VStack(spacing: 0) {

                        // ── HERO IMAGE ───────────────────────────────────────
                        ZStack(alignment: .bottomLeading) {
                            // Photo
                            Group {
                                if let photoUrl = menu.photoUrls.first, let url = URL(string: photoUrl) {
                                    CachedAsyncImage(url: url) { phase in
                                        switch phase {
                                        case .success(let img):
                                            img.resizable().aspectRatio(contentMode: .fill)
                                        default:
                                            Rectangle().fill(Color.appInputBackground)
                                        }
                                    }
                                } else {
                                    Rectangle().fill(Color.appInputBackground)
                                        .overlay(Image(systemName: "photo").font(.system(size: 50)).foregroundColor(.appTextSecondary.opacity(0.3)))
                                }
                            }
                            .frame(height: 280)
                            .clipped()

                            // Gradient — no intercepta toques
                            LinearGradient(
                                colors: [.clear, .black.opacity(0.75)],
                                startPoint: .center,
                                endPoint: .bottom
                            )
                            .frame(height: 280)
                            .allowsHitTesting(false)

                            // Overlaid content — no intercepta toques
                            VStack(alignment: .leading, spacing: 6) {
                                // Cuisine + price tier tags
                                HStack(spacing: 8) {
                                    if let cuisines = merchant?.cuisineTypes, let first = cuisines.first {
                                        Text(first)
                                            .font(.system(size: 12, weight: .medium))
                                            .foregroundColor(.white)
                                            .padding(.horizontal, 10).padding(.vertical, 4)
                                            .background(Color.white.opacity(0.2))
                                            .cornerRadius(12)
                                    }
                                    Text(priceRange(menu.priceTotal))
                                        .font(.system(size: 12, weight: .medium))
                                        .foregroundColor(.white)
                                        .padding(.horizontal, 10).padding(.vertical, 4)
                                        .background(Color.white.opacity(0.2))
                                        .cornerRadius(12)
                                }

                                // Restaurant name
                                Text(merchant?.name ?? menu.title)
                                    .font(.system(size: 26, weight: .bold))
                                    .foregroundColor(.white)
                                    .lineLimit(2)

                                // Distance + address
                                HStack(alignment: .top, spacing: 4) {
                                    Image(systemName: "location.fill")
                                        .font(.system(size: 11))
                                        .padding(.top, 1)
                                    VStack(alignment: .leading, spacing: 2) {
                                        if let addr = merchant?.address {
                                            Text(addr.formatted)
                                                .lineLimit(2)
                                                .font(.system(size: 13))
                                        }
                                        if let dist = distanceText {
                                            Text(dist)
                                                .font(.system(size: 12, weight: .medium))
                                                .opacity(0.85)
                                        }
                                    }
                                }
                                .foregroundColor(.white.opacity(0.9))

                                // Open / closed status
                                let status = openStatus()
                                HStack(spacing: 5) {
                                    Circle()
                                        .fill(status.isOpen ? Color.green : Color.red)
                                        .frame(width: 7, height: 7)
                                    Text(status.isOpen ? "Abierto ahora" : "Cerrado ahora")
                                        .font(.system(size: 12, weight: .semibold))
                                    if let timeLabel = status.timeLabel {
                                        Text("· \(timeLabel)")
                                            .font(.system(size: 12))
                                            .opacity(0.85)
                                    }
                                }
                                .foregroundColor(.white)
                                .padding(.horizontal, 8).padding(.vertical, 4)
                                .background(Color.black.opacity(0.35))
                                .cornerRadius(8)
                            }
                            .padding(16)
                            .allowsHitTesting(false)
                        }
                        .frame(height: 280)
                        .overlay(alignment: .bottomTrailing) {
                            Button(action: { toggleFavorite(menu: menu) }) {
                                Image(systemName: isFavorite ? "heart.fill" : "heart")
                                    .font(.system(size: 20, weight: .semibold))
                                    .foregroundColor(isFavorite ? .red : .white)
                                    .padding(10)
                                    .background(Circle().fill(Color.black.opacity(0.45)))
                            }
                            .padding(14)
                        }
                        .contentShape(Rectangle())
                        .onTapGesture { showingFullImage = true }

                        // ── ACTION BUTTONS ───────────────────────────────────
                        HStack(spacing: 12) {
                            OutlineActionButton(icon: "phone", label: "Llamar") {
                                if let phone = merchant?.phone,
                                   let url = URL(string: "tel://\(phone.replacingOccurrences(of: " ", with: ""))") {
                                    Task { await FirebaseService.shared.trackAction(menuId: menu.id, merchantId: menu.businessId, action: "call") }
                                    Task { await FirebaseService.shared.saveUserHistoryEntry(businessId: menu.businessId, businessName: merchant?.name ?? "", action: "call") }
                                    UIApplication.shared.open(url)
                                }
                            }
                            OutlineActionButton(icon: "arrow.triangle.turn.up.right.circle.fill", label: "Cómo ir") {
                                if merchant?.address != nil {
                                    showingDirectionsDialog = true
                                }
                            }
                        }
                        .padding(16)
                        .background(Color.appSurface)
                        .confirmationDialog("¿Cómo quieres ir?", isPresented: $showingDirectionsDialog, titleVisibility: .visible) {
                            Button("Apple Maps") { openDirections(provider: .apple, menu: menu) }
                            Button("Google Maps") { openDirections(provider: .google, menu: menu) }
                            Button("Cancelar", role: .cancel) {}
                        }

                        Divider()

                        // ── CATEGORY TABS ────────────────────────────────────
                        if let tags = menu.tags, !tags.isEmpty {
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 10) {
                                    ForEach(tags, id: \.self) { tag in
                                        Button(action: {
                                            selectedTag = (selectedTag == tag) ? nil : tag
                                        }) {
                                            Text(tag)
                                                .font(.system(size: 14, weight: .semibold))
                                                .lineLimit(1)
                                                .foregroundColor(selectedTag == tag ? .white : .appTextPrimary)
                                                .padding(.horizontal, 18).padding(.vertical, 9)
                                                .background(
                                                    RoundedRectangle(cornerRadius: 22)
                                                        .fill(selectedTag == tag ? Color.appPrimary : Color.appInputBackground)
                                                )
                                        }
                                    }
                                }
                                .padding(.horizontal, 16)
                            }
                            .padding(.vertical, 14)
                            .background(Color.appSurface)

                            Divider()
                        }

                        // ── MENU ITEM ────────────────────────────────────────
                        VStack(alignment: .leading, spacing: 0) {
                            // Section header with offer type badge
                            HStack(spacing: 8) {
                                Image(systemName: "fork.knife")
                                    .foregroundColor(.appPrimary)
                                Text(selectedTag ?? (menu.tags?.first ?? "Menú del día"))
                                    .font(.system(size: 18, weight: .bold))
                                    .foregroundColor(.appTextPrimary)
                                if let type = menu.offerType {
                                    Text(type)
                                        .font(.system(size: 12, weight: .semibold))
                                        .foregroundColor(.appPrimary)
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 3)
                                        .background(Color.appPrimary.opacity(0.12))
                                        .cornerRadius(6)
                                }
                            }
                            .padding(.horizontal, 16)
                            .padding(.top, 20)
                            .padding(.bottom, 14)

                            // Includes row
                            let includes: [(String, String)] = [
                                menu.includesDrink   ? ("Bebida",  "wineglass.fill") : nil,
                                menu.includesDessert ? ("Postre",  "birthday.cake.fill") : nil,
                                menu.includesCoffee  ? ("Café",    "cup.and.saucer.fill") : nil,
                            ].compactMap { $0 }
                            if !includes.isEmpty {
                                HStack(spacing: 8) {
                                    ForEach(includes, id: \.0) { label, icon in
                                        HStack(spacing: 4) {
                                            Image(systemName: icon).font(.system(size: 11))
                                            Text(label).font(.system(size: 12, weight: .medium))
                                        }
                                        .foregroundColor(.appTextSecondary)
                                        .padding(.horizontal, 9)
                                        .padding(.vertical, 4)
                                        .background(Color.appInputBackground)
                                        .cornerRadius(8)
                                    }
                                }
                                .padding(.horizontal, 16)
                                .padding(.bottom, 12)
                            }

                            // Item card
                            HStack(alignment: .top, spacing: 12) {
                                // Thumbnail
                                Group {
                                    if let photoUrl = menu.photoUrls.first, let url = URL(string: photoUrl) {
                                        CachedAsyncImage(url: url) { phase in
                                            if case .success(let img) = phase {
                                                img.resizable().aspectRatio(contentMode: .fill)
                                            } else {
                                                Rectangle().fill(Color.appInputBackground)
                                            }
                                        }
                                    } else {
                                        Rectangle().fill(Color.appInputBackground)
                                    }
                                }
                                .frame(width: 80, height: 80)
                                .cornerRadius(10)
                                .clipped()

                                VStack(alignment: .leading, spacing: 4) {
                                    Text(menu.title)
                                        .font(.system(size: 15, weight: .bold))
                                        .foregroundColor(.appTextPrimary)
                                        .lineLimit(2)

                                    if let desc = menu.description, !desc.isEmpty {
                                        Text(desc)
                                            .font(.system(size: 13))
                                            .foregroundColor(.appTextSecondary)
                                            .lineLimit(2)
                                    }

                                    Spacer(minLength: 4)

                                    Text(menu.formattedPrice)
                                        .font(.system(size: 16, weight: .bold))
                                        .foregroundColor(.appPrimary)
                                }

                                Spacer()
                            }
                            .padding(.horizontal, 16)
                            .padding(.bottom, 20)
                        }
                        .background(Color.appSurface)

                        // ── DIETARY INFO ─────────────────────────────────────
                        if menu.dietaryInfo.hasAnyInfo {
                            Divider()
                            DietaryInfoSection(dietaryInfo: menu.dietaryInfo)
                        }

                        // ── SHORT DESCRIPTION ────────────────────────────────
                        if let desc = merchant?.shortDescription, !desc.isEmpty {
                            Divider()
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Sobre el restaurante")
                                    .font(.system(size: 15, weight: .semibold))
                                    .foregroundColor(.appTextPrimary)
                                Text(desc)
                                    .font(.appBody)
                                    .foregroundColor(.appTextSecondary)
                            }
                            .padding(16)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color.appSurface)
                        }

                        Spacer(minLength: 32)
                    }
                }
                .background(Color.appBackground.ignoresSafeArea())
                .navigationTitle(merchant?.name ?? menu.title)
                .navigationBarTitleDisplayMode(.inline)
                .navigationBarBackButtonHidden(false)
                .toolbar {
                    if presentedAsSheet {
                        ToolbarItem(placement: .navigationBarLeading) {
                            Button(action: { presentationMode.wrappedValue.dismiss() }) {
                                Image(systemName: "xmark.circle.fill")
                                    .font(.title2)
                                    .symbolRenderingMode(.hierarchical)
                                    .foregroundColor(.appTextSecondary)
                            }
                            .accessibilityLabel("Cerrar")
                        }
                    }
                    ToolbarItem(placement: .navigationBarTrailing) {
                        // Compartimos un Universal Link al landing de Firebase Hosting.
                        // El receptor abre la app si la tiene instalada (App Link verificado),
                        // o aterriza en la landing que le redirige a la App Store / Play Store.
                        if let shareURL = URL(string: "https://eatout-70b8b.web.app/o/\(menu.id)") {
                            ShareLink(
                                item: shareURL,
                                subject: Text(menu.title),
                                message: Text("¡Mira este menú en Zampa: \(menu.title)!")
                            ) {
                                Image(systemName: "square.and.arrow.up")
                                    .foregroundColor(.appTextPrimary)
                            }
                        }
                    }
                }
                .fullScreenCover(isPresented: $showingFullImage) {
                    if let photoUrl = menu.photoUrls.first, let url = URL(string: photoUrl) {
                        ZStack(alignment: .topTrailing) {
                            Color.black.ignoresSafeArea()
                            CachedAsyncImage(url: url) { phase in
                                if case .success(let img) = phase {
                                    img.resizable().aspectRatio(contentMode: .fit)
                                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                                } else {
                                    ProgressView().tint(.white)
                                }
                            }
                            Button(action: { showingFullImage = false }) {
                                Image(systemName: "xmark.circle.fill")
                                    .font(.system(size: 30)).foregroundColor(.white).padding()
                            }
                            .padding(.top, 44)
                        }
                        .ignoresSafeArea()
                    }
                }
            } else {
                VStack(spacing: 16) {
                    Text("No se pudo cargar la oferta").font(.appBody)
                    Button("Volver") { presentationMode.wrappedValue.dismiss() }
                        .foregroundColor(.appPrimary)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color.appBackground)
            }
        }
        .onAppear { loadInitialData() }
    }

    // MARK: - Helpers

    private func priceRange(_ price: Double) -> String {
        if price < 10 { return "$" }
        else if price < 20 { return "$$" }
        else { return "$$$" }
    }

    private var distanceText: String? {
        guard let userLoc = appState.locationManager.location,
              let addr = merchant?.address else { return nil }
        let mLoc = CLLocation(latitude: addr.lat, longitude: addr.lng)
        let m = userLoc.distance(from: mLoc)
        return m < 1000 ? "\(Int(m))m" : String(format: "%.1f km", m / 1000)
    }

    private func openStatus() -> (isOpen: Bool, timeLabel: String?) {
        guard let schedule = merchant?.schedule else { return (false, nil) }
        let weekday = Calendar.current.component(.weekday, from: Date())
        let keys = ["sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"]
        let todayKey = keys[weekday - 1]
        guard let entry = schedule.first(where: { $0.day == todayKey }) else { return (false, nil) }

        let fmt = DateFormatter()
        fmt.dateFormat = "HH:mm"
        let now = fmt.string(from: Date())
        let isOpen = now >= entry.open && now <= entry.close

        let displayFmt = DateFormatter()
        displayFmt.dateFormat = "HH:mm"
        displayFmt.locale = Locale(identifier: "es_ES")

        let timeLabel = isOpen
            ? "Cierra a las \(formatTime(entry.close))"
            : "Abre a las \(formatTime(entry.open))"
        return (isOpen, timeLabel)
    }

    private func formatTime(_ time: String) -> String {
        let parts = time.split(separator: ":").map(String.init)
        guard parts.count == 2, let h = Int(parts[0]), let m = Int(parts[1]) else { return time }
        let suffix = h < 12 ? "AM" : "PM"
        let hour = h > 12 ? h - 12 : (h == 0 ? 12 : h)
        return m == 0 ? "\(hour):00 \(suffix)" : "\(hour):\(String(format: "%02d", m)) \(suffix)"
    }

    private func loadInitialData() {
        Task {
            if menu == nil {
                if let fetched = try? await FirebaseService.shared.getMenuById(menuId: menuId) {
                    await MainActor.run { self.menu = fetched }
                }
            }
            if let menu = menu {
                if let m = try? await FirebaseService.shared.getMerchantProfile(merchantId: menu.businessId) {
                    await MainActor.run { self.merchant = m }
                }
                if let fav = try? await FirebaseService.shared.isFavorite(merchantId: menu.businessId) {
                    await MainActor.run { self.isFavorite = fav }
                }
                Task { await FirebaseService.shared.trackImpression(merchantId: menu.businessId) }
            }
            await MainActor.run { self.isLoading = false }
        }
    }

    private func toggleFavorite(menu: Menu) {
        // Optimistic update: actualizar UI inmediatamente
        let previousState = isFavorite
        isFavorite.toggle()
        Task {
            if let newState = try? await FirebaseService.shared.toggleFavorite(merchantId: menu.businessId) {
                await MainActor.run { self.isFavorite = newState }
            } else {
                // Revertir si falla
                await MainActor.run { self.isFavorite = previousState }
            }
        }
    }

    private enum MapsProvider { case apple, google }

    private func openDirections(provider: MapsProvider, menu: Menu) {
        guard let addr = merchant?.address else { return }
        Task { await FirebaseService.shared.trackAction(menuId: menu.id, merchantId: menu.businessId, action: "directions") }
        Task { await FirebaseService.shared.saveUserHistoryEntry(businessId: menu.businessId, businessName: merchant?.name ?? "", action: "directions") }

        let userLoc = appState.locationManager.location
        // Use coordinates when valid, otherwise fall back to the formatted address string
        let hasCoords = addr.lat != 0 || addr.lng != 0
        let destination = hasCoords ? "\(addr.lat),\(addr.lng)" : addr.formatted

        let url: URL?
        switch provider {
        case .apple:
            var components = URLComponents(string: "maps://")!
            components.queryItems = [
                URLQueryItem(name: "daddr", value: destination),
                URLQueryItem(name: "dirflg", value: "d")
            ]
            if let ul = userLoc {
                components.queryItems?.append(URLQueryItem(name: "saddr", value: "\(ul.coordinate.latitude),\(ul.coordinate.longitude)"))
            }
            url = components.url

        case .google:
            var components = URLComponents(string: "comgooglemaps://")!
            components.queryItems = [
                URLQueryItem(name: "daddr", value: destination),
                URLQueryItem(name: "directionsmode", value: "driving")
            ]
            if let ul = userLoc {
                components.queryItems?.append(URLQueryItem(name: "saddr", value: "\(ul.coordinate.latitude),\(ul.coordinate.longitude)"))
            }
            if let appUrl = components.url, UIApplication.shared.canOpenURL(appUrl) {
                UIApplication.shared.open(appUrl)
                return
            }
            // Fall back to Google Maps web
            var webComponents = URLComponents(string: "https://www.google.com/maps/dir/")!
            webComponents.queryItems = [
                URLQueryItem(name: "api", value: "1"),
                URLQueryItem(name: "destination", value: destination),
                URLQueryItem(name: "travelmode", value: "driving")
            ]
            if let ul = userLoc {
                webComponents.queryItems?.append(URLQueryItem(name: "origin", value: "\(ul.coordinate.latitude),\(ul.coordinate.longitude)"))
            }
            url = webComponents.url
        }

        if let url { UIApplication.shared.open(url) }
    }
}

// MARK: - Outline Action Button

struct OutlineActionButton: View {
    let icon: String
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.system(size: 15))
                Text(label)
                    .font(.system(size: 14, weight: .medium))
            }
            .foregroundColor(.appTextPrimary)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 13)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color.appTextSecondary.opacity(0.3), lineWidth: 1)
            )
        }
    }
}
