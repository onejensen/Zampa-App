import SwiftUI
import PhotosUI

struct EditMenuView: View {
    @Environment(\.dismiss) var dismiss
    @EnvironmentObject var appState: AppState
    
    let menu: Menu
    @StateObject private var viewModel = EditMenuViewModel()
    
    var body: some View {
        NavigationView {
            Form {
                infoSection
                tagsSection
                offerTypeSection
                recurringDaysSection
                dietarySection
                photoSection
                saveSection
                deleteSection
            }
            .navigationTitle("Editar Menú")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancelar") { dismiss() }
                }
            }
            .alert("Error", isPresented: $viewModel.showingError) {
                Button("OK", role: .cancel) { }
            } message: {
                Text(viewModel.errorMessage)
            }
            .alert("Eliminar Menú", isPresented: $viewModel.showingDeleteConfirm) {
                Button("Cancelar", role: .cancel) { }
                Button("Eliminar", role: .destructive) {
                    Task {
                        await viewModel.deleteMenu(menuId: menu.id)
                        if viewModel.isSuccess {
                            dismiss()
                        }
                    }
                }
            } message: {
                Text("¿Estás seguro de que quieres eliminar esta oferta? Los clientes ya no podrán verla.")
            }
            .onChange(of: viewModel.selectedPhotoItem) { _, newItem in
                if let newItem {
                    Task {
                        await viewModel.loadImage(from: newItem)
                    }
                }
            }
            .onAppear {
                viewModel.setup(with: menu)
            }
        }
    }

    private var infoSection: some View {
        Section(header: Text("Información del menú")) {
            TextField("", text: $viewModel.title, prompt: Text("Título").foregroundColor(.appTextSecondary))
                .foregroundColor(.appTextPrimary)
            TextField("", text: $viewModel.description, prompt: Text("Descripción").foregroundColor(.appTextSecondary), axis: .vertical)
                .foregroundColor(.appTextPrimary)
                .lineLimit(3...6)

            HStack {
                Text("Precio")
                Spacer()
                TextField("", value: $viewModel.price, format: .number, prompt: Text("0.00").foregroundColor(.appTextSecondary))
                    .foregroundColor(.appTextPrimary)
                    .keyboardType(.decimalPad)
                    .multilineTextAlignment(.trailing)
                Text("€")
            }
        }
    }

    private var tagsSection: some View {
        Section(header: Text("Tipo de cocina")) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(viewModel.availableTags, id: \.self) { tag in
                        CategoryPill(title: tag, isSelected: viewModel.selectedTags.contains(tag)) {
                            if viewModel.selectedTags.contains(tag) {
                                viewModel.selectedTags.removeAll { $0 == tag }
                            } else {
                                viewModel.selectedTags.append(tag)
                            }
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }
                .padding(.vertical, 8)
            }
        }
    }

    private var offerTypeSection: some View {
        Section(header: Text("Tipo de oferta")) {
            OfferDetailsSection(
                offerType: $viewModel.offerType,
                includesDrink: $viewModel.includesDrink,
                includesDessert: $viewModel.includesDessert,
                includesCoffee: $viewModel.includesCoffee,
                serviceTime: $viewModel.serviceTime
            )
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.clear)
        }
    }

    @ViewBuilder
    private var recurringDaysSection: some View {
        if viewModel.isPermanentMenu {
            Section(header: Text(LocalizationManager.shared.t("create_menu_recurring_days_title"))) {
                RecurringDaysPicker(
                    occupiedDays: viewModel.occupiedDays,
                    selectedDays: $viewModel.recurringDays
                )
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)
            }
        }
    }

    private var dietarySection: some View {
        Section(header: Text("Información dietética")) {
            DietaryInfoEditor(dietaryInfo: $viewModel.dietaryInfo)
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)
        }
    }

    private var photoSection: some View {
        Section(header: Text("Foto (Mantén la actual o cámbiala)")) {
            if let newImage = viewModel.selectedImage {
                Image(uiImage: newImage)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(maxHeight: 200)
                    .cornerRadius(8)
            } else if let firstPhotoUrl = menu.photoUrls.first, let url = URL(string: firstPhotoUrl) {
                CachedAsyncImage(url: url) { image in
                    image.resizable().aspectRatio(contentMode: .fit)
                } placeholder: {
                    ProgressView()
                }
                .frame(maxHeight: 200)
                .cornerRadius(8)
            }

            PhotosPicker(
                selection: $viewModel.selectedPhotoItem,
                matching: .images
            ) {
                Label("Cambiar foto", systemImage: "photo")
            }
        }
    }

    private var saveSection: some View {
        Section {
            Button(action: {
                Task {
                    await viewModel.updateMenu(menuId: menu.id)
                    if viewModel.isSuccess {
                        dismiss()
                    }
                }
            }) {
                HStack {
                    if viewModel.isLoading {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle())
                            .padding(.trailing, 4)
                    }
                    Text(viewModel.isLoading ? "Guardando..." : "Guardar cambios")
                }
                .frame(maxWidth: .infinity)
            }
            .disabled(viewModel.isLoading || !viewModel.isValid)
            .buttonStyle(AppDesign.ButtonStyle(isPrimary: true))
            .listRowBackground(Color.clear)
            .listRowInsets(EdgeInsets())
        }
    }

    private var deleteSection: some View {
        Section {
            Button(role: .destructive, action: {
                viewModel.showingDeleteConfirm = true
            }) {
                Text("Desactivar / Eliminar Menú")
                    .frame(maxWidth: .infinity)
            }
            .disabled(viewModel.isLoading)
        }
    }
}

