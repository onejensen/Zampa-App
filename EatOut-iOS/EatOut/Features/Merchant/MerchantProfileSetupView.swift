import SwiftUI
import PhotosUI
import CoreLocation

/// Vista para que el merchant complete o edite su perfil
struct MerchantProfileSetupView: View {
    @EnvironmentObject var appState: AppState

    /// Cuando se pasa un perfil existente, la vista opera en modo edición
    var existingProfile: Merchant? = nil

    @State private var businessName: String = ""
    @State private var phone: String = ""
    @State private var description: String = ""
    @State private var addressText: String = ""
    @State private var acceptsReservations: Bool = false
    @State private var selectedCuisines: [String] = []
    @State private var selectedPhotoItem: PhotosPickerItem?
    @State private var coverImage: UIImage?

    @State private var isLoading: Bool = false
    @State private var showingError: Bool = false
    @State private var errorMessage: String = ""

    // Horario
    @State private var scheduleEntries: [EditableScheduleEntry] = EditableScheduleEntry.defaultWeek()

    @State private var availableCuisines: [String] = []

    private var isEditMode: Bool { existingProfile != nil }

    private var isValid: Bool {
        !businessName.isEmpty && !phone.isEmpty && !addressText.isEmpty
    }
    
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    // Header
                    VStack(alignment: .leading, spacing: 8) {
                        Text(isEditMode ? "Editar perfil" : "Completa tu perfil")
                            .font(.appHeadline)
                            .foregroundColor(.appTextPrimary)
                        Text(isEditMode ? "Actualiza los datos de tu restaurante." : "Configura tu restaurante para empezar a publicar menús del día.")
                            .font(.appBody)
                            .foregroundColor(.appTextSecondary)
                    }

                    // Cover Photo
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Foto de portada")
                            .font(.appSubheadline)
                            .fontWeight(.bold)
                        
