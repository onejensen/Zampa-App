import SwiftUI
import CoreLocation
import MapKit

/// Vista pública del perfil de un restaurante. Muestra info (nombre, cocinas,
/// horario, descripción, dirección, mapa) y la lista de sus ofertas activas.
/// Entry points: tap en nombre en MenuDetail, "Ver restaurante" en sheet del mapa,
/// tap en card de Favoritos.
struct MerchantProfileView: View {
    let merchantId: String

    @EnvironmentObject var appState: AppState
    @ObservedObject var localization = LocalizationManager.shared
    @Environment(\.presentationMode) var presentationMode

    @State private var merchant: Merchant?
    @State private var offers: [Menu] = []
    @State private var isLoading = true
    @State private var error: String?
    @State private var mapPosition: MapCameraPosition = .automatic

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                coverHeader
                VStack(alignment: .leading, spacing: 20) {
                    titleBlock
                    if let desc = merchant?.shortDescription, !desc.isEmpty {
                        Text(desc)
                            .font(.appBody)
                            .foregroundColor(.appTextPrimary)
                            .lineSpacing(4)
                    }
                    infoRows
                    actionButtons
                    Divider()
                    offersSection
                }
                .padding(20)
            }
        }
        .background(Color.appBackground.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                Text(merchant?.name ?? localization.t("merchant_profile_title"))
                    .font(.custom("Sora-Bold", size: 16))
                    .foregroundColor(.appTextPrimary)
            }
        }
        .task { await load() }
    }

    // MARK: - Sections

    @ViewBuilder
    private var coverHeader: some View {
        ZStack(alignment: .bottomLeading) {
            if let url = merchant?.coverPhotoUrl.flatMap(URL.init(string:)) {
                AsyncImage(url: url) { image in
                    image.resizable().scaledToFill()
                } placeholder: {
                    Color.appPrimary.opacity(0.4)
                }
                .frame(height: 200)
                .clipped()
            } else {
                Color.appPrimary
                    .frame(height: 160)
                    .overlay(
                        Image("Logo")
                            .resizable()
                            .scaledToFit()
                            .frame(width: 120, height: 120)
                            .opacity(0.35)
                    )
            }
        }
    }

    @ViewBuilder
    private var titleBlock: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(merchant?.name ?? "")
                .font(.custom("Sora-Bold", size: 24))
                .foregroundColor(.appTextPrimary)

            if let cuisines = merchant?.cuisineTypes, !cuisines.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(cuisines, id: \.self) { cuisine in
                            Text(cuisine)
                                .font(.custom("Sora-SemiBold", size: 11))
                                .foregroundColor(.appPrimary)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 5)
                                .background(Color.appPrimary.opacity(0.12))
                                .cornerRadius(12)
                        }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var infoRows: some View {
        VStack(alignment: .leading, spacing: 12) {
            if let phone = merchant?.phone, !phone.isEmpty {
                ProfileInfoRow(icon: "phone.fill", text: phone)
            }
            if let addr = merchant?.addressText ?? merchant?.address?.formatted {
                ProfileInfoRow(icon: "mappin.circle.fill", text: addr)
            }
            if let schedule = merchant?.schedule, !schedule.isEmpty {
                ScheduleBlock(schedule: schedule)
            }
            if let addr = merchant?.address, addr.lat != 0 || addr.lng != 0 {
                Map(position: $mapPosition, interactionModes: []) {
                    Marker(merchant?.name ?? "", coordinate: CLLocationCoordinate2D(latitude: addr.lat, longitude: addr.lng))
                        .tint(Color.appPrimary)
                }
                .frame(height: 160)
                .cornerRadius(16)
                .onAppear {
                    mapPosition = .region(MKCoordinateRegion(
                        center: CLLocationCoordinate2D(latitude: addr.lat, longitude: addr.lng),
                        span: MKCoordinateSpan(latitudeDelta: 0.005, longitudeDelta: 0.005)
                    ))
                }
            }
        }
    }

    @ViewBuilder
    private var actionButtons: some View {
        HStack(spacing: 12) {
            if let phone = merchant?.phone, !phone.isEmpty {
                ProfileActionButton(
                    icon: "phone.fill",
                    label: localization.t("detail_call"),
                    action: { callPhone(phone) }
                )
            }
            if let addr = merchant?.address {
                ProfileActionButton(
                    icon: "map.fill",
                    label: localization.t("detail_directions"),
                    action: { openDirections(to: addr) }
                )
            }
        }
    }

    @ViewBuilder
    private var offersSection: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(localization.t("merchant_profile_offers_header"))
                .font(.custom("Sora-Bold", size: 18))
                .foregroundColor(.appTextPrimary)

            if isLoading {
                ProgressView().frame(maxWidth: .infinity).padding()
            } else if offers.isEmpty {
                Text(localization.t("merchant_profile_no_offers"))
                    .font(.appBody)
                    .foregroundColor(.appTextSecondary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 24)
            } else {
                VStack(spacing: 12) {
                    ForEach(offers) { offer in
                        NavigationLink(destination: MenuDetailView(menu: offer).environmentObject(appState)) {
                            OfferMiniCard(menu: offer)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    // MARK: - Load

    private func load() async {
        do {
            async let merchantTask = FirebaseService.shared.getMerchantProfile(merchantId: merchantId)
            async let offersTask = FirebaseService.shared.getMenusByMerchant(merchantId: merchantId)
            let (m, rawOffers) = try await (merchantTask, offersTask)
            // Solo ofertas hoy/permanentes (misma lógica que feed)
            let active = rawOffers.filter { $0.isToday }
            await MainActor.run {
                self.merchant = m
                self.offers = active
                self.isLoading = false
            }
        } catch {
            await MainActor.run {
                self.error = error.localizedDescription
                self.isLoading = false
            }
        }
    }

    // MARK: - Actions

    private func callPhone(_ phone: String) {
        let cleaned = phone.replacingOccurrences(of: " ", with: "")
        if let url = URL(string: "tel://\(cleaned)") {
            UIApplication.shared.open(url)
        }
    }

    private func openDirections(to addr: MerchantAddress) {
        let hasCoords = addr.lat != 0 || addr.lng != 0
        let dest = hasCoords ? "\(addr.lat),\(addr.lng)" : addr.formatted
        var components = URLComponents(string: "maps://")!
        components.queryItems = [
            URLQueryItem(name: "daddr", value: dest),
            URLQueryItem(name: "dirflg", value: "d"),
        ]
        if let url = components.url { UIApplication.shared.open(url) }
    }
}

// MARK: - Subviews

private struct ProfileInfoRow: View {
    let icon: String
    let text: String
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .foregroundColor(.appPrimary)
                .frame(width: 24)
            Text(text)
                .font(.appBody)
                .foregroundColor(.appTextPrimary)
                .lineLimit(2)
            Spacer()
        }
    }
}

private struct ProfileActionButton: View {
    let icon: String
    let label: String
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: icon)
                Text(label).font(.custom("Sora-SemiBold", size: 14))
            }
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(Color.appPrimary)
            .cornerRadius(12)
        }
    }
}

