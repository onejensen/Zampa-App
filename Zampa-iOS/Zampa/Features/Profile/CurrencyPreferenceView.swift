import SwiftUI

/// Catálogo estático de monedas soportadas para el picker.
private struct CurrencyOption: Identifiable {
    let code: String
    let flag: String
    let name: String
    let symbol: String
    var id: String { code }
}

private let currencyOptions: [CurrencyOption] = [
    .init(code: "EUR", flag: "🇪🇺", name: "Euro",                    symbol: "€"),
    .init(code: "USD", flag: "🇺🇸", name: "Dólar estadounidense",    symbol: "$"),
    .init(code: "GBP", flag: "🇬🇧", name: "Libra esterlina",          symbol: "£"),
    .init(code: "JPY", flag: "🇯🇵", name: "Yen japonés",              symbol: "¥"),
    .init(code: "CHF", flag: "🇨🇭", name: "Franco suizo",             symbol: "CHF"),
    .init(code: "SEK", flag: "🇸🇪", name: "Corona sueca",             symbol: "kr"),
    .init(code: "NOK", flag: "🇳🇴", name: "Corona noruega",           symbol: "kr"),
    .init(code: "DKK", flag: "🇩🇰", name: "Corona danesa",            symbol: "kr"),
    .init(code: "CAD", flag: "🇨🇦", name: "Dólar canadiense",         symbol: "C$"),
    .init(code: "AUD", flag: "🇦🇺", name: "Dólar australiano",        symbol: "A$"),
]

struct CurrencyPreferenceView: View {
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
                                .font(.system(size: 24))
                            VStack(alignment: .leading, spacing: 2) {
                                Text(option.code)
                                    .font(.appBody)
                                    .fontWeight(.semibold)
                                    .foregroundColor(.appTextPrimary)
                                Text(option.name)
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
                Text("Los precios siempre se cobran en euros. Esta opción sólo cambia cómo se muestran en la app.")
                    .font(.appCaption)
                    .foregroundColor(.appTextSecondary)
            }
        }
        .listStyle(InsetGroupedListStyle())
        .navigationTitle("Moneda")
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
                    await MainActor.run {
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
