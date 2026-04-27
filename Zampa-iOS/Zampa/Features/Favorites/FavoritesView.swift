import SwiftUI

// MARK: - Model

private struct FavoriteItem: Identifiable {
    let merchant: Merchant
    let activeMenu: Menu?
    var id: String { merchant.id }
}

// MARK: - FavoritesView

struct FavoritesView: View {
    @EnvironmentObject var tourManager: TourManager
    @ObservedObject var localization = LocalizationManager.shared
    @State private var items: [FavoriteItem] = []
    @State private var isLoading = true
    @State private var selectedMenu: Menu? = nil

    var body: some View {
        NavigationView {
            ZStack {
                Color.appBackground.ignoresSafeArea()
                // Anchor invisible para registrar el contenido de esta pantalla en el tour
                Color.clear.tourTarget(.favoritesContent)

                if isLoading {
                    ProgressView(localization.t("favorites_loading"))
                        .progressViewStyle(CircularProgressViewStyle(tint: .appPrimary))
                } else if items.isEmpty {
                    emptyState
                } else {
                    ScrollView {
                        LazyVStack(spacing: 16) {
                            Text(localization.t("favorites_subtitle"))
                                .font(.appCaption)
                                .foregroundColor(.appTextSecondary)
                                .frame(maxWidth: .infinity, alignment: .leading)

                            ForEach(items) { item in
                                FavoriteRow(item: item) {
                                    removeFavorite(merchantId: item.merchant.id)
                                } onTap: {
                                    if let menu = item.activeMenu {
                                        selectedMenu = menu
                                    }
                                }
                            }
                        }
                        .padding(20)
                    }
                }
            }
            .navigationTitle(localization.t("favorites_title"))
            .onAppear { loadFavorites() }
            .sheet(item: $selectedMenu) { menu in
                NavigationView {
                    MenuDetailView(menu: menu, presentedAsSheet: true)
                }
            }
        }
    }

    // MARK: Empty state

    private var emptyState: some View {
        VStack(spacing: 24) {
            Image(systemName: "heart.fill")
                .font(.custom("Sora-Regular", size: 80))
                .foregroundColor(.appPrimary.opacity(0.1))
                .overlay(
                    Image(systemName: "heart")
                        .font(.custom("Sora-Regular", size: 80))
                        .foregroundColor(.appPrimary.opacity(0.2))
                )
            VStack(spacing: 8) {
                Text(localization.t("favorites_empty"))
                    .font(.appHeadline)
                    .foregroundColor(.appTextPrimary)
                Text(localization.t("favorites_empty_desc"))
                    .font(.appBody)
                    .foregroundColor(.appTextSecondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 40)
            }
        }
    }

    // MARK: Data loading - merchants y menus en paralelo en una sola pasada

    private func loadFavorites() {
        Task {
            isLoading = true
            do {
                let favs = try await FirebaseService.shared.getFavorites()

                let loaded: [FavoriteItem] = try await withThrowingTaskGroup(of: FavoriteItem?.self) { group in
                    for fav in favs {
                        group.addTask {
                            async let merchantTask = FirebaseService.shared.getMerchantProfile(merchantId: fav.businessId)
                            async let menusTask    = FirebaseService.shared.getMenusByMerchant(merchantId: fav.businessId)
                            guard let merchant = try await merchantTask else { return nil }
                            let menus = try await menusTask
                            let active = menus.first { $0.isActive }
                            return FavoriteItem(merchant: merchant, activeMenu: active)
                        }
                    }
                    var result: [FavoriteItem] = []
                    for try await item in group {
                        if let i = item { result.append(i) }
                    }
                    return result
                }

                await MainActor.run {
                    self.items = loaded
                    self.isLoading = false
                }
            } catch {
                print("Error loading favorites: \(error)")
                await MainActor.run { self.isLoading = false }
            }
        }
    }

    private func removeFavorite(merchantId: String) {
        Task {
            try? await FirebaseService.shared.removeFavorite(merchantId: merchantId)
            await MainActor.run {
                items.removeAll { $0.merchant.id == merchantId }
            }
        }
    }
}

// MARK: - FavoriteRow

private struct FavoriteRow: View {
    let item: FavoriteItem
    let onRemove: () -> Void
    let onTap: () -> Void

    var body: some View {
        Button(action: { if item.activeMenu != nil { onTap() } }) {
            HStack(spacing: 16) {
                let photoUrl = item.merchant.profilePhotoUrl ?? item.merchant.coverPhotoUrl
                Group {
                    if let urlStr = photoUrl, let url = URL(string: urlStr) {
                        CachedAsyncImage(url: url) { img in
                            img.resizable().aspectRatio(contentMode: .fill)
                        } placeholder: {
                            restaurantPlaceholder
                        }
                    } else {
                        restaurantPlaceholder
                    }
                }
                .frame(width: 72, height: 72)
                .clipShape(Circle())

                VStack(alignment: .leading, spacing: 4) {
                    Text(item.merchant.name)
                        .font(.appSubheadline)
                        .fontWeight(.bold)
                        .foregroundColor(.appTextPrimary)

                    if let cuisine = item.merchant.cuisineTypes?.first {
                        Text(cuisine)
                            .font(.appCaption)
                            .foregroundColor(.appSecondary)
                    }

                    if let menu = item.activeMenu {
                        HStack(spacing: 4) {
                            Text(menu.title)
                                .font(.appCaption)
                                .foregroundColor(.appTextSecondary)
                                .lineLimit(1)
                            Spacer()
                            Text(menu.formattedPrice)
                                .font(.appCaption)
                                .fontWeight(.semibold)
                                .foregroundColor(.appPrimary)
                        }
                    } else {
                        Text("Sin menu hoy")
                            .font(.appCaption)
                            .foregroundColor(.appTextSecondary.opacity(0.5))
                    }
                }

                Spacer()

                VStack(spacing: 8) {
                    Button(action: onRemove) {
                        Image(systemName: "heart.fill")
                            .foregroundColor(.red)
                            .padding(8)
                            .background(Color.red.opacity(0.1))
                            .clipShape(Circle())
                    }
                    .buttonStyle(.plain)

                    if item.activeMenu != nil {
                        Image(systemName: "chevron.right")
                            .font(.appCaption)
                            .foregroundColor(.appTextSecondary.opacity(0.5))
                    }
                }
            }
            .padding(12)
            .background(Color.appSurface)
            .cornerRadius(16)
            .shadow(color: Color.black.opacity(0.03), radius: 8, x: 0, y: 4)
        }
        .buttonStyle(.plain)
    }

    private var restaurantPlaceholder: some View {
        Circle()
            .fill(Color.appInputBackground)
            .overlay(Image(systemName: "house.fill").foregroundColor(.appPrimary.opacity(0.3)))
    }
}

// MARK: - Menu: Identifiable para sheet(item:)


#Preview {
    FavoritesView()
}
