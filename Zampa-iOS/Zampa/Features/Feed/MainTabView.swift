import SwiftUI

struct MainTabView: View {
    @EnvironmentObject var appState: AppState
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
        .sheet(item: Binding(
            get: { deepLinkMenuId.map { IdentifiableMenu(id: $0) } },
            set: { deepLinkMenuId = $0?.id }
        )) { item in
            NavigationStack {
                MenuDetailView(menuId: item.id, presentedAsSheet: true)
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





