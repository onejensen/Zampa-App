import SwiftUI
import PhotosUI

// MARK: - Sheet enum

private enum ProfileSheet: Identifiable {
    case camera, gallery, editProfile
    case merchantStats, merchantSubscription, editBusiness
    case deleteAccount

    var id: Int { hashValue }
}

// MARK: - ProfileView

struct ProfileView: View {
    @EnvironmentObject var appState: AppState
    @State private var profileImage: UIImage?       // preview local durante la subida
    @State private var activeSheet: ProfileSheet?
    @State private var pendingPickerAction: PickerAction?
    @State private var showingSourceDialog = false
    @State private var editNameText = ""
    @State private var isSavingName = false
    @State private var isUploadingPhoto = false
    @State private var rawPickedImage: UIImage?      // imagen bruta del picker, antes del crop
    @State private var croppedPreviewImage: UIImage? // preview inmediato post-crop mientras sube
    @State private var showingCrop = false
    @State private var uploadErrorMessage: String?

    private enum PickerAction { case camera, gallery }

    @ViewBuilder
    private var avatarView: some View {
        ZStack(alignment: .bottomTrailing) {
            Group {
                if let image = croppedPreviewImage {
                    // Preview local inmediato tras confirmar el crop
                    Image(uiImage: image)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                } else if let urlStr = appState.currentUser?.photoUrl,
                          let url = URL(string: urlStr) {
                    CachedAsyncImage(url: url) { img in
                        img.resizable().aspectRatio(contentMode: .fill)
                    } placeholder: {
                        Circle().fill(Color.appInputBackground)
                    }
                } else {
                    Circle()
                        .fill(Color.appInputBackground)
                        .overlay(
                            Image(systemName: "person.fill")
                                .font(.system(size: 40))
                                .foregroundColor(.appTextSecondary)
                        )
                }
            }
            .frame(width: 100, height: 100)
            .clipShape(Circle())

            Image(systemName: "camera.fill")
                .padding(8)
                .background(Color.appPrimary)
                .foregroundColor(.white)
                .clipShape(Circle())
        }
        .contentShape(Circle())
        .onTapGesture { showingSourceDialog = true }
    }

