import SwiftUI

struct LocationConfigView: View {
    @Environment(\.presentationMode) var presentationMode
    @EnvironmentObject var appState: AppState
    @ObservedObject var localization = LocalizationManager.shared
    
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
                        .font(.custom("Sora-Regular", size: 80))
                        .foregroundColor(.appPrimary)
                    
                    VStack(spacing: 12) {
                        Text(localization.t("location_title"))
                            .font(.appHeadline)
                            .multilineTextAlignment(.center)

                        Text(localization.t("location_subtitle"))
                            .font(.appBody)
                            .foregroundColor(.appTextSecondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal)
                    }
                    
                    VStack(spacing: 8) {
                        Image(systemName: "exclamationmark.triangle")
                            .foregroundColor(.appPrimary)
                        Text(localization.t("location_disclaimer"))
                            .font(.appCaption)
                            .foregroundColor(.appTextSecondary)
                    }
                    .padding()
                    .background(RoundedRectangle(cornerRadius: 8).fill(Color.appPrimary.opacity(0.05)))
                    
                    Button(action: {
                        appState.locationManager.requestAuthorization()
                    }) {
                        Text(localization.t("location_activate"))
                    }
                    .buttonStyle(AppDesign.ButtonStyle(isPrimary: true))
                    
                    Button(localization.t("location_manual")) {
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