                        PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
                            if let image = coverImage {
                                Image(uiImage: image)
                                    .resizable()
                                    .aspectRatio(contentMode: .fill)
                                    .frame(height: 180)
                                    .cornerRadius(16)
                                    .clipped()
                            } else {
                                RoundedRectangle(cornerRadius: 16)
                                    .fill(Color.appInputBackground)
                                    .frame(height: 180)
                                    .overlay(
                                        VStack(spacing: 8) {
                                            Image(systemName: "camera.fill")
                                                .font(.system(size: 30))
                                            Text("Añadir foto")
                                                .font(.appBody)
                                        }
                                        .foregroundColor(.appTextSecondary)
                                    )
                            }
                        }
                    }
                    
                    // Business name + Contact Info
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Información del restaurante")
                            .font(.appSubheadline)
                            .fontWeight(.bold)

                        CustomTextField(title: "Nombre del restaurante *", text: $businessName, icon: "fork.knife")

                        CustomTextField(title: "Teléfono *", text: $phone, icon: "phone")
                            .keyboardType(.phonePad)

                        CustomTextField(title: "Dirección *", text: $addressText, icon: "mappin")
                    }
                    
                    // Description
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Descripción")
                            .font(.appSubheadline)
                            .fontWeight(.bold)
                        
                        TextEditor(text: $description)
                            .font(.appBody)
                            .foregroundColor(.appTextPrimary)
                            .frame(minHeight: 80)
                            .padding(8)
                            .background(RoundedRectangle(cornerRadius: 12).fill(Color.appInputBackground))
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(Color.clear)
                            )
                    }
                    
                    // Cuisine Types
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Tipo de cocina")
                            .font(.appSubheadline)
                            .fontWeight(.bold)
                        
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(availableCuisines, id: \.self) { cuisine in
                                    CategoryPill(title: cuisine, isSelected: selectedCuisines.contains(cuisine)) {
                                        if selectedCuisines.contains(cuisine) {
                                            selectedCuisines.removeAll { $0 == cuisine }
                                        } else {
                                            selectedCuisines.append(cuisine)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // Schedule
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Horario")
                            .font(.appSubheadline)
                            .fontWeight(.bold)
                        
                        ForEach($scheduleEntries) { $entry in
                            HStack {
                                Toggle(isOn: $entry.isOpen) {
                                    Text(entry.dayName)
                                        .font(.appBody)
                                        .frame(width: 80, alignment: .leading)
                                }
                                .toggleStyle(SwitchToggleStyle(tint: .appPrimary))
                                
                                if entry.isOpen {
                                    TextField("Abre", text: $entry.openTime)
                                        .font(.appBody)
                                        .frame(width: 55)
                                        .padding(6)
                                        .background(Color.appInputBackground)
                                        .cornerRadius(8)
                                    
                                    Text("–")
                                        .foregroundColor(.appTextSecondary)
                                    
                                    TextField("Cierra", text: $entry.closeTime)
                                        .font(.appBody)
                                        .frame(width: 55)
                                        .padding(6)
                                        .background(Color.appInputBackground)
                                        .cornerRadius(8)
                                }
                            }
                            .padding(.vertical, 4)
                        }
                    }
                    
                    // Reservations
                    Toggle(isOn: $acceptsReservations) {
                        HStack(spacing: 12) {
                            Image(systemName: "calendar.badge.checkmark")
                                .foregroundColor(.appPrimary)
                            Text("Acepta reservas")
                                .font(.appBody)
                        }
                    }
                    .toggleStyle(SwitchToggleStyle(tint: .appPrimary))
                    .padding()
                    .background(RoundedRectangle(cornerRadius: 12).fill(Color.appInputBackground))
                    
                    // Save Button
                    Button(action: { saveProfile() }) {
                        if isLoading {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                        } else {
                            Text(isEditMode ? "Guardar cambios" : "Guardar y continuar")
                        }
                    }
                    .buttonStyle(AppDesign.ButtonStyle(isPrimary: true, isDisabled: !isValid || isLoading))
                    .disabled(!isValid || isLoading)

                    if !isEditMode {
                        Button(action: {
                            appState.needsMerchantSetup = false
                        }) {
                            Text("Completar más tarde")
                                .font(.appButton)
                                .foregroundColor(.appTextSecondary)
                                .frame(maxWidth: .infinity)
                        }
                        .padding(.bottom, 24)
                    }
                }
                .padding(24)
            }
            .navigationTitle(isEditMode ? "Editar Restaurante" : "Mi Restaurante")
            .navigationBarTitleDisplayMode(.inline)
            .background(Color.appBackground)
            .onAppear {
                // Pre-populate when editing
                if let p = existingProfile {
                    businessName = p.name
                    phone = p.phone ?? ""
                    addressText = p.address?.formatted ?? p.addressText ?? ""
                    description = p.shortDescription ?? ""
                    acceptsReservations = p.acceptsReservations
                    selectedCuisines = p.cuisineTypes ?? []
                    if let existing = p.schedule, !existing.isEmpty {
                        scheduleEntries = EditableScheduleEntry.defaultWeek().map { entry in
                            if let match = existing.first(where: { $0.day == entry.dayKey }) {
                                return EditableScheduleEntry(dayKey: entry.dayKey, dayName: entry.dayName, isOpen: true, openTime: match.open, closeTime: match.close)
                            }
                            return EditableScheduleEntry(dayKey: entry.dayKey, dayName: entry.dayName, isOpen: false, openTime: entry.openTime, closeTime: entry.closeTime)
                        }
                    }
                } else {
                    businessName = appState.currentUser?.name ?? ""
                }
                Task {
                    if let types = try? await FirebaseService.shared.fetchCuisineTypes() {
                        availableCuisines = types.map { $0.name }
                    }
                }
            }
            .alert("Error", isPresented: $showingError) {
                Button("OK", role: .cancel) { }
            } message: {
                Text(errorMessage)
            }
            .onChange(of: selectedPhotoItem) { _, newItem in
                if let newItem {
                    Task {
                        if let data = try? await newItem.loadTransferable(type: Data.self),
                           let image = UIImage(data: data) {
                            coverImage = image
                        }
                    }
                }
            }
        }
    }
    
    private func saveProfile() {
        guard let userId = appState.currentUser?.id else { return }
        isLoading = true
        
        Task {
            do {
                // Upload cover if selected
                var coverUrl: String? = nil
                if let image = coverImage,
                   let imageData = image.jpegData(compressionQuality: 0.8) {
                    coverUrl = try await FirebaseService.shared.uploadImage(
                        data: imageData,
                        path: "businesses/\(userId)/cover.jpg"
                    )
                }
                
                // Build schedule
                let schedule = scheduleEntries.filter(\.isOpen).map { entry in
                    ScheduleEntry(day: entry.dayKey, open: entry.openTime, close: entry.closeTime)
                }
                
                // Geocode the address to get real coordinates
                let geocodedAddress = await geocode(addressText)

                // Build merchant profile
                let merchant = Merchant(
                    id: userId,
                    name: businessName.trimmingCharacters(in: .whitespaces),
                    phone: phone,
                    address: geocodedAddress,
                    schedule: schedule.isEmpty ? nil : schedule,
                    cuisineTypes: selectedCuisines.isEmpty ? nil : selectedCuisines,
                    acceptsReservations: acceptsReservations,
                    shortDescription: description.isEmpty ? nil : description,
                    coverPhotoUrl: coverUrl
                )
                
                try await FirebaseService.shared.updateMerchantProfile(merchant)
                
                await MainActor.run {
                    isLoading = false
                    appState.merchantProfile = merchant
                    appState.needsMerchantSetup = false
                }
            } catch {
                await MainActor.run {
                    isLoading = false
                    errorMessage = error.localizedDescription
                    showingError = true
                }
            }
        }
    }

    /// Geocodes a free-form address string. Returns a MerchantAddress with real
    /// coordinates if successful, or lat/lng = 0 as a safe fallback.
    private func geocode(_ address: String) async -> MerchantAddress {
        await withCheckedContinuation { continuation in
            CLGeocoder().geocodeAddressString(address) { placemarks, _ in
                if let loc = placemarks?.first?.location {
                    continuation.resume(returning: MerchantAddress(
                        formatted: address,
                        lat: loc.coordinate.latitude,
                        lng: loc.coordinate.longitude,
                        placeId: nil
                    ))
                } else {
                    continuation.resume(returning: MerchantAddress(
                        formatted: address, lat: 0, lng: 0, placeId: nil
                    ))
                }
            }
        }
    }
}

// MARK: - Editable Schedule Entry

struct EditableScheduleEntry: Identifiable {
    let id = UUID()
    let dayKey: String
    let dayName: String
    var isOpen: Bool
    var openTime: String
    var closeTime: String
    
    static func defaultWeek() -> [EditableScheduleEntry] {
        let days: [(key: String, name: String)] = [
            ("monday", "Lunes"),
            ("tuesday", "Martes"),
            ("wednesday", "Miércoles"),
            ("thursday", "Jueves"),
            ("friday", "Viernes"),
            ("saturday", "Sábado"),
            ("sunday", "Domingo")
        ]
        return days.map { day in
            EditableScheduleEntry(
                dayKey: day.key,
                dayName: day.name,
                isOpen: day.key != "sunday",
                openTime: "10:00",
                closeTime: "23:00"
            )
        }
    }
}

#Preview {
    MerchantProfileSetupView()
        .environmentObject(AppState())
}
