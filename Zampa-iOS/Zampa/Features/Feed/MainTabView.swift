import SwiftUI

struct MainTabView: View {
    @EnvironmentObject var appState: AppState
    @EnvironmentObject var tourManager: TourManager
    @ObservedObject var localization = LocalizationManager.shared
    @State private var selectedTab: Int = 0
    @State private var deepLinkMenuId: String? = nil

    private var profileTabIndex: Int {
        appState.currentUser?.role == .comercio ? 3 : 2
    }

    var body: some View {
        TabView(selection: $selectedTab) {
            FeedView(onNavigateToProfile: { selectedTab = profileTabIndex })
                .tabItem { Label(localization.t("tab_feed"), systemImage: "fork.knife") }
                .tag(0)

            FavoritesView()
                .tabItem { Label(localization.t("tab_favorites"), systemImage: "heart.fill") }
                .tag(1)

            if appState.currentUser?.role == .comercio {
                MerchantDashboardView()
                    .tabItem { Label(localization.t("tab_my_menus"), systemImage: "plus.square.fill") }
                    .tag(2)
            }

            ProfileView()
                .tabItem {
                    Label(localization.t("tab_profile"), systemImage: "person.fill")
                }
                .tag(profileTabIndex)
        }
        .tint(.appPrimary)
        .background(
            GeometryReader { geo in
                Color.clear.onAppear {
                    registerTabBounds(geo: geo)
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
                        if let uid = appState.currentUser?.id {
                            tourManager.start(
                                for: uid,
                                isMerchant: appState.currentUser?.role == .comercio
                            )
                        }
                    }
                }
            }
        )
        .sheet(item: Binding(
            get: { deepLinkMenuId.map { IdentifiableMenu(id: $0) } },
            set: { deepLinkMenuId = $0?.id }
        )) { item in
            NavigationStack {
                MenuDetailView(menuId: item.id)
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .openMenuDetail)) { notification in
            if let menuId = notification.userInfo?["menuId"] as? String {
                self.deepLinkMenuId = menuId
            }
        }
    }

    private func registerTabBounds(geo: GeometryProxy) {
        let isMerchant = appState.currentUser?.role == .comercio
        let tabCount: CGFloat = isMerchant ? 4 : 3
        let tabWidth = geo.size.width / tabCount
        let safeBottom = geo.safeAreaInsets.bottom
        let tabBarTop = geo.size.height - 49 - safeBottom

        tourManager.register(
            target: .favoritesTab,
            bounds: CGRect(x: tabWidth * 1, y: tabBarTop, width: tabWidth, height: 49)
        )
        if isMerchant {
            tourManager.register(
                target: .merchantDashboardTab,
                bounds: CGRect(x: tabWidth * 2, y: tabBarTop, width: tabWidth, height: 49)
            )
        }
    }
}

struct IdentifiableMenu: Identifiable {
    let id: String
}

#Preview {
    MainTabView()
        .environmentObject(AppState())
        .environmentObject(TourManager())
}





