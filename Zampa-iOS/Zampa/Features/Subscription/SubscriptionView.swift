import SwiftUI

struct SubscriptionView: View {
    @Environment(\.presentationMode) var presentationMode
    @EnvironmentObject var appState: AppState

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 32) {
                    // Header
                    VStack(spacing: 12) {
                        Image(systemName: "star.circle.fill")
                            .font(.system(size: 80))
                            .foregroundColor(.appPrimary)
                        
                        Text("Zampa Pro")
                            .font(.appLargeTitle)
                            .fontWeight(.bold)
                        
                        Text("Haz que tu negocio destaque y atrae a más clientes con herramientas exclusivas.")
                            .font(.appBody)
                            .foregroundColor(.appTextSecondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 32)
                    }
                    .padding(.top, 40)
                    
                    // Comparison Table
                    VStack(spacing: 24) {
                        PlanFeatureRow(feature: "Menús activos", free: "1", pro: "Ilimitados")
                        PlanFeatureRow(feature: "Fotos por menú", free: "1", pro: "Hasta 5")
                        PlanFeatureRow(feature: "Estadísticas", free: "7 días", pro: "30 días + Detalle")
                        PlanFeatureRow(feature: "Badge 'Pro'", free: "No", pro: "Sí", isHighlight: true)
                        PlanFeatureRow(feature: "Perfil destacado", free: "No", pro: "Sí", isHighlight: true)
                    }
                    .padding()
                    .background(RoundedRectangle(cornerRadius: 20).fill(Color.appSurface))
                    .shadow(color: Color.black.opacity(0.05), radius: 10)
                    .padding(.horizontal)
                    
                    if appState.isPremium {
                        VStack(spacing: 16) {
                            Text("¡Ya eres miembro Pro!")
                                .font(.appHeadline)
                                .foregroundColor(.appPrimary)

                            Text("Tu suscripción está activa. Gracias por confiar en Zampa.")
                                .font(.appSubheadline)
                                .foregroundColor(.appTextSecondary)
                                .multilineTextAlignment(.center)
                        }
                        .padding()
                    } else {
                        // Pagos pendientes de integrar (RevenueCat + StoreKit / Play Billing).
                        // Hasta entonces, el upgrade está deshabilitado para evitar el bypass
                        // que permitía marcarse como Pro sin pagar.
                        VStack(spacing: 16) {
                            Text("Próximamente")
                                .font(.appHeadline)
                                .foregroundColor(.appTextSecondary)
                            Text("Estamos integrando los pagos in-app de App Store. Muy pronto podrás suscribirte a Zampa Pro desde la app.")
                                .font(.appSubheadline)
                                .foregroundColor(.appTextSecondary)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 32)
                        }
                        .padding()
                    }
                }
                .padding(.bottom, 40)
            }
            .background(Color.appBackground.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Cerrar") {
                        presentationMode.wrappedValue.dismiss()
                    }
                }
            }
        }
    }
    
}

struct PlanFeatureRow: View {
    let feature: String
    let free: String
    let pro: String
    var isHighlight: Bool = false
    
    var body: some View {
        HStack {
            Text(feature)
                .font(.appSubheadline)
                .foregroundColor(.appTextPrimary)
            Spacer()
            HStack(spacing: 20) {
                Text(free)
                    .font(.caption)
                    .foregroundColor(.appTextSecondary)
                    .frame(width: 80, alignment: .trailing)
                
                Text(pro)
                    .font(.appSubheadline)
                    .fontWeight(isHighlight ? .bold : .medium)
                    .foregroundColor(isHighlight ? .appPrimary : .appTextPrimary)
                    .frame(width: 80, alignment: .trailing)
            }
        }
    }
}

#Preview {
    SubscriptionView()
        .environmentObject(AppState())
}
