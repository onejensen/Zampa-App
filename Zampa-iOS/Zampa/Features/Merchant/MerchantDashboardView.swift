import SwiftUI

struct MerchantDashboardView: View {
    @ObservedObject var localization = LocalizationManager.shared
    @EnvironmentObject var appState: AppState
    @EnvironmentObject var tourManager: TourManager
    @State private var menus: [Menu] = []
    @State private var isLoading: Bool = false
    @State private var showingCreateMenu = false

    @State private var editingMenu: Menu?
    @State private var deletingMenu: Menu?

    @State private var isSelecting = false
    @State private var selectedIds = Set<String>()
    @State private var showingBulkDeleteConfirm = false

    @State private var todayImpressions: Int = 0
    @State private var todayFavorites: Int = 0
    @State private var todayClicks: Int = 0
    @State private var promoFreeUntil: Date? = nil
    @State private var showingSubscription = false

    private var merchant: Merchant? { appState.merchantProfile }
    private var promoActive: Bool { (promoFreeUntil ?? .distantPast) > Date() }
    private var trialDays: Int? { merchant?.trialDaysRemaining() }

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 0) {

                    // ── BANNER DE SUSCRIPCIÓN ───────────────────────────
                    subscriptionBanner
                        .padding(.horizontal, 16)
                        .padding(.top, 12)

                    // ── STATS GRID ──────────────────────────────────────
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                        StatCard(icon: "eye.fill", title: localization.t("merchant_views_today"), value: "\(todayImpressions)", color: .blue)
                        StatCard(icon: "hand.tap.fill", title: localization.t("merchant_clicks_today"), value: "\(todayClicks)", color: .appPrimary)
                        StatCard(icon: "heart.fill", title: localization.t("merchant_favorites"), value: "\(todayFavorites)", color: .red)
                        StatCard(icon: "fork.knife", title: localization.t("merchant_active_menus"), value: "\(menus.filter { $0.isToday }.count)", color: .green)
                    }
                    .tourTarget(.merchantStatsGrid)
                    .padding(16)

                    // ── BIG PUBLISH BUTTON ──────────────────────────────
                    Button(action: { showingCreateMenu = true }) {
                        HStack(spacing: 12) {
                            Image(systemName: "plus.circle.fill")
                                .font(.custom("Sora-Regular", size: 24))
                            Text(localization.t("merchant_publish"))
                                .font(.custom("Sora-Bold", size: 18))
                        }
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 18)
                        .background(
                            RoundedRectangle(cornerRadius: 16)
                                .fill(Color.appPrimary)
                                .shadow(color: Color.appPrimary.opacity(0.4), radius: 12, x: 0, y: 6)
                        )
                    }
                    .tourTarget(.merchantCreateButton)
                    .padding(.horizontal, 16)
                    .padding(.bottom, 24)

                    // ── SECTION HEADER ──────────────────────────────────
                    HStack {
                        Text(localization.t("merchant_my_offers"))
                            .font(.custom("Sora-Bold", size: 17))
                            .foregroundColor(.appTextPrimary)
                        Spacer()
                        if !menus.isEmpty {
                            Button(isSelecting ? localization.t("merchant_done") : localization.t("merchant_edit")) {
                                isSelecting.toggle()
                                if !isSelecting { selectedIds.removeAll() }
                            }
                            .foregroundColor(.appPrimary)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 12)

                    // ── MENUS LIST ──────────────────────────────────────
                    if isLoading && menus.isEmpty {
                        ProgressView()
                            .padding(40)
                    } else if menus.isEmpty {
                        VStack(spacing: 20) {
                            Image(systemName: "plus.square.dashed")
                                .font(.custom("Sora-Regular", size: 60))
                                .foregroundColor(.appTextSecondary.opacity(0.3))

                            Text(localization.t("merchant_no_menus"))
                                .font(.appSubheadline)
                                .foregroundColor(.appTextSecondary)

                            Button(action: { showingCreateMenu = true }) {
                                Text(localization.t("merchant_first_menu"))
                            }
                            .buttonStyle(AppDesign.ButtonStyle(isPrimary: true))
                            .padding(.horizontal, 40)
                        }
                        .padding(.top, 40)
                        .frame(maxWidth: .infinity)
                    } else {
                        ForEach(menus) { menu in
                            HStack(spacing: 12) {
                                if isSelecting {
                                    Image(systemName: selectedIds.contains(menu.id) ? "checkmark.circle.fill" : "circle")
                                        .foregroundColor(selectedIds.contains(menu.id) ? .appPrimary : .appTextSecondary)
                                        .font(.title3)
                                        .onTapGesture { toggleSelection(menu.id) }
                                }
                                MerchantMenuRow(menu: menu)
                                    .contentShape(Rectangle())
                                    .onTapGesture {
                                        if isSelecting {
                                            toggleSelection(menu.id)
                                        } else if menu.isToday {
                                            editingMenu = menu
                                        }
                                    }
                                if isSelecting {
                                    Spacer()
                                    Button(action: { deletingMenu = menu }) {
                                        Image(systemName: "trash")
                                            .foregroundColor(.red)
                                    }
                                } else if menu.isToday {
                                    Spacer()
                                    Button(action: { editingMenu = menu }) {
                                        Image(systemName: "pencil")
                                            .foregroundColor(.appPrimary)
                                    }
                                }
                            }
                            .padding(.horizontal, 16)
                            .padding(.bottom, 8)
                        }

                        if isSelecting && !selectedIds.isEmpty {
                            Button(action: { showingBulkDeleteConfirm = true }) {
                                HStack {
                                    Image(systemName: "trash")
                                    Text("\(localization.t("merchant_delete_selected")) (\(selectedIds.count))")
                                }
                                .frame(maxWidth: .infinity)
                            }
                            .buttonStyle(AppDesign.ButtonStyle(isPrimary: true))
                            .padding(.horizontal, 16)
                            .padding(.vertical, 12)
                        }
                    }

                    Spacer(minLength: 32)
                }
            }
            .background(Color.appBackground)
            .navigationTitle(localization.t("merchant_dashboard"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {}
            .sheet(isPresented: $showingCreateMenu, onDismiss: {
                loadMerchantMenus()
            }) {
                CreateMenuView(activeMenusCount: menus.count)
            }
            .sheet(item: $editingMenu, onDismiss: {
                loadMerchantMenus()
            }) { menu in
                EditMenuView(menu: menu)
            }
            .alert(localization.t("merchant_delete_menu"), isPresented: Binding(
                get: { deletingMenu != nil },
                set: { if !$0 { deletingMenu = nil } }
            )) {
                Button(localization.t("common_cancel"), role: .cancel) { deletingMenu = nil }
                Button(localization.t("common_delete"), role: .destructive) {
                    if let menu = deletingMenu {
                        performDelete(ids: [menu.id])
                    }
                }
            } message: {
                Text(localization.t("merchant_delete_confirm"))
            }
            .alert("\(localization.t("common_delete")) \(selectedIds.count) menú(s)", isPresented: $showingBulkDeleteConfirm) {
                Button(localization.t("common_cancel"), role: .cancel) { }
                Button(localization.t("common_delete"), role: .destructive) {
                    performDelete(ids: Array(selectedIds))
                }
            } message: {
                Text(localization.t("merchant_delete_selected_body"))
            }
            .onAppear {
                loadMerchantMenus()
                loadTodayStats()
            }
            .task {
                do {
                    if let ms = try await FirebaseService.shared.getPromoFreeUntilMs() {
                        promoFreeUntil = Date(timeIntervalSince1970: Double(ms) / 1000)
                    }
                } catch {}
            }
            .sheet(isPresented: $showingSubscription) {
                SubscriptionView().environmentObject(appState)
            }
        }
    }

    /// Banner clickable con el estado actual de la suscripción.
    @ViewBuilder
    private var subscriptionBanner: some View {
        let fmt: DateFormatter = {
            let f = DateFormatter()
            f.dateStyle = .long
            f.locale = Locale.current
            return f
        }()
        let (title, subtitle, icon): (String, String?, String) = {
            if promoActive, let until = promoFreeUntil {
                return (
                    localization.t("subscription_promo_title"),
                    String(format: localization.t("subscription_promo_body"), fmt.string(from: until)),
                    "gift.fill"
                )
            }
            guard let days = trialDays else { return (localization.t("subscription_title"), nil, "star.fill") }
            if days <= 0 {
                return (localization.t("subscription_trial_ends_today"), nil, "exclamationmark.circle.fill")
            }
            return (String(format: localization.t("subscription_trial_days_remaining"), days), nil, "hourglass")
        }()
        Button(action: { showingSubscription = true }) {
            HStack(spacing: 12) {
                Image(systemName: icon).foregroundColor(.appPrimary)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.custom("Sora-Bold", size: 14))
                        .foregroundColor(.appTextPrimary)
                    if let subtitle {
                        Text(subtitle)
                            .font(.appCaption)
                            .foregroundColor(.appTextSecondary)
                            .lineLimit(2)
                    }
                }
                Spacer()
                Image(systemName: "chevron.right").foregroundColor(.appTextSecondary)
            }
            .padding(14)
            .background(RoundedRectangle(cornerRadius: 14).fill(Color.appPrimary.opacity(0.12)))
        }
        .buttonStyle(.plain)
    }

    private func toggleSelection(_ id: String) {
        if selectedIds.contains(id) {
            selectedIds.remove(id)
        } else {
            selectedIds.insert(id)
        }
    }

    private func loadMerchantMenus() {
        isLoading = true
        Task {
            do {
                guard let merchantId = appState.currentUser?.id else { return }
                let merchantMenus = try await MenuService.shared.getMenusByMerchant(merchantId: merchantId)
                await MainActor.run {
                    self.menus = merchantMenus
                    self.isLoading = false
                }
            } catch {
                await MainActor.run {
                    self.isLoading = false
                }
            }
        }
    }

    private func loadTodayStats() {
        guard let merchantId = appState.currentUser?.id else { return }
        let dateStr = String(ISO8601DateFormatter().string(from: Date()).prefix(10))
        Task {
            let doc = try? await FirebaseService.shared.db
                .collection("metrics").document(merchantId)
                .collection("daily").document(dateStr)
                .getDocument()
            let data = doc?.data() ?? [:]
            let clicks = data["clicks"] as? [String: Any] ?? [:]
            await MainActor.run {
                todayImpressions = (data["impressions"] as? Int) ?? 0
                todayFavorites = (data["favorites"] as? Int) ?? 0
                todayClicks = ((clicks["call"] as? Int) ?? 0) + ((clicks["directions"] as? Int) ?? 0)
            }
        }
    }

    private func performDelete(ids: [String]) {
        Task {
            for id in ids {
                try? await MenuService.shared.deleteMenu(menuId: id)
            }
            await MainActor.run {
                deletingMenu = nil
                selectedIds.removeAll()
                isSelecting = false
                loadMerchantMenus()
            }
        }
    }
}

