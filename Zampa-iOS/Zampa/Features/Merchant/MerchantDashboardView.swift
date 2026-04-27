import SwiftUI

struct MerchantDashboardView: View {
    @EnvironmentObject var appState: AppState
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

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 0) {

                    // ── STATS GRID ──────────────────────────────────────
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                        StatCard(icon: "eye.fill", title: "Vistas hoy", value: "\(todayImpressions)", color: .blue)
                        StatCard(icon: "hand.tap.fill", title: "Clics hoy", value: "\(todayClicks)", color: .appPrimary)
                        StatCard(icon: "heart.fill", title: "Favoritos", value: "\(todayFavorites)", color: .red)
                        StatCard(icon: "fork.knife", title: "Menús activos", value: "\(menus.filter { $0.isToday }.count)", color: .green)
                    }
                    .padding(16)

                    // ── BIG PUBLISH BUTTON ──────────────────────────────
                    Button(action: { showingCreateMenu = true }) {
                        HStack(spacing: 12) {
                            Image(systemName: "plus.circle.fill")
                                .font(.system(size: 24))
                            Text("PUBLICAR OFERTA")
                                .font(.system(size: 18, weight: .bold))
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
                    .padding(.horizontal, 16)
                    .padding(.bottom, 24)

                    // ── SECTION HEADER ──────────────────────────────────
                    HStack {
                        Text("Mis ofertas")
                            .font(.system(size: 17, weight: .bold))
                            .foregroundColor(.appTextPrimary)
                        Spacer()
                        if !menus.isEmpty {
                            Button(isSelecting ? "Listo" : "Editar") {
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
                                .font(.system(size: 60))
                                .foregroundColor(.appTextSecondary.opacity(0.3))

                            Text("Aún no has publicado ningún menú")
                                .font(.appSubheadline)
                                .foregroundColor(.appTextSecondary)

                            Button(action: { showingCreateMenu = true }) {
                                Text("Publicar mi primer menú")
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
                                    Text("Eliminar seleccionados (\(selectedIds.count))")
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
            .navigationTitle("Panel Restaurante")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    if !isSelecting {
                        Button(action: { showingCreateMenu = true }) {
                            Image(systemName: "plus")
                        }
                    }
                }
            }
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
            .alert("Eliminar menú", isPresented: Binding(
                get: { deletingMenu != nil },
                set: { if !$0 { deletingMenu = nil } }
            )) {
                Button("Cancelar", role: .cancel) { deletingMenu = nil }
                Button("Eliminar", role: .destructive) {
                    if let menu = deletingMenu {
                        performDelete(ids: [menu.id])
                    }
                }
            } message: {
                Text("¿Estás seguro de que quieres eliminar esta oferta? Los clientes ya no podrán verla.")
            }
            .alert("Eliminar \(selectedIds.count) menú(s)", isPresented: $showingBulkDeleteConfirm) {
                Button("Cancelar", role: .cancel) { }
                Button("Eliminar", role: .destructive) {
                    performDelete(ids: Array(selectedIds))
                }
            } message: {
                Text("Esta acción eliminará los menús seleccionados. No se puede deshacer.")
            }
            .onAppear {
                loadMerchantMenus()
                loadTodayStats()
            }
        }
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
                    .font(.system(size: 18))
                Spacer()
            }
            Text(value)
                .font(.system(size: 28, weight: .bold))
                .foregroundColor(.appTextPrimary)
            Text(title)
                .font(.system(size: 12))
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

                Text(menu.createdAt)
                    .font(.caption)
                    .foregroundColor(.appTextSecondary)
            }

            Spacer()

            if !menu.isToday {
                Text("Pasada")
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
}

#Preview {
    MerchantDashboardView()
        .environmentObject(AppState())
}