    // MARK: - Header section
    @ViewBuilder
    private var headerSection: some View {
        Section {
            VStack(spacing: 16) {
                avatarView

                VStack(spacing: 4) {
                    Text(appState.currentUser?.name ?? "Usuario")
                        .font(.appSubheadline)
                        .fontWeight(.bold)
                        .foregroundColor(.appTextPrimary)

                    Text(appState.currentUser?.email ?? "usuario@ejemplo.com")
                        .font(.appBody)
                        .foregroundColor(.appTextSecondary)

                    Button("Editar nombre") {
                        editNameText = appState.currentUser?.name ?? ""
                        activeSheet = .editProfile
                    }
                    .font(.system(size: 13))
                    .foregroundColor(.appPrimary)
                    .padding(.top, 4)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .listRowBackground(Color.clear)
        }
    }

    // MARK: - Preferences section
    @ViewBuilder
    private var preferencesSection: some View {
        Section(header: Text("Preferencias").font(.caption).foregroundColor(.appTextSecondary)) {
            NavigationLink(destination: DietaryPreferencesView()) {
                ProfileMenuRowContent(icon: "leaf.fill", title: "Preferencias Alimentarias", color: .green)
            }
            NavigationLink(destination: NotificationPreferencesView()) {
                ProfileMenuRowContent(icon: "bell.fill", title: "Notificaciones", color: .orange)
            }
            NavigationLink(destination: CurrencyPreferenceView()) {
                HStack(spacing: 16) {
                    Image(systemName: "dollarsign.circle.fill")
                        .foregroundColor(.appPrimary)
                        .frame(width: 24)
                    Text("Moneda")
                        .font(.appBody)
                        .foregroundColor(.appTextPrimary)
                    Spacer()
                    Text(currentCurrencyLabel)
                        .font(.appCaption)
                        .foregroundColor(.appTextSecondary)
                }
            }
            HStack(spacing: 16) {
                Image(systemName: "circle.lefthalf.filled")
                    .foregroundColor(.purple)
                    .frame(width: 24)
                Text("Tema")
                    .font(.appBody)
                    .foregroundColor(.appTextPrimary)
                Spacer()
                Picker("", selection: $appState.appColorScheme) {
                    ForEach(ColorSchemePreference.allCases, id: \.self) { pref in
                        Text(pref.label).tag(pref)
                    }
                }
                .pickerStyle(.segmented)
                .frame(maxWidth: 180)
            }
        }
    }

    /// Etiqueta corta mostrada en la trailing de la fila Moneda: "EUR (€)".
    private var currentCurrencyLabel: String {
        let code = appState.currentUser?.currencyPreference ?? "EUR"
        let symbol: String
        switch code {
        case "EUR": symbol = "€"
        case "USD": symbol = "$"
        case "GBP": symbol = "£"
        case "JPY": symbol = "¥"
        case "CHF": symbol = "CHF"
        case "SEK", "NOK", "DKK": symbol = "kr"
        case "CAD": symbol = "C$"
        case "AUD": symbol = "A$"
        default:    symbol = code
        }
        return "\(code) (\(symbol))"
    }

    // MARK: - Merchant section
    @ViewBuilder
    private var merchantSection: some View {
        if appState.currentUser?.role == .comercio {
            Section(header: Text("Mi Restaurante").font(.caption).foregroundColor(.appTextSecondary)) {
                ProfileMenuRow(icon: "chart.bar.fill", title: "Estadísticas", color: .blue) {
                    activeSheet = .merchantStats
                }
                ProfileMenuRow(icon: "pencil.circle.fill", title: "Editar perfil del restaurante", color: .appPrimary) {
                    activeSheet = .editBusiness
                }
                ProfileMenuRow(icon: "star.fill", title: "Zampa Pro", color: .yellow) {
                    activeSheet = .merchantSubscription
                }
            }
        }
    }

    var body: some View {
        NavigationView {
            List {
                headerSection

                Section(header: Text("Mi Actividad").font(.caption).foregroundColor(.appTextSecondary)) {
                    ProfileMenuRow(icon: "heart.fill", title: "Favoritos", color: .red) {}
                    NavigationLink(destination: HistoryView()) {
                        ProfileMenuRowContent(icon: "clock.arrow.circlepath", title: "Historial", color: .blue)
                    }
                }

                preferencesSection
                merchantSection

                Section(header: Text("Más").font(.caption).foregroundColor(.appTextSecondary)) {
                    ProfileMenuRow(icon: "questionmark.circle.fill", title: "Ayuda y Soporte", color: .gray) {}
                    ProfileMenuRow(icon: "doc.text.fill", title: "Términos y Privacidad", color: .gray) {}
                }

                Section {
                    Button(action: { appState.logout() }) {
                        HStack {
                            Spacer()
                            Text("Cerrar Sesión")
                                .font(.appButton)
                                .foregroundColor(.red)
                            Spacer()
                        }
                    }
                }

                // Sólo clientes pueden eliminar su cuenta desde la app en v1.
                // Comercios deben contactar soporte (no hay botón oculto).
                if appState.currentUser?.role == .cliente {
                    Section {
                        Button(action: { activeSheet = .deleteAccount }) {
                            HStack {
                                Spacer()
                                Text("Eliminar cuenta")
                                    .font(.appButton)
                                    .foregroundColor(.red.opacity(0.7))
                                Spacer()
                            }
                        }
                    }
                }
            }
            .listStyle(InsetGroupedListStyle())
            .navigationTitle("Mi Perfil")
            .navigationBarTitleDisplayMode(.inline)

            // MARK: Un único sheet para todo
            .sheet(item: $activeSheet) { sheet in
                switch sheet {
                case .camera:
                    CameraImagePicker(image: $profileImage)
                case .gallery:
                    GalleryImagePicker(image: $profileImage)
                case .editProfile:
                    editProfileSheet
                case .merchantStats:
                    StatsView()
                case .merchantSubscription:
                    SubscriptionView()
                case .editBusiness:
                    MerchantProfileSetupView(existingProfile: appState.merchantProfile)
                case .deleteAccount:
                    DeleteAccountConfirmationSheet()
                        .environmentObject(appState)
                }
            }

            // MARK: Confirmation dialog
            .confirmationDialog("Cambiar foto de perfil", isPresented: $showingSourceDialog, titleVisibility: .visible) {
                Button("Cámara") { pendingPickerAction = .camera }
                Button("Galería") { pendingPickerAction = .gallery }
                Button("Cancelar", role: .cancel) { pendingPickerAction = nil }
            }
            // Interceptar imagen del picker: guardar para recortar, no subir aún
            .onChange(of: profileImage) { _, newImage in
                guard let image = newImage else { return }
                profileImage = nil
                rawPickedImage = image
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
                    showingCrop = true
                }
            }
            // Esperar a que el dialog termine de cerrarse antes de presentar el sheet
            .onChange(of: showingSourceDialog) { _, isShowing in
                guard !isShowing, let action = pendingPickerAction else { return }
                pendingPickerAction = nil
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                    activeSheet = action == .camera ? .camera : .gallery
                }
            }
            .alert("Error al subir foto", isPresented: Binding(
                get: { uploadErrorMessage != nil },
                set: { if !$0 { uploadErrorMessage = nil } }
            )) {
                Button("OK") { uploadErrorMessage = nil }
            } message: {
                Text(uploadErrorMessage ?? "")
            }
            .fullScreenCover(isPresented: $showingCrop) {
                if let image = rawPickedImage {
                    CropImageView(
                        sourceImage: image,
                        onConfirm: { cropped in
                            showingCrop = false
                            rawPickedImage = nil
                            croppedPreviewImage = cropped  // preview inmediato
                            Task {
                                do {
                                    try await FirebaseService.shared.uploadProfilePhoto(cropped)
                                    if let updated = try? await FirebaseService.shared.getCurrentUser() {
                                        // Pre-popular caché de disco con la imagen ya recortada
                                        if let urlStr = updated.photoUrl, let url = URL(string: urlStr) {
                                            ImageCache.shared[url] = cropped
                                        }
                                        await MainActor.run {
                                            appState.currentUser = updated
                                            croppedPreviewImage = nil
                                        }
                                    }
                                } catch {
                                    print("❌ uploadProfilePhoto error: \(error)")
                                    await MainActor.run {
                                        uploadErrorMessage = error.localizedDescription
                                    }
                                }
                            }
                        },
                        onCancel: {
                            showingCrop = false
                            rawPickedImage = nil
                        }
                    )
                }
            }
        }
    }

