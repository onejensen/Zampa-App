import SwiftUI

struct FilterView: View {
    @Environment(\.presentationMode) var presentationMode

    @State var selectedCuisine: String?
    @State var maxPrice: Double = 30
    @State var maxDistanceKm: Double?
    @State var onlyFavorites: Bool = false
    @State var onlyOpen: Bool = false

    var onApply: (String?, Double?, Double?, Bool, Bool) -> Void

    @State private var cuisineOptions: [String] = []

    private let distanceOptions: [(label: String, value: Double?)] = [
        ("Cualquiera", nil),
        ("1 km", 1),
        ("2 km", 2),
        ("5 km", 5),
        ("10 km", 10),
        ("25 km", 25),
    ]

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                Button("Cancelar") {
                    presentationMode.wrappedValue.dismiss()
                }
                .foregroundColor(.appTextSecondary)

                Spacer()
                Text("Filtrar")
                    .font(.appSubheadline)
                    .fontWeight(.bold)
                Spacer()

                Button("Limpiar") {
                    selectedCuisine = nil
                    maxPrice = 30
                    maxDistanceKm = nil
                    onlyFavorites = false
                    onlyOpen = false
                }
                .foregroundColor(.appPrimary)
            }
            .padding()
            .background(Color.appSurface)

            ScrollView {
                VStack(alignment: .leading, spacing: 32) {

                    // Open now
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Estado")
                            .font(.appSubheadline)
                            .fontWeight(.bold)

                        FilterToggle(title: "Solo abiertos ahora", icon: "clock.fill", isOn: $onlyOpen)
                    }

                    // Favorites
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Mis favoritos")
                            .font(.appSubheadline)
                            .fontWeight(.bold)

                        FilterToggle(title: "Solo favoritos", icon: "heart.fill", isOn: $onlyFavorites)
                    }

                    // Cuisine Types
                    VStack(alignment: .leading, spacing: 16) {
                        Text("Tipo de cocina")
                            .font(.appSubheadline)
                            .fontWeight(.bold)

                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(cuisineOptions, id: \.self) { option in
                                    CategoryPill(title: option, isSelected: selectedCuisine == option) {
                                        selectedCuisine = (selectedCuisine == option) ? nil : option
                                    }
                                }
                            }
                            .padding(.horizontal, 24)
                        }
                        .padding(.horizontal, -24)
                    }

                    // Price Range
                    VStack(alignment: .leading, spacing: 16) {
                        HStack {
                            Text("Precio máximo")
                                .font(.appSubheadline)
                                .fontWeight(.bold)
                            Spacer()
                            Text("Hasta \(Int(maxPrice)) €")
                                .font(.appBody)
                                .foregroundColor(.appPrimary)
                                .fontWeight(.bold)
                        }

                        Slider(value: $maxPrice, in: 5...100, step: 1)
                            .accentColor(.appPrimary)

                        HStack {
                            Text("5€")
                            Spacer()
                            Text("100€")
                        }
                        .font(.caption)
                        .foregroundColor(.appTextSecondary)
                    }

                    // Distance
                    VStack(alignment: .leading, spacing: 16) {
                        Text("Distancia máxima")
                            .font(.appSubheadline)
                            .fontWeight(.bold)

                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(distanceOptions, id: \.label) { option in
                                    CategoryPill(
                                        title: option.label,
                                        isSelected: maxDistanceKm == option.value
                                    ) {
                                        maxDistanceKm = option.value
                                    }
                                }
                            }
                            .padding(.horizontal, 24)
                        }
                        .padding(.horizontal, -24)
                    }
                }
                .padding(24)
            }

            // Apply Button
            VStack {
                Button(action: {
                    onApply(selectedCuisine, maxPrice, maxDistanceKm, onlyFavorites, onlyOpen)
                    presentationMode.wrappedValue.dismiss()
                }) {
                    Text("Mostrar resultados")
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
    FilterView(selectedCuisine: nil, maxPrice: 30, maxDistanceKm: nil, onlyFavorites: false, onlyOpen: false) { _, _, _, _, _ in }
}
