import SwiftUI

struct NotificationPreferencesView: View {
    @ObservedObject var localization = LocalizationManager.shared
    @State private var prefs = NotificationPreferences()
    @State private var isLoading = true
    @State private var isSaving = false

    var body: some View {
        List {
            Section(header: Text(localization.t("notif_favorite_restaurants"))) {
                Toggle(isOn: $prefs.newMenuFromFavorites) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(localization.t("notif_new_offers"))
                            .font(.appBody)
                            .foregroundColor(.appTextPrimary)
                        Text(localization.t("notif_new_offers_desc"))
                            .font(.appCaption)
                            .foregroundColor(.appTextSecondary)
                    }
                }
                .tint(.appPrimary)
                .onChange(of: prefs.newMenuFromFavorites) { _, _ in savePrefs() }
            }

            Section(header: Text(localization.t("notif_other"))) {
                Toggle(isOn: $prefs.promotions) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(localization.t("notif_promotions"))
                            .font(.appBody)
                            .foregroundColor(.appTextPrimary)
                        Text(localization.t("notif_promotions_desc"))
                            .font(.appCaption)
                            .foregroundColor(.appTextSecondary)
                    }
                }
                .tint(.appPrimary)
                .onChange(of: prefs.promotions) { _, _ in savePrefs() }

                Toggle(isOn: $prefs.general) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(localization.t("notif_news"))
                            .font(.appBody)
                            .foregroundColor(.appTextPrimary)
                        Text(localization.t("notif_news_desc"))
                            .font(.appCaption)
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
                            .font(.custom("Sora-Regular", size: 14))
                        Text(localization.t("notif_pro_section"))
                            .font(.custom("Sora-Bold", size: 14))
                            .foregroundColor(.appPrimary)
                    }
                    Text(localization.t("notif_pro_desc"))
                        .font(.appCaption)
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