@MainActor
class EditMenuViewModel: ObservableObject {
    @Published var title: String = ""
    @Published var description: String = ""
    @Published var price: Double = 0.0
    @Published var selectedPhotoItem: PhotosPickerItem?
    @Published var selectedImage: UIImage?
    @Published var selectedTags: [String] = []
    @Published var dietaryInfo: DietaryInfo = DietaryInfo()
    @Published var offerType: String? = nil
    @Published var includesDrink: Bool = false
    @Published var includesDessert: Bool = false
    @Published var includesCoffee: Bool = false
    @Published var serviceTime: String = "both"
    @Published var recurringDays: Set<Int> = []
    @Published var occupiedDays: Set<Int> = []
    @Published private(set) var isPermanentMenu: Bool = false
    private var editingMenuId: String = ""

    @Published var availableTags: [String] = []
    
    @Published var isLoading: Bool = false
    @Published var isSuccess: Bool = false
    @Published var showingError: Bool = false
    @Published var showingDeleteConfirm: Bool = false
    @Published var errorMessage: String = ""
    
    var isValid: Bool {
        guard !title.isEmpty && price > 0 else { return false }
        if isPermanentMenu { return !recurringDays.isEmpty }
        return true
    }
    
    func setup(with menu: Menu) {
        self.title = menu.title
        self.description = menu.description ?? ""
        self.price = menu.priceTotal
        self.selectedTags = menu.tags ?? []
        self.dietaryInfo = menu.dietaryInfo
        self.offerType = menu.offerType
        self.includesDrink = menu.includesDrink
        self.includesDessert = menu.includesDessert
        self.includesCoffee = menu.includesCoffee
        self.serviceTime = menu.serviceTime
        self.isPermanentMenu = menu.isPermanent
        self.editingMenuId = menu.id
        if menu.isPermanent {
            self.recurringDays = Set(menu.recurringDays ?? [])
            Task { await loadOccupiedDays() }
        }

        Task {
            if let types = try? await FirebaseService.shared.fetchCuisineTypes() {
                self.availableTags = types.map { $0.name }
            }
        }
    }

    @MainActor
    func loadOccupiedDays() async {
        guard let uid = FirebaseService.shared.currentFirebaseUser?.uid else { return }
        let all = (try? await FirebaseService.shared.getMenusByMerchant(merchantId: uid)) ?? []
        let activePermanents = all.filter { $0.isPermanent && $0.isActive && $0.id != editingMenuId }
        self.occupiedDays = Menu.occupiedDays(from: activePermanents)
    }
    
    func loadImage(from item: PhotosPickerItem) async {
        do {
            if let data = try? await item.loadTransferable(type: Data.self),
               let image = UIImage(data: data) {
                self.selectedImage = image
            }
        }
    }
    
    func updateMenu(menuId: String) async {
        guard isValid else { return }
        
        isLoading = true
        defer { isLoading = false }
        
        do {
            var updateData: [String: Any] = [
                "title": title,
                "description": description,
                "priceTotal": price,
                "tags": selectedTags,
                "dietaryInfo": dietaryInfo.firestoreMap,
                "offerType": offerType as Any,
                "includesDrink": includesDrink,
                "includesDessert": includesDessert,
                "includesCoffee": includesCoffee,
                "serviceTime": serviceTime
            ]

            if isPermanentMenu {
                updateData["recurringDays"] = Array(recurringDays)
            }

            // If the user selected a new image, we have to upload it
            if let newImage = selectedImage, let imageData = newImage.jpegData(compressionQuality: 0.8) {
                let imagePath = "dailyOffers/\(UUID().uuidString).jpg"
                let newPhotoUrl = try await FirebaseService.shared.uploadImage(data: imageData, path: imagePath)
                updateData["photoUrls"] = [newPhotoUrl]
            }
            
            try await MenuService.shared.updateMenu(menuId: menuId, data: updateData)
            isSuccess = true
            
        } catch {
            errorMessage = error.localizedDescription
            showingError = true
        }
    }
    
    func deleteMenu(menuId: String) async {
        isLoading = true
        defer { isLoading = false }
        
        do {
            try await MenuService.shared.deleteMenu(menuId: menuId)
            isSuccess = true
        } catch {
            errorMessage = error.localizedDescription
            showingError = true
        }
    }
}
