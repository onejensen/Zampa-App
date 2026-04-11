import SwiftUI

struct ContentView: View {
    @EnvironmentObject var appState: AppState
    @State private var showOnboarding = false
    @State private var deepLinkedOfferId: String?

    var body: some View {
        Group {
            if appState.isLoading {
                // Pantalla de carga mientras verifica autenticación
                ZStack {
                    Color.appPrimary.ignoresSafeArea()
                    VStack(spacing: 20) {
                        Image("Logo")
                            .resizable()
                            .scaledToFit()
                            .frame(width: 120, height: 120)
                        Text("Zampa")
                            .font(.system(size: 36, weight: .bold))
                            .foregroundColor(.white)
                        ProgressView()
                            .tint(.white)
                            .padding(.top, 8)
                    }
                }
            } else if appState.isAuthenticated {
                if appState.currentUser?.deletedAt != nil {
                    // Cuenta pendiente de eliminación → pantalla de recuperación
                    AccountDeletionRecoveryView()
                } else if appState.needsMerchantSetup {
                    // Merchant que no ha completado su perfil
                    MerchantProfileSetupView()
                } else {
                    // Usuario autenticado: pantalla principal
                    MainTabView()
                }
            } else {
                // No autenticado: login/registro
                AuthView()
            }
        }
        // fullScreenCover on the Group (stable anchor — never recreated)
        .fullScreenCover(isPresented: $showOnboarding) {
            OnboardingView(
                isMerchant: appState.currentUser?.role == .comercio,
                uid: appState.currentUser?.id ?? "",
                onFinish: {
                    showOnboarding = false
                }
            )
        }
        // Deep link → muestra MenuDetailView en sheet sobre cualquier estado.
        // Se presenta también cuando el usuario aún no ha hecho login: la oferta
        // es pública y al cerrar el sheet vuelve a la pantalla que tocara.
        .sheet(item: Binding(
            get: { deepLinkedOfferId.map(DeepLinkOffer.init) },
            set: { deepLinkedOfferId = $0?.id }
        )) { offer in
            NavigationView {
                MenuDetailView(menuId: offer.id, presentedAsSheet: true)
            }
        }
        .onChange(of: appState.needsMerchantSetup) { _, needsSetup in
            // Once merchant setup is complete, check if onboarding is due
            if !needsSetup { checkOnboarding(uid: appState.currentUser?.id) }
        }
        .onChange(of: appState.currentUser?.id) { _, uid in
            // Fired when a user signs in; only show if setup is already done
            if !appState.needsMerchantSetup { checkOnboarding(uid: uid) }
        }
        .onChange(of: appState.pendingDeepLinkOfferId) { _, newValue in
            guard let newValue else { return }
            deepLinkedOfferId = newValue
            // Consumimos el flag para que un futuro deep link al mismo offer ID
            // vuelva a disparar el sheet.
            appState.pendingDeepLinkOfferId = nil
        }
    }

    private struct DeepLinkOffer: Identifiable {
        let id: String
    }

    private func checkOnboarding(uid: String?) {
        guard appState.isAuthenticated, let uid, !uid.isEmpty else { return }
        // Only set to true — never back to false from here (finish() handles that)
        if !UserDefaults.standard.bool(forKey: "hasSeenOnboarding_\(uid)") {
            showOnboarding = true
        }
    }
}

#Preview {
    ContentView()
        .environmentObject(AppState())
}





