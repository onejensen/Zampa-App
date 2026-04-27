import SwiftUI
import PhotosUI

// MARK: - Sheet enum

private enum ProfileSheet: Identifiable {
    case camera, gallery, editProfile
    case merchantStats, merchantSubscription, editBusiness
    case deleteAccount
    case legal

    var id: Int { hashValue }
}

// MARK: - ProfileView

struct ProfileView: View {
    @EnvironmentObject var appState: AppState
    @EnvironmentObject var tourManager: TourManager
    @ObservedObject var localization = LocalizationManager.shared
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
                                .font(.custom("Sora-Regular", size: 32))
                                .foregroundColor(.appTextSecondary)
                        )
                }
            }
            .frame(width: 72, height: 72)
            .clipShape(Circle())
            .overlay(Circle().stroke(Color.white, lineWidth: 3))
            .shadow(color: .black.opacity(0.1), radius: 4, x: 0, y: 2)

            Image(systemName: "camera.fill")
                .font(.custom("Sora-Regular", size: 10))
                .padding(6)
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
            HStack(spacing: 14) {
                avatarView

                VStack(alignment: .leading, spacing: 4) {
                    Text(appState.currentUser?.name ?? "Usuario")
                        .font(.appSubheadline)
                        .fontWeight(.bold)
                        .foregroundColor(.appTextPrimary)

                    Text(appState.currentUser?.email ?? "")
                        .font(.appCaption)
                        .foregroundColor(.appTextSecondary)

                    Button(localization.t("profile_edit_name")) {
                        editNameText = appState.currentUser?.name ?? ""
                        activeSheet = .editProfile
                    }
                    .font(.custom("Sora-Regular", size: 13))
                    .foregroundColor(.appPrimary)
                    .padding(.top, 2)
                }

                Spacer()
            }
            .padding(.vertical, 8)
            .listRowBackground(Color.clear)
        }
    }

    // MARK: - Preferences section
    @ViewBuilder
    private var preferencesSection: some View {
        Section(header: Text(localization.t("profile_section_preferences")).font(.appCaption).foregroundColor(.appTextSecondary)) {
            NavigationLink(destination: DietaryPreferencesView()) {
                ProfileMenuRowContent(icon: "leaf.fill", title: localization.t("profile_dietary"), color: .green)
            }
            .listRowBackground(Color.appSurface)
            NavigationLink(destination: NotificationPreferencesView()) {
                ProfileMenuRowContent(icon: "bell.fill", title: localization.t("profile_notifications"), color: .orange)
            }
            .listRowBackground(Color.appSurface)
            NavigationLink(destination: CurrencyPreferenceView()) {
                HStack(spacing: 16) {
                    Image(systemName: "dollarsign.circle.fill")
                        .foregroundColor(.appPrimary)
                        .frame(width: 24)
                    Text(localization.t("profile_currency"))
                        .font(.appBody)
                        .foregroundColor(.appTextPrimary)
                    Spacer()
                    Text(currentCurrencyLabel)
                        .font(.appCaption)
                        .foregroundColor(.appTextSecondary)
                }
            }
            .listRowBackground(Color.appSurface)
            NavigationLink(destination: LanguagePickerView()) {
                HStack(spacing: 16) {
                    Image(systemName: "globe")
                        .foregroundColor(.blue)
                        .frame(width: 24)
                    Text(localization.t("profile_language"))
                        .font(.appBody)
                        .foregroundColor(.appTextPrimary)
                    Spacer()
                    Text(localization.resolvedLanguageNativeName)
                        .font(.appCaption)
                        .foregroundColor(.appTextSecondary)
                }
            }
            .listRowBackground(Color.appSurface)
            HStack(spacing: 16) {
                Image(systemName: "circle.lefthalf.filled")
                    .foregroundColor(.purple)
                    .frame(width: 24)
                Text(localization.t("profile_theme"))
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
            .listRowBackground(Color.appSurface)
            Button {
                if let uid = appState.currentUser?.id {
                    tourManager.restart(for: uid, isMerchant: appState.currentUser?.role == .comercio)
                }
            } label: {
                ProfileMenuRowContent(icon: "questionmark.circle.fill", title: localization.t("profile_restart_tour"), color: .indigo)
            }
            .listRowBackground(Color.appSurface)
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
            Section(header: Text(localization.t("profile_section_restaurant")).font(.appCaption).foregroundColor(.appTextSecondary)) {
                ProfileMenuRow(icon: "chart.bar.fill", title: localization.t("profile_stats"), color: .blue) {
                    activeSheet = .merchantStats
                }
                .listRowBackground(Color.appSurface)
                ProfileMenuRow(icon: "pencil.circle.fill", title: localization.t("profile_edit_restaurant"), color: .appPrimary) {
                    activeSheet = .editBusiness
                }
                .listRowBackground(Color.appSurface)
                ProfileMenuRow(icon: "star.fill", title: localization.t("profile_zampa_pro"), color: .yellow) {
                    activeSheet = .merchantSubscription
                }
                .listRowBackground(Color.appSurface)
            }
        }
    }

    var body: some View {
        NavigationView {
            List {
                headerSection

                preferencesSection
                merchantSection

                Section(header: Text(localization.t("profile_section_more")).font(.appCaption).foregroundColor(.appTextSecondary)) {
                    ProfileMenuRow(icon: "questionmark.circle.fill", title: localization.t("profile_help"), color: .gray) {
                        if let url = URL(string: "https://www.getzampa.com/#faq") {
                            UIApplication.shared.open(url)
                        }
                    }
                    .listRowBackground(Color.appSurface)
                    ProfileMenuRow(icon: "doc.text.fill", title: localization.t("profile_terms"), color: .gray) {
                        activeSheet = .legal
                    }
                    .listRowBackground(Color.appSurface)
                }

                Section {
                    Button(action: { appState.logout() }) {
                        HStack {
                            Spacer()
                            Text(localization.t("profile_logout"))
                                .font(.appButton)
                                .foregroundColor(.red)
                            Spacer()
                        }
                    }
                    .listRowBackground(Color.appSurface)
                }

                // Sólo clientes pueden eliminar su cuenta desde la app en v1.
                // Comercios deben contactar soporte (no hay botón oculto).
                if appState.currentUser?.role == .cliente {
                    Section {
                        Button(action: { activeSheet = .deleteAccount }) {
                            HStack {
                                Spacer()
                                Text(localization.t("profile_delete_account"))
                                    .font(.appButton)
                                    .foregroundColor(.red.opacity(0.7))
                                Spacer()
                            }
                        }
                        .listRowBackground(Color.appSurface)
                    }
                }
            }
            .listStyle(InsetGroupedListStyle())
            .scrollContentBackground(.hidden)
            .background(Color.appBackground)
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
                case .legal:
                    LegalView()
                }
            }

            // MARK: Confirmation dialog
            .confirmationDialog(localization.t("profile_change_photo"), isPresented: $showingSourceDialog, titleVisibility: .visible) {
                Button(localization.t("profile_camera")) { pendingPickerAction = .camera }
                Button(localization.t("profile_gallery")) { pendingPickerAction = .gallery }
                Button(localization.t("common_cancel"), role: .cancel) { pendingPickerAction = nil }
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
            .alert(localization.t("profile_photo_error"), isPresented: Binding(
                get: { uploadErrorMessage != nil },
                set: { if !$0 { uploadErrorMessage = nil } }
            )) {
                Button(localization.t("common_ok")) { uploadErrorMessage = nil }
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
                Section(header: Text(localization.t("profile_display_name"))) {
                    TextField(localization.t("profile_your_name"), text: $editNameText)
                        .textContentType(.name)
                        .autocorrectionDisabled()
                }
            }
            .navigationTitle(localization.t("profile_edit_profile"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(localization.t("common_cancel")) { activeSheet = nil }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(localization.t("profile_save")) {
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
    @ObservedObject var localization = LocalizationManager.shared
    @State private var typedConfirmation = ""
    @State private var isDeleting = false
    @State private var errorMessage: String?

    private var isValid: Bool { typedConfirmation == localization.t("profile_delete_confirm_word") }

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text(localization.t("profile_delete_title"))
                        .font(.appHeadline)
                        .foregroundColor(.appTextPrimary)

                    Text(localization.t("profile_delete_body"))
                        .font(.appBody)
                        .foregroundColor(.appTextSecondary)

                    VStack(alignment: .leading, spacing: 8) {
                        Text(localization.t("profile_delete_item_profile"))
                        Text(localization.t("profile_delete_item_favorites"))
                        Text(localization.t("profile_delete_item_history"))
                    }
                    .font(.appBody)
                    .foregroundColor(.appTextPrimary)

                    Divider()

                    Text(localization.t("profile_delete_confirm_prompt"))
                        .font(.appBody)
                        .foregroundColor(.appTextSecondary)

                    TextField(localization.t("profile_delete_confirm_word"), text: $typedConfirmation)
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
                                Text(localization.t("profile_delete_button"))
                            }
                            Spacer()
                        }
                    }
                    .buttonStyle(AppDesign.ButtonStyle(isPrimary: true, isDisabled: !isValid || isDeleting))
                    .disabled(!isValid || isDeleting)
                }
                .padding(24)
            }
            .navigationTitle(localization.t("profile_delete_account"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(localization.t("common_cancel")) { dismiss() }
                        .disabled(isDeleting)
                }
            }
            .alert(localization.t("common_error"), isPresented: Binding(
                get: { errorMessage != nil },
                set: { if !$0 { errorMessage = nil } }
            )) {
                Button(localization.t("common_ok")) { errorMessage = nil }
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
