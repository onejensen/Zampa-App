import SwiftUI

struct ContentView: View {
    @EnvironmentObject var appState: AppState
    @State private var showOnboarding = false

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
                        Text("EatOut")
                            .font(.system(size: 36, weight: .bold))
                            .foregroundColor(.white)
                        ProgressView()
                            .tint(.white)
                            .padding(.top, 8)
                    }
                }
            } else if appState.isAuthenticated {
                if appState.needsMerchantSetup {
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
        .onChange(of: appState.needsMerchantSetup) { _, needsSetup in
            // Once merchant setup is complete, check if onboarding is due
            if !needsSetup { checkOnboarding(uid: appState.currentUser?.id) }
        }
        .onChange(of: appState.currentUser?.id) { _, uid in
            // Fired when a user signs in; only show if setup is already done
            if !appState.needsMerchantSetup { checkOnboarding(uid: uid) }
        }
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





