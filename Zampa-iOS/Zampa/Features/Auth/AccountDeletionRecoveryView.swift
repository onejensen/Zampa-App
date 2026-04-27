import SwiftUI

/// Pantalla que se muestra cuando un usuario inicia sesión (o abre la app con
/// sesión cacheada) y su cuenta está marcada como pendiente de eliminación.
/// Ofrece recuperar la cuenta o cerrar sesión.
struct AccountDeletionRecoveryView: View {
    @EnvironmentObject var appState: AppState
    @ObservedObject var localization = LocalizationManager.shared
    @State private var isRecovering = false
    @State private var errorMessage: String?
    @State private var showRecoveredToast = false

    private var purgeDate: Date {
        appState.currentUser?.scheduledPurgeAt ?? Date()
    }

    private var formattedPurgeDate: String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "es_ES")
        formatter.dateStyle = .full
        return formatter.string(from: purgeDate)
    }

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()

            VStack(spacing: 24) {
                Spacer()

                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.custom("Sora-Regular", size: 64))
                    .foregroundColor(.orange)

                Text(localization.t("account_deletion_title"))
                    .font(.appHeadline)
                    .multilineTextAlignment(.center)
                    .foregroundColor(.appTextPrimary)

                VStack(spacing: 8) {
                    Text(localization.t("account_deletion_date"))
                        .font(.appBody)
                        .foregroundColor(.appTextSecondary)
                    Text(formattedPurgeDate)
                        .font(.appBody)
                        .fontWeight(.bold)
                        .foregroundColor(.appTextPrimary)
                        .multilineTextAlignment(.center)
                }

                Text(localization.t("account_deletion_recover_hint"))
                    .font(.appBody)
                    .foregroundColor(.appTextSecondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)

                Spacer()

                VStack(spacing: 12) {
                    Button(action: recoverAccount) {
                        HStack {
                            Spacer()
                            if isRecovering {
                                ProgressView().tint(.white)
                            } else {
                                Text(localization.t("account_deletion_recover"))
                            }
                            Spacer()
                        }
                    }
                    .buttonStyle(AppDesign.ButtonStyle(isPrimary: true, isDisabled: isRecovering))
                    .disabled(isRecovering)

                    Button(localization.t("profile_logout")) {
                        appState.logout()
                    }
                    .foregroundColor(.appTextSecondary)
                    .disabled(isRecovering)
                }
                .padding(.horizontal, 32)
                .padding(.bottom, 48)
            }

            if showRecoveredToast {
                VStack {
                    Text(localization.t("account_deletion_recovered"))
                        .font(.appBody)
                        .foregroundColor(.white)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 12)
                        .background(Capsule().fill(Color.green))
                        .padding(.top, 60)
                    Spacer()
                }
                .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .alert(localization.t("common_error"), isPresented: Binding(
            get: { errorMessage != nil },
            set: { if !$0 { errorMessage = nil } }
        )) {
            Button(localization.t("common_ok")) { errorMessage = nil }
        } message: {
            Text(errorMessage ?? "")
        }
    }

    private func recoverAccount() {
        isRecovering = true
        Task {
            do {
                try await FirebaseService.shared.cancelAccountDeletion()

                // Mostrar toast ANTES de refrescar el usuario: si refrescamos
                // primero, el router cambia a MainTabView y la vista se
                // desmonta antes de que el toast sea visible.
                await MainActor.run {
                    withAnimation { showRecoveredToast = true }
                }
                try? await Task.sleep(nanoseconds: 1_500_000_000)

                if let updated = try? await FirebaseService.shared.getCurrentUser() {
                    await MainActor.run {
                        appState.currentUser = updated
                        isRecovering = false
                    }
                }
            } catch {
                await MainActor.run {
                    isRecovering = false
                    errorMessage = error.localizedDescription
                }
            }
        }
    }
}

#Preview {
    AccountDeletionRecoveryView()
        .environmentObject(AppState())
}
