import SwiftUI

struct SubscriptionView: View {
    @Environment(\.presentationMode) var presentationMode
    @EnvironmentObject var appState: AppState
    @State private var isLoading = false
    
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 32) {
                    // Header
                    VStack(spacing: 12) {
                        Image(systemName: "star.circle.fill")
                            .font(.system(size: 80))
                            .foregroundColor(.appPrimary)
                        
                        Text("EatOut Pro")
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
                            
                            Text("Tu suscripción está activa. Gracias por confiar en EatOut.")
                                .font(.appSubheadline)
                                .foregroundColor(.appTextSecondary)
                                .multilineTextAlignment(.center)
                        }
                        .padding()
                    } else {
                        // Action Button
                        VStack(spacing: 16) {
                            Button(action: upgradeToPro) {
                                if isLoading {
                                    ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white))
                                } else {
                                    Text("Actualizar a Pro — 9.99€ / mes")
                                        .fontWeight(.bold)
                                }
                            }
                            .buttonStyle(AppDesign.ButtonStyle(isPrimary: true))
                            .disabled(isLoading)
                            
                            Text("Facturación mensual. Cancela en cualquier momento.")
                                .font(.caption2)
                                .foregroundColor(.appTextSecondary)
                        }
                        .padding(.horizontal, 40)
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
    
    private func upgradeToPro() {
        guard let userId = appState.currentUser?.id else { return }
        isLoading = true
        
        Task {
            do {
                // Simulación de pasarela de pago / StoreKit
                try await Task.sleep(nanoseconds: 2 * 1_000_000_000)
                
                // Actualizar en Firestore
                let db = FirebaseService.shared.db
                try await db.collection("businesses").document(userId).updateData([
                    "planTier": "pro"
                ])
                
                // Crear el documento de la suscripción
                _ = try await FirebaseService.shared.createSubscription(businessId: userId, type: .monthly)
                
                await MainActor.run {
                    appState.isPremium = true
                    isLoading = false
                    // Opcional: Mostrar éxito
                }
            } catch {
                print("Error upgrading: \(error)")
                await MainActor.run { isLoading = false }
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