private struct OfferMiniCard: View {
    let menu: Menu
    var body: some View {
        HStack(spacing: 12) {
            AsyncImage(url: menu.photoUrls.first.flatMap(URL.init(string:))) { image in
                image.resizable().scaledToFill()
            } placeholder: {
                Color.appSurface
            }
            .frame(width: 84, height: 84)
            .clipShape(RoundedRectangle(cornerRadius: 10))

            VStack(alignment: .leading, spacing: 4) {
                Text(menu.title)
                    .font(.custom("Sora-SemiBold", size: 15))
                    .foregroundColor(.appTextPrimary)
                    .lineLimit(1)
                if let desc = menu.description, !desc.isEmpty {
                    Text(desc)
                        .font(.appCaption)
                        .foregroundColor(.appTextSecondary)
                        .lineLimit(2)
                }
                Text(menu.formattedPrice)
                    .font(.custom("Sora-Bold", size: 15))
                    .foregroundColor(.appPrimary)
            }
            Spacer()
        }
        .padding(12)
        .background(Color.appSurface)
        .cornerRadius(14)
        .shadow(color: Color.black.opacity(0.04), radius: 4, x: 0, y: 2)
    }
}

/// Bloque de horario semanal Lun-Dom con HOY resaltado. Los días sin entry se
/// muestran como "Cerrado". Los nombres de los días se localizan con DateFormatter.
private struct ScheduleBlock: View {
    let schedule: [ScheduleEntry]
    @ObservedObject var localization = LocalizationManager.shared

    private let keys = ["sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"]
    // Orden Lun→Dom (España). Índices sobre `keys`.
    private let displayOrder = [1, 2, 3, 4, 5, 6, 0]

    private var todayIndex: Int {
        Calendar.current.component(.weekday, from: Date()) - 1
    }

    private var dayNames: [String] {
        let fmt = DateFormatter()
        fmt.locale = Locale.current
        // `standaloneWeekdaySymbols` devuelve [Sunday, Monday, ...]
        return fmt.standaloneWeekdaySymbols ?? keys.map { $0.capitalized }
    }

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: "clock.fill")
                .foregroundColor(.appPrimary)
                .frame(width: 24)
                .padding(.top, 2)

            VStack(alignment: .leading, spacing: 4) {
                ForEach(displayOrder, id: \.self) { idx in
                    let key = keys[idx]
                    let entry = schedule.first { $0.day == key }
                    let name = dayNames[idx].capitalized(with: Locale.current)
                    let isToday = idx == todayIndex
                    HStack {
                        Text(name)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Text(entry.map { "\($0.open) – \($0.close)" } ?? localization.t("schedule_closed"))
                    }
                    .font(.appBody)
                    .fontWeight(isToday ? .bold : .regular)
                    .foregroundColor(scheduleColor(isToday: isToday, hasEntry: entry != nil))
                }
            }
        }
    }

    private func scheduleColor(isToday: Bool, hasEntry: Bool) -> Color {
        if isToday { return .appPrimary }
        if !hasEntry { return .appTextSecondary }
        return .appTextPrimary
    }
}