    // MARK: - Edit profile sheet view
    @ViewBuilder
    private var editProfileSheet: some View {
        NavigationView {
            Form {
                Section(header: Text("Nombre a mostrar")) {
                    TextField("Tu nombre", text: $editNameText)
                        .textContentType(.name)
                        .autocorrectionDisabled()
                }
            }
            .navigationTitle("Editar perfil")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") { activeSheet = nil }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Guardar") {
                        let trimmed = editNameText.trimmingCharacters(in: .whitespaces)
                        guard !trimmed.isEmpty else { return }
                        isSavingName = true
                        Task {
                            try? await FirebaseService.shared.updateUserName(trimmed)
                            if let updated = try? await FirebaseService.shared.getCurrentUser() {
                                await MainActor.run {
                                    appState.currentUser = updated
                                }
                            }
                            await MainActor.run {
                                isSavingName = false
                                activeSheet = nil
                            }
                        }
                    }
                    .disabled(isSavingName || editNameText.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }
}

// MARK: - Reusable row components

struct ProfileMenuRow: View {
    let icon: String
    let title: String
    let color: Color
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            ProfileMenuRowContent(icon: icon, title: title, color: color)
        }
    }
}

struct ProfileMenuRowContent: View {
    let icon: String
    let title: String
    let color: Color

    var body: some View {
        HStack(spacing: 16) {
            Image(systemName: icon)
                .foregroundColor(color)
                .frame(width: 24)
            Text(title)
                .font(.appBody)
                .foregroundColor(.appTextPrimary)
            Spacer()
        }
    }
}

// MARK: - Camera Picker

