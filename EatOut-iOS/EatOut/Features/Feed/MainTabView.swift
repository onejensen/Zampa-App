import SwiftUI

struct MainTabView: View {
    @EnvironmentObject var appState: AppState
    @State private var selectedTab: Int = 0
    @State private var deepLinkMenuId: String? = nil

    private var profileTabIndex: Int {
        appState.currentUser?.role == .comercio ? 3 : 2
    }

    var body: some View {
        TabView(selection: $selectedTab) {
            FeedView(onNavigateToProfile: { selectedTab = profileTabIndex })
                .tabItem { Label("Feed", systemImage: "house.fill") }
                .tag(0)

            FavoritesView()
                .tabItem { Label("Favoritos", systemImage: "heart.fill") }
                .tag(1)

            if appState.currentUser?.role == .comercio {
                MerchantDashboardView()
                    .tabItem { Label("Mis Menús", systemImage: "plus.square.fill") }
                    .tag(2)
            }

            ProfileView()
                .tabItem { Label("Perfil", systemImage: "person.fill") }
                .tag(profileTabIndex)
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
}

struct IdentifiableMenu: Identifiable {
    let id: String
}

#Preview {
    MainTabView()
        .environmentObject(AppState())
}





