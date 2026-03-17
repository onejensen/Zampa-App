import SwiftUI
import PhotosUI

// MARK: - Sheet enum

private enum ProfileSheet: Identifiable {
    case camera, gallery, editProfile
    case merchantStats, merchantSubscription, editBusiness

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

    var body: some View {
        NavigationView {
            List {
                // MARK: Header
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

                // MARK: Actividad
                Section(header: Text("Mi Actividad").font(.caption).foregroundColor(.appTextSecondary)) {
                    ProfileMenuRow(icon: "heart.fill", title: "Favoritos", color: .red) {}
                    ProfileMenuRow(icon: "clock.arrow.circlepath", title: "Historial de Reservas", color: .blue) {}
                }

                // MARK: Preferencias
                Section(header: Text("Preferencias").font(.caption).foregroundColor(.appTextSecondary)) {
                    NavigationLink(destination: DietaryPreferencesView()) {
                        ProfileMenuRowContent(icon: "leaf.fill", title: "Preferencias Alimentarias", color: .green)
                    }
                    ProfileMenuRow(icon: "bell.fill", title: "Notificaciones", color: .orange) {}
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

                // MARK: Mi Restaurante
                if appState.currentUser?.role == .comercio {
                    Section(header: Text("Mi Restaurante").font(.caption).foregroundColor(.appTextSecondary)) {
                        ProfileMenuRow(icon: "chart.bar.fill", title: "Estadísticas", color: .blue) {
                            activeSheet = .merchantStats
                        }
                        ProfileMenuRow(icon: "pencil.circle.fill", title: "Editar perfil del restaurante", color: .appPrimary) {
                            activeSheet = .editBusiness
                        }
                        ProfileMenuRow(icon: "star.fill", title: "EatOut Pro", color: .yellow) {
                            activeSheet = .merchantSubscription
                        }
                    }
                }

                // MARK: Más
                Section(header: Text("Más").font(.caption).foregroundColor(.appTextSecondary)) {
                    ProfileMenuRow(icon: "questionmark.circle.fill", title: "Ayuda y Soporte", color: .gray) {}
                    ProfileMenuRow(icon: "doc.text.fill", title: "Términos y Privacidad", color: .gray) {}
                }

                // MARK: Sesión
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

#Preview {
    ProfileView()
        .environmentObject(AppState())
}
