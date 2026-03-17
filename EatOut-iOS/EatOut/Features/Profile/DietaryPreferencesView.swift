import SwiftUI

struct DietaryPreferencesView: View {
    @Environment(\.dismiss) var dismiss
    @EnvironmentObject var appState: AppState

    @State private var prefs: DietaryPreferences = DietaryPreferences()

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 32) {
                    VStack(alignment: .leading, spacing: 16) {
                        Text("Dietas")
                            .font(.appSubheadline)
                            .fontWeight(.bold)
                            .foregroundColor(.appTextPrimary)

                        VStack(spacing: 12) {
                            FilterToggle(title: "Vegetariano", icon: "leaf", isOn: $prefs.isVegetarian)
                            FilterToggle(title: "Vegano", icon: "leaf.fill", isOn: Binding(
                                get: { prefs.isVegan },
                                set: { prefs.isVegan = $0; if $0 { prefs.isVegetarian = true } }
                            ))
                        }
                    }

                    VStack(alignment: .leading, spacing: 16) {
                        Text("Alérgenos e Intolerancias")
                            .font(.appSubheadline)
                            .fontWeight(.bold)
                            .foregroundColor(.appTextPrimary)

                        VStack(spacing: 12) {
                            FilterToggle(title: "Sin Gluten", icon: "wheat", isOn: $prefs.isGlutenFree)
                            FilterToggle(title: "Sin Lactosa", icon: "drop.fill", isOn: $prefs.isLactoseFree)
                            FilterToggle(title: "Sin Frutos Secos", icon: "hazelnut.fill", isOn: $prefs.isNutFree)
                        }
                    }

                    Text("Las ofertas que no sean compatibles con tus preferencias se ocultarán automáticamente en el feed. Las ofertas sin información dietética siempre se muestran.")
                        .font(.caption)
                        .foregroundColor(.appTextSecondary)
                        .padding(.top, 8)
                }
                .padding(24)
            }

            VStack {
                Button(action: {
                    appState.dietaryPreferences = prefs
                    dismiss()
                }) {
                    Text("Guardar preferencias")
                }
                .buttonStyle(AppDesign.ButtonStyle(isPrimary: true))
            }
            .padding(24)
            .background(Color.appSurface.shadow(color: Color.black.opacity(0.05), radius: 10, x: 0, y: -5))
        }
        .navigationTitle("Preferencias Alimentarias")
        .navigationBarTitleDisplayMode(.inline)
        .background(Color.appBackground)
        .onAppear { prefs = appState.dietaryPreferences }
    }
}

#Preview {
    NavigationView {
        DietaryPreferencesView()
            .environmentObject(AppState())
    }
}
