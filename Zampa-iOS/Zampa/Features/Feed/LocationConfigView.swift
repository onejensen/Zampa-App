import SwiftUI

struct LocationConfigView: View {
    @Environment(\.presentationMode) var presentationMode
    @EnvironmentObject var appState: AppState
    
    var body: some View {
        ZStack {
            // ... (keep background)
            Color.gray.opacity(0.1).ignoresSafeArea()
            
            VStack {
                // ... (header)
                HStack {
                    Spacer()
                    Button(action: { presentationMode.wrappedValue.dismiss() }) {
                        Image(systemName: "xmark")
                            .padding(12)
                            .background(Circle().fill(.white))
                            .foregroundColor(.black)
                            .shadow(radius: 4)
                    }
                }
                .padding()
                
                Spacer()
                
                // Content Card
                VStack(spacing: 24) {
                    Image(systemName: "mappin.circle.fill")
                        .font(.system(size: 80))
                        .foregroundColor(.appPrimary)
                    
                    VStack(spacing: 12) {
                        Text("Encuentra menús cerca de ti")
                            .font(.appHeadline)
                            .multilineTextAlignment(.center)
                        
                        Text("Activa los permisos de tu ubicación para mostrarte los mejores menús diarios ordenados por distancia.")
                            .font(.appBody)
                            .foregroundColor(.appTextSecondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal)
                    }
                    
                    VStack(spacing: 8) {
                        Image(systemName: "exclamationmark.triangle")
                            .foregroundColor(.appPrimary)
                        Text("Tu ubicación será compartida solo con este propósito.")
                            .font(.caption)
                            .foregroundColor(.appTextSecondary)
                    }
                    .padding()
                    .background(RoundedRectangle(cornerRadius: 8).fill(Color.appPrimary.opacity(0.05)))
                    
                    Button(action: {
                        appState.locationManager.requestAuthorization()
                    }) {
                        Text("Activar ubicación")
                    }
                    .buttonStyle(AppDesign.ButtonStyle(isPrimary: true))
                    
                    Button("Buscar manualmente") {
                        presentationMode.wrappedValue.dismiss()
                    }
                    .font(.appButton)
                    .foregroundColor(.appPrimary)
                }
                .padding(32)
                .background(Color.appSurface)
                .cornerRadius(32)
                .shadow(color: Color.black.opacity(0.1), radius: 20, x: 0, y: 10)
                .padding()
            }
        }
        .onReceive(appState.locationManager.$authorizationStatus) { status in
            if status == .authorizedWhenInUse || status == .authorizedAlways {
                presentationMode.wrappedValue.dismiss()
            }
        }
    }
}

#Preview {
    LocationConfigView()
}