// MARK: - Stat Card

struct StatCard: View {
    let icon: String
    let title: String
    let value: String
    let color: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: icon)
                    .foregroundColor(color)
                    .font(.custom("Sora-Regular", size: 18))
                Spacer()
            }
            Text(value)
                .font(.custom("Sora-Bold", size: 28))
                .foregroundColor(.appTextPrimary)
            Text(title)
                .font(.custom("Sora-Regular", size: 12))
                .foregroundColor(.appTextSecondary)
        }
        .padding(16)
        .background(Color.appSurface)
        .cornerRadius(14)
        .shadow(color: Color.black.opacity(0.05), radius: 8, x: 0, y: 2)
    }
}

// MARK: - Merchant Menu Row

struct MerchantMenuRow: View {
    @ObservedObject var localization = LocalizationManager.shared
    let menu: Menu

    var body: some View {
        HStack(spacing: 16) {
            if let photoUrl = menu.photoUrls.first, let url = URL(string: photoUrl) {
                CachedAsyncImage(url: url) { image in
                    image.resizable().aspectRatio(contentMode: .fill)
                } placeholder: {
                    Rectangle().fill(Color.appInputBackground)
                }
                .frame(width: 80, height: 80)
                .cornerRadius(12)
            }

            VStack(alignment: .leading, spacing: 4) {
                Text(menu.title)
                    .font(.appSubheadline)
                    .fontWeight(.bold)
                    .foregroundColor(.appTextPrimary)

                Text("\(menu.priceTotal, specifier: "%.2f") \(menu.currency)")
                    .font(.appBody)
                    .foregroundColor(.appPrimary)
                    .fontWeight(.bold)

                Text(Self.formatDate(menu.createdAt))
                    .font(.appCaption)
                    .foregroundColor(.appTextSecondary)
            }

            Spacer()

            if !menu.isToday {
                Text(localization.t("merchant_expired"))
                    .font(.caption2)
                    .foregroundColor(.appTextSecondary)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.appInputBackground)
                    .cornerRadius(8)
            }
        }
        .padding(.vertical, 8)
    }

    private static let isoFormatter: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()

    private static let isoFormatterNoFrac: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f
    }()

    private static let displayFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "dd/MM/yy  HH:mm"
        return f
    }()

    private static func formatDate(_ iso: String) -> String {
        guard let date = isoFormatter.date(from: iso) ?? isoFormatterNoFrac.date(from: iso) else {
            return iso
        }
        return displayFormatter.string(from: date)
    }
}

#Preview {
    MerchantDashboardView()
        .environmentObject(AppState())
}
