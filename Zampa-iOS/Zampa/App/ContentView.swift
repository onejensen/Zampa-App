import SwiftUI

struct ContentView: View {
    @EnvironmentObject var appState: AppState
    @StateObject private var tourManager = TourManager()
    @State private var deepLinkedOfferId: String?

    var body: some View {
        Group {
            if appState.isLoading {
                ZStack {
                    Color.appPrimary.ignoresSafeArea()
                    VStack(spacing: 20) {
                        Image("Logo")
                            .resizable()
                            .scaledToFit()
                            .frame(width: 120, height: 120)
                        Text("Zampa")
                            .font(.custom("Sora-Bold", size: 36))
                            .foregroundColor(.white)
                        ProgressView()
                            .tint(.white)
                            .padding(.top, 8)
                    }
                }
            } else if appState.isAuthenticated {
                if appState.currentUser?.deletedAt != nil {
                    AccountDeletionRecoveryView()
                } else if appState.needsMerchantSetup {
                    MerchantProfileSetupView()
                } else {
                    MainTabView()
                        .environmentObject(tourManager)
                        .overlay {
                            if tourManager.isActive {
                                TourOverlayView()
                                    .environmentObject(tourManager)
                                    .ignoresSafeArea()
                            }
                        }
                }
            } else {
                AuthView()
            }
        }
        .sheet(item: Binding(
            get: { deepLinkedOfferId.map(DeepLinkOffer.init) },
            set: { deepLinkedOfferId = $0?.id }
        )) { offer in
            NavigationView {
                MenuDetailView(menuId: offer.id, presentedAsSheet: true)
            }
        }
        .onChange(of: appState.pendingDeepLinkOfferId) { _, newValue in
            guard let newValue else { return }
            deepLinkedOfferId = newValue
            appState.pendingDeepLinkOfferId = nil
        }
    }

    private struct DeepLinkOffer: Identifiable {
        let id: String
    }
}

#Preview {
    ContentView()
        .environmentObject(AppState())
}





