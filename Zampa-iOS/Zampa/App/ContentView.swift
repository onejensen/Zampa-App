import SwiftUI

struct ContentView: View {
    @EnvironmentObject var appState: AppState
    @State private var deepLinkedOfferId: String?
    /// Marca local que se activa al terminar el onboarding en esta sesión.
    /// Se usa para que el siguiente render eval haga swap a `MainTabView` sin
    /// tener que esperar a que `UserDefaults` se propague.
    @State private var onboardingFinishedThisSession = false

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
                } else if let uid = appState.currentUser?.id,
                          shouldShowOnboarding(uid: uid) {
                    // Primera vez tras autenticarse → onboarding. Se enruta
                    // inline (no fullScreenCover) para que el swap con
                    // MainTabView sea atómico y no parpadee.
                    OnboardingView(
                        isMerchant: appState.currentUser?.role == .comercio,
                        uid: uid,
                        onFinish: {
                            onboardingFinishedThisSession = true
                        }
                    )
                } else {
                    // Usuario autenticado: pantalla principal
                    MainTabView()
                }
            } else {
                // No autenticado: login/registro
                AuthView()
            }
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

    private func shouldShowOnboarding(uid: String) -> Bool {
        if onboardingFinishedThisSession { return false }
        return !UserDefaults.standard.bool(forKey: "hasSeenOnboarding_\(uid)")
    }
}

#Preview {
    ContentView()
        .environmentObject(AppState())
}





