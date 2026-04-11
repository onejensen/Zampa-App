import SwiftUI

struct NotificationPreferencesView: View {
    @State private var prefs = NotificationPreferences()
    @State private var isLoading = true
    @State private var isSaving = false

    var body: some View {
        List {
            Section(header: Text("Restaurantes favoritos")) {
                Toggle(isOn: $prefs.newMenuFromFavorites) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Nuevas ofertas de favoritos")
                            .font(.appBody)
                            .foregroundColor(.appTextPrimary)
                        Text("Recibe una notificación cuando un restaurante favorito publique un nuevo menú o plato del día")
                            .font(.caption)
                            .foregroundColor(.appTextSecondary)
                    }
                }
                .tint(.appPrimary)
                .onChange(of: prefs.newMenuFromFavorites) { _, _ in savePrefs() }
            }

            Section(header: Text("Otras notificaciones")) {
                Toggle(isOn: $prefs.promotions) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Promociones y descuentos")
                            .font(.appBody)
                            .foregroundColor(.appTextPrimary)
                        Text("Ofertas especiales y descuentos exclusivos")
                            .font(.caption)
                            .foregroundColor(.appTextSecondary)
                    }
                }
                .tint(.appPrimary)
                .onChange(of: prefs.promotions) { _, _ in savePrefs() }

                Toggle(isOn: $prefs.general) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Novedades de Zampa")
                            .font(.appBody)
                            .foregroundColor(.appTextPrimary)
                        Text("Actualizaciones de la app y nuevas funcionalidades")
                            .font(.caption)
                            .foregroundColor(.appTextSecondary)
                    }
                }
                .tint(.appPrimary)
                .onChange(of: prefs.general) { _, _ in savePrefs() }
            }

            Section {
                VStack(alignment: .leading, spacing: 8) {
                    HStack(spacing: 6) {
                        Image(systemName: "star.fill")
                            .foregroundColor(.appPrimary)
                            .font(.system(size: 14))
                        Text("Zampa Pro")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.appPrimary)
                    }
                    Text("Los restaurantes con suscripción Pro envían notificaciones automáticas a sus seguidores cada vez que publican una nueva oferta.")
                        .font(.caption)
                        .foregroundColor(.appTextSecondary)
                }
                .padding(.vertical, 4)
            }
        }
        .listStyle(InsetGroupedListStyle())
        .navigationTitle("Notificaciones")
        .navigationBarTitleDisplayMode(.inline)
        .redacted(reason: isLoading ? .placeholder : [])
        .task { await loadPrefs() }
    }

    private func loadPrefs() async {
        do {
            prefs = try await FirebaseService.shared.getNotificationPreferences()
        } catch {}
        isLoading = false
    }

    private func savePrefs() {
        guard !isLoading else { return }
        let current = prefs
        Task {
            try? await FirebaseService.shared.updateNotificationPreferences(current)
        }
    }
}
