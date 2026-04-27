import SwiftUI

/// Catálogo estático de monedas soportadas para el picker.
private struct CurrencyOption: Identifiable {
    let code: String
    let flag: String
    let nameKey: String
    let symbol: String
    var id: String { code }
}

private let currencyOptions: [CurrencyOption] = [
    .init(code: "EUR", flag: "🇪🇺", nameKey: "currency_eur",  symbol: "€"),
    .init(code: "USD", flag: "🇺🇸", nameKey: "currency_usd",  symbol: "$"),
    .init(code: "GBP", flag: "🇬🇧", nameKey: "currency_gbp",  symbol: "£"),
    .init(code: "JPY", flag: "🇯🇵", nameKey: "currency_jpy",  symbol: "¥"),
    .init(code: "CHF", flag: "🇨🇭", nameKey: "currency_chf",  symbol: "CHF"),
    .init(code: "SEK", flag: "🇸🇪", nameKey: "currency_sek",  symbol: "kr"),
    .init(code: "NOK", flag: "🇳🇴", nameKey: "currency_nok",  symbol: "kr"),
    .init(code: "DKK", flag: "🇩🇰", nameKey: "currency_dkk",  symbol: "kr"),
    .init(code: "CAD", flag: "🇨🇦", nameKey: "currency_cad",  symbol: "C$"),
    .init(code: "AUD", flag: "🇦🇺", nameKey: "currency_aud",  symbol: "A$"),
]

struct CurrencyPreferenceView: View {
    @ObservedObject var localization = LocalizationManager.shared
    @EnvironmentObject var appState: AppState
    @Environment(\.dismiss) private var dismiss

    @State private var pendingCode: String?
    @State private var errorMessage: String?

    private var selectedCode: String {
        appState.currentUser?.currencyPreference ?? "EUR"
    }

    var body: some View {
        List {
            if let err = errorMessage {
                Section {
                    Text(err)
                        .font(.appBody)
                        .foregroundColor(.red)
                }
            }
            Section {
                ForEach(currencyOptions) { option in
                    Button(action: { select(option.code) }) {
                        HStack(spacing: 12) {
                            Text(option.flag)
                                .font(.custom("Sora-Regular", size: 24))
                            VStack(alignment: .leading, spacing: 2) {
                                Text(option.code)
                                    .font(.appBody)
                                    .fontWeight(.semibold)
                                    .foregroundColor(.appTextPrimary)
                                Text(localization.t(option.nameKey))
                                    .font(.appCaption)
                                    .foregroundColor(.appTextSecondary)
                            }
                            Spacer()
                            Text(option.symbol)
                                .font(.appBody)
                                .foregroundColor(.appTextSecondary)
                            if pendingCode == option.code {
                                ProgressView()
                                    .controlSize(.small)
                            } else if option.code == selectedCode {
                                Image(systemName: "checkmark")
                                    .foregroundColor(.appPrimary)
                            }
                        }
                        .contentShape(Rectangle())
                    }
                    .disabled(pendingCode != nil)
                }
            } footer: {
                Text(localization.t("currency_footer"))
                    .font(.appCaption)
                    .foregroundColor(.appTextSecondary)
            }
        }
        .listStyle(InsetGroupedListStyle())
        .navigationTitle(localization.t("currency_title"))
        .navigationBarTitleDisplayMode(.inline)
    }

    private func select(_ code: String) {
        guard code != selectedCode, pendingCode == nil else { return }
        pendingCode = code
        errorMessage = nil
        Task {
            do {
                try await FirebaseService.shared.updateCurrencyPreference(code)
                if let updated = try? await FirebaseService.shared.getCurrentUser() {
                    await MainActor.run {
                        appState.currentUser = updated
                        pendingCode = nil
                        dismiss()
                    }
                } else {
                    // El write a Firestore ha tenido éxito pero el refetch falló
                    // (offline, etc). Parcheamos el valor local para que la UI
                    // refleje la nueva selección sin tener que reiniciar la app.
                    await MainActor.run {
                        if let current = appState.currentUser {
                            appState.currentUser = User(
                                id: current.id,
                                email: current.email,
                                name: current.name,
                                role: current.role,
                                phone: current.phone,
                                photoUrl: current.photoUrl,
                                deletedAt: current.deletedAt,
                                scheduledPurgeAt: current.scheduledPurgeAt,
                                currencyPreference: code
                            )
                        }
                        pendingCode = nil
                        dismiss()
                    }
                }
            } catch {
                await MainActor.run {
                    pendingCode = nil
                    errorMessage = error.localizedDescription
                }
            }
        }
    }
}

#Preview {
    NavigationView {
        CurrencyPreferenceView()
            .environmentObject(AppState())
    }
}