struct CameraImagePicker: UIViewControllerRepresentable {
    @Binding var image: UIImage?
    @Environment(\.dismiss) private var dismiss

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.cameraFlashMode = .off
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    class Coordinator: NSObject, UINavigationControllerDelegate, UIImagePickerControllerDelegate {
        let parent: CameraImagePicker
        init(_ parent: CameraImagePicker) { self.parent = parent }

        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            if let img = info[.originalImage] as? UIImage { parent.image = img }
            parent.dismiss()
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.dismiss()
        }
    }
}

// MARK: - Gallery Picker (PHPickerViewController)

struct GalleryImagePicker: UIViewControllerRepresentable {
    @Binding var image: UIImage?
    @Environment(\.dismiss) private var dismiss

    func makeUIViewController(context: Context) -> PHPickerViewController {
        var config = PHPickerConfiguration()
        config.filter = .images
        config.selectionLimit = 1
        let picker = PHPickerViewController(configuration: config)
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: PHPickerViewController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    class Coordinator: NSObject, PHPickerViewControllerDelegate {
        let parent: GalleryImagePicker
        init(_ parent: GalleryImagePicker) { self.parent = parent }

        func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
            parent.dismiss()
            guard let provider = results.first?.itemProvider,
                  provider.canLoadObject(ofClass: UIImage.self) else { return }
            provider.loadObject(ofClass: UIImage.self) { [weak self] object, _ in
                if let img = object as? UIImage {
                    DispatchQueue.main.async { self?.parent.image = img }
                }
            }
        }
    }
}

// MARK: - Delete account confirmation sheet

private struct DeleteAccountConfirmationSheet: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject var appState: AppState
    @State private var typedConfirmation = ""
    @State private var isDeleting = false
    @State private var errorMessage: String?

    private var isValid: Bool { typedConfirmation == "ELIMINAR" }

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text("¿Eliminar tu cuenta?")
                        .font(.appHeadline)
                        .foregroundColor(.appTextPrimary)

                    Text("Esta acción programará la eliminación definitiva de tu cuenta en 30 días. Durante ese tiempo podrás recuperarla iniciando sesión. Pasado el plazo, se borrarán para siempre:")
                        .font(.appBody)
                        .foregroundColor(.appTextSecondary)

                    VStack(alignment: .leading, spacing: 8) {
                        Text("• Tu perfil y foto")
                        Text("• Tus favoritos")
                        Text("• Tu historial")
                    }
                    .font(.appBody)
                    .foregroundColor(.appTextPrimary)

                    Divider()

                    Text("Para confirmar, escribe ELIMINAR:")
                        .font(.appBody)
                        .foregroundColor(.appTextSecondary)

                    TextField("ELIMINAR", text: $typedConfirmation)
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                        .disabled(isDeleting)

                    Spacer(minLength: 32)

                    Button(action: performDelete) {
                        HStack {
                            Spacer()
                            if isDeleting {
                                ProgressView().tint(.white)
                            } else {
                                Text("Eliminar")
                            }
                            Spacer()
                        }
                    }
                    .buttonStyle(AppDesign.ButtonStyle(isPrimary: true, isDisabled: !isValid || isDeleting))
                    .disabled(!isValid || isDeleting)
                }
                .padding(24)
            }
            .navigationTitle("Eliminar cuenta")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") { dismiss() }
                        .disabled(isDeleting)
                }
            }
            .alert("Error", isPresented: Binding(
                get: { errorMessage != nil },
                set: { if !$0 { errorMessage = nil } }
            )) {
                Button("OK") { errorMessage = nil }
            } message: {
                Text(errorMessage ?? "")
            }
        }
    }

    private func performDelete() {
        isDeleting = true
        Task {
            do {
                try await FirebaseService.shared.requestAccountDeletion()
                // Per spec §3.2: cerrar sesión tras el batch y volver al auth entry.
                // Cuando el usuario inicie sesión de nuevo, ContentView detectará
                // deletedAt y mostrará la pantalla de recuperación.
                await MainActor.run {
                    isDeleting = false
                    dismiss()
                    appState.logout()
                }
            } catch {
                await MainActor.run {
                    isDeleting = false
                    errorMessage = error.localizedDescription
                }
            }
        }
    }
}

#Preview {
    ProfileView()
        .environmentObject(AppState())
}
