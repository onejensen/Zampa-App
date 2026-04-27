import SwiftUI

struct FilterView: View {
    @EnvironmentObject var appState: AppState
    @Environment(\.presentationMode) var presentationMode
    @ObservedObject var localization = LocalizationManager.shared

    @State var selectedCuisine: String?
    @State var maxPrice: Double = 30
    @State var maxDistanceKm: Double?
    @State var onlyFavorites: Bool = false
    @State var onlyOpen: Bool = false
    @State var offerType: String? = nil

    var onApply: (String?, Double?, Double?, Bool, Bool, String?) -> Void

    @State private var cuisineOptions: [String] = []

    private var offerTypeLabels: [(value: String, label: String)] {[
        (OfferTypes.menuDelDia, localization.t("offer_type_menu")),
        (OfferTypes.platoDelDia, localization.t("offer_type_plato")),
        (OfferTypes.ofertaDelDia, localization.t("offer_type_oferta")),
        (OfferTypes.ofertaPermanente, localization.t("offer_type_permanente")),
    ]}

    private var distanceOptions: [(label: String, value: Double?)] {[
        (localization.t("filter_any"), nil),
        (localization.t("filter_1km"), 1),
        (localization.t("filter_2km"), 2),
        (localization.t("filter_5km"), 5),
        (localization.t("filter_10km"), 10),
        (localization.t("filter_25km"), 25),
    ]}

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                Button(localization.t("filter_cancel")) {
                    presentationMode.wrappedValue.dismiss()
                }
                .foregroundColor(.appTextSecondary)

                Spacer()
                Text(localization.t("filter_title"))
                    .font(.appSubheadline)
                    .fontWeight(.bold)
                Spacer()

                Button(localization.t("filter_clear")) {
                    selectedCuisine = nil
                    maxPrice = 30
                    maxDistanceKm = nil
                    onlyFavorites = false
                    onlyOpen = false
                    offerType = nil
                }
                .foregroundColor(.appPrimary)
            }
            .padding()
            .background(Color.appSurface)

            ScrollView {
                VStack(alignment: .leading, spacing: 32) {

                    // Quick toggles
                    VStack(alignment: .leading, spacing: 12) {
                        Text(localization.t("filter_status"))
                            .font(.appSubheadline)
                            .fontWeight(.bold)

                        FilterToggle(title: localization.t("filter_open_now"), icon: "clock.fill", isOn: $onlyOpen)
                    }

                    // Distance
                    VStack(alignment: .leading, spacing: 16) {
                        Text(localization.t("filter_max_distance"))
                            .font(.appSubheadline)
                            .fontWeight(.bold)

                        ChipFlow(spacing: 8) {
                            ForEach(distanceOptions, id: \.label) { option in
                                CategoryPill(
                                    title: option.label,
                                    isSelected: maxDistanceKm == option.value
                                ) {
                                    maxDistanceKm = option.value
                                }
                            }
                        }
                    }

                    // Offer type
                    VStack(alignment: .leading, spacing: 16) {
                        Text(localization.t("filter_offer_type"))
                            .font(.appSubheadline)
                            .fontWeight(.bold)

                        ChipFlow(spacing: 8) {
                            ForEach(offerTypeLabels, id: \.value) { option in
                                CategoryPill(
                                    title: option.label,
                                    isSelected: offerType == option.value
                                ) {
                                    offerType = (offerType == option.value) ? nil : option.value
                                }
                            }
                        }
                    }

                    // Cuisine Types
                    VStack(alignment: .leading, spacing: 16) {
                        Text(localization.t("filter_cuisine_type"))
                            .font(.appSubheadline)
                            .fontWeight(.bold)

                        ChipFlow(spacing: 8) {
                            ForEach(cuisineOptions, id: \.self) { option in
                                CategoryPill(title: option, isSelected: selectedCuisine == option) {
                                    selectedCuisine = (selectedCuisine == option) ? nil : option
                                }
                            }
                        }
                    }

                    // Price Range
                    VStack(alignment: .leading, spacing: 16) {
                        HStack(alignment: .top) {
                            Text(localization.t("filter_max_price"))
                                .font(.appSubheadline)
                                .fontWeight(.bold)
                            Spacer()
                            VStack(alignment: .trailing, spacing: 2) {
                                Text("\(localization.t("filter_up_to")) \(Int(maxPrice)) €")
                                    .font(.appBody)
                                    .foregroundColor(.appPrimary)
                                    .fontWeight(.bold)
                                if let prefCode = appState.currentUser?.currencyPreference,
                                   prefCode != "EUR",
                                   let converted = CurrencyService.formatConverted(eurAmount: Double(Int(maxPrice)), to: prefCode) {
                                    Text("~\(converted)")
                                        .font(.appCaption)
                                        .foregroundColor(.appTextSecondary)
                                }
                            }
                        }

                        Slider(value: $maxPrice, in: 5...100, step: 5)
                            .accentColor(.appPrimary)

                        HStack {
                            Text(localization.t("filter_min_price_label"))
                            Spacer()
                            Text(localization.t("filter_max_price_label"))
                        }
                        .font(.appCaption)
                        .foregroundColor(.appTextSecondary)
                    }
                }
                .padding(24)
            }

            // Apply Button
            VStack {
                Button(action: {
                    onApply(selectedCuisine, maxPrice, maxDistanceKm, onlyFavorites, onlyOpen, offerType)
                    presentationMode.wrappedValue.dismiss()
                }) {
                    Text(localization.t("filter_show_results"))
                }
                .buttonStyle(AppDesign.ButtonStyle(isPrimary: true))
            }
            .padding(24)
            .background(Color.appSurface.shadow(color: Color.black.opacity(0.05), radius: 10, x: 0, y: -5))
        }
        .background(Color.appBackground)
        .onAppear {
            Task {
                if let types = try? await FirebaseService.shared.fetchCuisineTypes() {
                    cuisineOptions = types.map { $0.name }
                }
            }
        }
    }
}

#Preview {
    FilterView(selectedCuisine: nil, maxPrice: 30, maxDistanceKm: nil, onlyFavorites: false, onlyOpen: false, offerType: nil) { _, _, _, _, _, _ in }
}

/// Layout que coloca los hijos en filas, saltando a la siguiente cuando no caben.
/// Equivalente a CSS flex-wrap. Mantiene cada chip a su tamaño natural.
struct ChipFlow: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var rowWidth: CGFloat = 0
        var rowHeight: CGFloat = 0
        var totalHeight: CGFloat = 0
        var rowCount = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if rowWidth + size.width > maxWidth, rowWidth > 0 {
                totalHeight += rowHeight + spacing
                rowWidth = 0
                rowHeight = 0
                rowCount += 1
            }
            rowWidth += size.width + (rowWidth > 0 ? spacing : 0)
            rowHeight = max(rowHeight, size.height)
        }
        totalHeight += rowHeight
        return CGSize(width: maxWidth.isFinite ? maxWidth : rowWidth, height: totalHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x: CGFloat = bounds.minX
        var y: CGFloat = bounds.minY
        var rowHeight: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x + size.width > bounds.maxX, x > bounds.minX {
                x = bounds.minX
                y += rowHeight + spacing
                rowHeight = 0
            }
            subview.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}
