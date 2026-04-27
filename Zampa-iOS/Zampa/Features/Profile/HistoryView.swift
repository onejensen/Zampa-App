import SwiftUI
import FirebaseFirestore

struct HistoryView: View {
    @ObservedObject var localization = LocalizationManager.shared
    @State private var callEntries: [[String: Any]] = []
    @State private var directionEntries: [[String: Any]] = []
    @State private var favoriteNames: [(id: String, name: String, date: String)] = []
    @State private var isLoading = true

    var body: some View {
        List {
            Section(header: Label(localization.t("history_calls"), systemImage: "phone.fill").foregroundColor(.green)) {
                if callEntries.isEmpty && !isLoading {
                    Text(localization.t("history_no_records"))
                        .font(.appBody)
                        .foregroundColor(.appTextSecondary)
                } else {
                    ForEach(callEntries.indices, id: \.self) { i in
                        historyRow(
                            icon: "phone.fill",
                            color: .green,
                            name: callEntries[i]["businessName"] as? String ?? "Restaurante",
                            timestamp: callEntries[i]["timestamp"] as? Timestamp
                        )
                    }
                }
            }

            Section(header: Label(localization.t("history_directions"), systemImage: "arrow.triangle.turn.up.right.circle.fill").foregroundColor(.blue)) {
                if directionEntries.isEmpty && !isLoading {
                    Text(localization.t("history_no_records"))
                        .font(.appBody)
                        .foregroundColor(.appTextSecondary)
                } else {
                    ForEach(directionEntries.indices, id: \.self) { i in
                        historyRow(
                            icon: "arrow.triangle.turn.up.right.circle.fill",
                            color: .blue,
                            name: directionEntries[i]["businessName"] as? String ?? "Restaurante",
                            timestamp: directionEntries[i]["timestamp"] as? Timestamp
                        )
                    }
                }
            }

            Section(header: Label(localization.t("history_favorites"), systemImage: "heart.fill").foregroundColor(.red)) {
                if favoriteNames.isEmpty && !isLoading {
                    Text(localization.t("history_no_records"))
                        .font(.appBody)
                        .foregroundColor(.appTextSecondary)
                } else {
                    ForEach(favoriteNames, id: \.id) { fav in
                        historyRow(
                            icon: "heart.fill",
                            color: .red,
                            name: fav.name,
                            dateString: fav.date
                        )
                    }
                }
            }
        }
        .listStyle(InsetGroupedListStyle())
        .navigationTitle(localization.t("history_title"))
        .navigationBarTitleDisplayMode(.large)
        .background(Color.appBackground)
        .onAppear { loadData() }
    }

    @ViewBuilder
    private func historyRow(icon: String, color: Color, name: String, timestamp: Timestamp? = nil, dateString: String? = nil) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .foregroundColor(color)
                .frame(width: 24)
            VStack(alignment: .leading, spacing: 2) {
                Text(name)
                    .font(.appBody)
                    .foregroundColor(.appTextPrimary)
                if let ts = timestamp {
                    Text(relativeTime(from: ts.dateValue()))
                        .font(.appCaption)
                        .foregroundColor(.appTextSecondary)
                } else if let ds = dateString, !ds.isEmpty {
                    Text(relativeTime(from: ISO8601DateFormatter().date(from: ds) ?? Date()))
                        .font(.appCaption)
                        .foregroundColor(.appTextSecondary)
                }
            }
            Spacer()
        }
    }

    private func relativeTime(from date: Date) -> String {
        let formatter = RelativeDateTimeFormatter()
        formatter.locale = Locale(identifier: "es_ES")
        formatter.unitsStyle = .short
        return formatter.localizedString(for: date, relativeTo: Date())
    }

    private func loadData() {
        Task {
            do {
                let history = try await FirebaseService.shared.getUserHistory()
                callEntries = history.filter { ($0["action"] as? String) == "call" }
                directionEntries = history.filter { ($0["action"] as? String) == "directions" }
            } catch {}

            do {
                let favorites = try await FirebaseService.shared.getFavorites()
                var names: [(id: String, name: String, date: String)] = []
                for fav in favorites {
                    let merchant = try? await FirebaseService.shared.getMerchantProfile(merchantId: fav.businessId)
                    names.append((id: fav.id, name: merchant?.name ?? fav.businessId, date: fav.createdAt))
                }
                favoriteNames = names
            } catch {}

            isLoading = false
        }
    }
}
