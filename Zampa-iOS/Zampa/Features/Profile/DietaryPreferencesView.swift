import SwiftUI

struct DietaryPreferencesView: View {
    @ObservedObject var localization = LocalizationManager.shared
    @Environment(\.dismiss) var dismiss
    @EnvironmentObject var appState: AppState

    @State private var prefs: DietaryPreferences = DietaryPreferences()

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 32) {
                    VStack(alignment: .leading, spacing: 16) {
                        Text(localization.t("dietary_diets"))
                            .font(.appSubheadline)
                            .fontWeight(.bold)
                            .foregroundColor(.appTextPrimary)

                        VStack(spacing: 12) {
                            FilterToggle(title: localization.t("dietary_vegetarian"), icon: "leaf", isOn: $prefs.isVegetarian)
                            FilterToggle(title: localization.t("dietary_vegan"), icon: "leaf.fill", isOn: Binding(
                                get: { prefs.isVegan },
                                set: { prefs.isVegan = $0; if $0 { prefs.isVegetarian = true } }
                            ))
                        }
                    }

                    VStack(alignment: .leading, spacing: 16) {
                        Text(localization.t("dietary_allergens"))
                            .font(.appSubheadline)
                            .fontWeight(.bold)
                            .foregroundColor(.appTextPrimary)

                        VStack(spacing: 12) {
                            FilterToggle(title: localization.t("dietary_no_meat"), icon: "fork.knife", isOn: $prefs.isMeatFree)
                            FilterToggle(title: localization.t("dietary_no_fish"), icon: "fish.fill", isOn: $prefs.isFishFree)
                            FilterToggle(title: localization.t("dietary_no_gluten"), icon: "wheat", isOn: $prefs.isGlutenFree)
                            FilterToggle(title: localization.t("dietary_no_lactose"), icon: "drop.fill", isOn: $prefs.isLactoseFree)
                            FilterToggle(title: localization.t("dietary_no_nuts"), icon: "hazelnut.fill", isOn: $prefs.isNutFree)
                        }
                    }

                    Text(localization.t("dietary_footer"))
                        .font(.appCaption)
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
                    Text(localization.t("dietary_save"))
                }
                .buttonStyle(AppDesign.ButtonStyle(isPrimary: true))
            }
            .padding(24)
            .background(Color.appSurface.shadow(color: Color.black.opacity(0.05), radius: 10, x: 0, y: -5))
        }
        .navigationTitle(localization.t("dietary_title"))
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
