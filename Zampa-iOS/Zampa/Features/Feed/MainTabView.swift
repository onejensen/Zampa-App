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
                    // Para merchants, cambiamos al tab Dashboard antes de iniciar el tour
                    // para que DashboardView se renderice y registre sus bounds antes de
                    // que TourManager.start() se llame.
                    if appState.currentUser?.role == .comercio {
                        selectedTab = 2
                    }
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
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
        .onChange(of: tourManager.pendingTabSwitch) { _, newTab in
            if let tab = newTab {
                selectedTab = tab
                tourManager.clearPendingTabSwitch()
            }
        }
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

    // Los pasos de tab bar se eliminaron del tour iOS (UITabBar es UIKit,
    // se renderiza sobre SwiftUI y el spotlight no sería visible).
    // Esta función se mantiene vacía por si se añaden targets futuros.
    private func registerTabBounds(geo: GeometryProxy) {}
}

struct IdentifiableMenu: Identifiable {
    let id: String
}

#Preview {
    MainTabView()
        .environmentObject(AppState())
        .environmentObject(TourManager())
}





