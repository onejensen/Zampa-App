import SwiftUI
import PhotosUI
import UIKit

struct CreateMenuView: View {
    @Environment(\.dismiss) var dismiss
    @EnvironmentObject var appState: AppState
    @StateObject private var viewModel = CreateMenuViewModel()
    @State private var showingSubscription = false
    @State private var mealType: MealType = .lunch
    let activeMenusCount: Int

    enum MealType: String, CaseIterable {
        case lunch = "Comida"
        case dinner = "Cena"
    }

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 20) {

                    // ── PHOTO ───────────────────────────────────────────
                    PhotosPicker(selection: $viewModel.selectedPhotoItem, matching: .images) {
                        ZStack {
                            if let image = viewModel.selectedImage {
                                Image(uiImage: image)
                                    .resizable()
                                    .aspectRatio(contentMode: .fill)
                            } else {
                                Rectangle()
                                    .fill(Color.appInputBackground)
                                    .overlay(
                                        VStack(spacing: 12) {
                                            Image(systemName: "camera.fill")
                                                .font(.system(size: 36))
                                                .foregroundColor(.appTextSecondary)
                                            Text("Añadir foto del menú")
                                                .font(.appBody)
                                                .foregroundColor(.appTextSecondary)
                                        }
                                    )
                            }
                        }
                        .frame(height: 220)
                        .cornerRadius(16)
                        .clipped()
                    }

                    // ── MEAL TYPE PICKER ────────────────────────────────
                    Picker("Tipo", selection: $mealType) {
                        ForEach(MealType.allCases, id: \.self) { type in
                            Text(type.rawValue).tag(type)
                        }
                    }
                    .pickerStyle(.segmented)

                    // ── OFFER TYPE + INCLUDES ─────────────────────────────
                    OfferDetailsSection(
                        offerType: $viewModel.offerType,
                        includesDrink: $viewModel.includesDrink,
                        includesDessert: $viewModel.includesDessert,
                        includesCoffee: $viewModel.includesCoffee
                    )

                    // ── TITLE FIELD ─────────────────────────────────────
                    CustomTextField(title: "Título del menú *", text: $viewModel.title, icon: "text.alignleft")

                    // ── DESCRIPTION ─────────────────────────────────────
                    ZStack(alignment: .topLeading) {
                        if viewModel.description.isEmpty {
                            Text("Descripción del menú (opcional)")
                                .foregroundColor(.appTextSecondary)
                                .font(.appBody)
                                .padding(EdgeInsets(top: 12, leading: 12, bottom: 0, trailing: 0))
                        }
                        TextEditor(text: $viewModel.description)
                            .font(.appBody)
                            .foregroundColor(.appTextPrimary)
                            .frame(minHeight: 80)
                            .padding(8)
                            .scrollContentBackground(.hidden)
                    }
                    .background(RoundedRectangle(cornerRadius: 12).fill(Color.appInputBackground))

                    // ── PRICE FIELD ─────────────────────────────────────
                    HStack {
                        Image(systemName: "eurosign.circle")
                            .foregroundColor(.appTextSecondary)
                            .frame(width: 20)
                        TextField("Precio", value: $viewModel.price, format: .number)
                            .keyboardType(.decimalPad)
                            .foregroundColor(.appTextPrimary)
                            .font(.appBody)
                        Text("€")
                            .foregroundColor(.appTextSecondary)
                    }
                    .padding()
                    .background(RoundedRectangle(cornerRadius: 12).fill(Color.appInputBackground))

                    // ── DIETARY INFO ─────────────────────────────────────
                    DietaryInfoEditor(dietaryInfo: $viewModel.dietaryInfo)

                    // ── PRO LIMIT BANNER OR PUBLISH BUTTON ──────────────
                    if !appState.isPremium && activeMenusCount >= 1 {
                        VStack(spacing: 12) {
                            HStack {
                                Image(systemName: "star.fill")
                                    .foregroundColor(.yellow)
                                Text("Límite alcanzado — Plan Free")
                                    .font(.system(size: 15, weight: .bold))
                                    .foregroundColor(.appTextPrimary)
                            }
                            Text("Actualiza a Pro para publicar menús ilimitados.")
                                .font(.caption)
                                .foregroundColor(.appTextSecondary)
                                .multilineTextAlignment(.center)
                            Button("Ver planes Pro") { showingSubscription = true }
                                .buttonStyle(AppDesign.ButtonStyle(isPrimary: true))
                        }
                        .padding(16)
                        .background(RoundedRectangle(cornerRadius: 14).fill(Color.appPrimarySurface))
                    } else {
                        Button(action: {
                            Task {
                                viewModel.selectedTags = [mealType.rawValue]
                                await viewModel.createMenu(isPro: appState.isPremium)
                                if viewModel.isSuccess { dismiss() }
                            }
                        }) {
                            HStack(spacing: 10) {
                                if viewModel.isLoading {
                                    ProgressView()
                                        .progressViewStyle(.circular)
                                        .tint(.white)
                                }
                                Text(viewModel.isLoading ? "Publicando..." : "PUBLICAR OFERTA")
                                    .font(.system(size: 17, weight: .bold))
                            }
                        }
                        .buttonStyle(AppDesign.ButtonStyle(isPrimary: true, isDisabled: !viewModel.isValid || viewModel.isLoading))
                        .disabled(!viewModel.isValid || viewModel.isLoading)
                    }
                }
                .padding(16)
            }
            .navigationTitle("Nueva oferta")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancelar") { dismiss() }
                }
            }
            .sheet(isPresented: $showingSubscription) {
                SubscriptionView()
            }
            .alert("Error", isPresented: $viewModel.showingError) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(viewModel.errorMessage)
            }
            .onChange(of: viewModel.selectedPhotoItem) { _, newItem in
                if let newItem {
                    Task { await viewModel.loadImage(from: newItem) }
                }
            }
        }
    }
}

class CreateMenuViewModel: ObservableObject {
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

    @Published var availableTags: [String] = []

    @Published var isLoading: Bool = false
    @Published var isSuccess: Bool = false
    @Published var showingError: Bool = false
    @Published var showingSuccess: Bool = false
    @Published var errorMessage: String = ""

    var isValid: Bool {
        !title.isEmpty && price > 0 && selectedImage != nil
    }

    init() {
        Task { @MainActor in
            if let types = try? await FirebaseService.shared.fetchCuisineTypes() {
                self.availableTags = types.map { $0.name }
            }
        }
    }

    @MainActor
    func loadImage(from item: PhotosPickerItem) async {
        if let data = try? await item.loadTransferable(type: Data.self),
           let image = UIImage(data: data) {
            self.selectedImage = image
        }
    }

    @MainActor
    func createMenu(isPro: Bool = false) async {
        guard isValid, let image = selectedImage else { return }

        isLoading = true
        defer { isLoading = false }

        do {
            guard let imageData = image.jpegData(compressionQuality: 0.8) else {
                errorMessage = "No se pudo procesar la imagen"
                showingError = true
                return
            }

            _ = try await MenuService.shared.createMenu(
                title: title,
                description: description,
                price: price,
                currency: "EUR",
                photoData: imageData,
                tags: selectedTags,
                isPro: isPro,
                dietaryInfo: dietaryInfo,
                offerType: offerType,
                includesDrink: includesDrink,
                includesDessert: includesDessert,
                includesCoffee: includesCoffee
            )

            isSuccess = true
            showingSuccess = true

            title = ""
            description = ""
            price = 0.0
            selectedImage = nil
            selectedPhotoItem = nil
        } catch {
            errorMessage = error.localizedDescription
            showingError = true
        }
    }
}

// MARK: - Offer Details Section (type + inclusions)

struct OfferDetailsSection: View {
    @Binding var offerType: String?
    @Binding var includesDrink: Bool
    @Binding var includesDessert: Bool
    @Binding var includesCoffee: Bool

    private let types = ["Menú", "Plato del día", "Oferta"]

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            // Offer type
            VStack(alignment: .leading, spacing: 8) {
                Label("Tipo de oferta", systemImage: "tag")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(.appTextPrimary)
                HStack(spacing: 8) {
                    ForEach(types, id: \.self) { type in
                        let selected = offerType == type
                        Button(action: { offerType = selected ? nil : type }) {
                            Text(type)
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(selected ? .white : .appTextPrimary)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 9)
                                .frame(maxWidth: .infinity)
                                .background(RoundedRectangle(cornerRadius: 10).fill(selected ? Color.appPrimary : Color.appSurface))
                        }
                        .buttonStyle(.borderless)
                    }
                }
            }

            // Includes
            VStack(alignment: .leading, spacing: 8) {
                Label("Incluye", systemImage: "plus.circle")
                    .font(.caption.weight(.semibold))
                    .foregroundColor(.appTextSecondary)
                HStack(spacing: 8) {
                    DietaryChip(label: "Bebida", icon: "wineglass.fill", isOn: $includesDrink, color: .blue)
                    DietaryChip(label: "Postre", icon: "birthday.cake.fill", isOn: $includesDessert, color: .pink)
                    DietaryChip(label: "Café", icon: "cup.and.saucer.fill", isOn: $includesCoffee, color: Color(red: 0.5, green: 0.3, blue: 0.1))
                }
            }
        }
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 12).fill(Color.appInputBackground))
    }
}

// MARK: - Dietary Info Editor (shared by Create & Edit)

struct DietaryInfoEditor: View {
    @Binding var dietaryInfo: DietaryInfo

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Información dietética")
                .font(.system(size: 15, weight: .semibold))
                .foregroundColor(.appTextPrimary)

            // Diet
            VStack(alignment: .leading, spacing: 8) {
                Label("Dieta", systemImage: "leaf")
                    .font(.caption.weight(.semibold))
                    .foregroundColor(.appTextSecondary)
                HStack(spacing: 8) {
                    DietaryChip(label: "Vegetariano", icon: "leaf", isOn: $dietaryInfo.isVegetarian, color: .green)
                    DietaryChip(label: "Vegano", icon: "leaf.fill", isOn: $dietaryInfo.isVegan, color: .green)
                }
            }

            // Protein
            VStack(alignment: .leading, spacing: 8) {
                Label("Proteína principal", systemImage: "fork.knife")
                    .font(.caption.weight(.semibold))
                    .foregroundColor(.appTextSecondary)
                HStack(spacing: 8) {
                    DietaryChip(label: "Carne", icon: "fork.knife", isOn: $dietaryInfo.hasMeat)
                    DietaryChip(label: "Pescado/Marisco", icon: "fish.fill", isOn: $dietaryInfo.hasFish, color: .blue)
                }
            }

            // Allergens
            VStack(alignment: .leading, spacing: 8) {
                Label("Alérgenos presentes", systemImage: "exclamationmark.triangle")
                    .font(.caption.weight(.semibold))
                    .foregroundColor(.appTextSecondary)
                HStack(spacing: 8) {
                    DietaryChip(label: "Gluten", icon: "exclamationmark.triangle.fill", isOn: $dietaryInfo.hasGluten, color: .orange)
                    DietaryChip(label: "Lácteos", icon: "exclamationmark.triangle.fill", isOn: $dietaryInfo.hasLactose, color: .orange)
                }
                HStack(spacing: 8) {
                    DietaryChip(label: "Frutos secos", icon: "exclamationmark.triangle.fill", isOn: $dietaryInfo.hasNuts, color: .orange)
                    DietaryChip(label: "Huevo", icon: "exclamationmark.triangle.fill", isOn: $dietaryInfo.hasEgg, color: .orange)
                }
            }
        }
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 12).fill(Color.appInputBackground))
    }
}

struct DietaryChip: View {
    let label: String
    let icon: String
    @Binding var isOn: Bool
    var color: Color = .appPrimary

    var body: some View {
        Button(action: { isOn.toggle() }) {
            HStack(spacing: 5) {
                Image(systemName: isOn ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 12))
                Text(label)
                    .font(.system(size: 13, weight: .medium))
                    .lineLimit(1)
            }
            .foregroundColor(isOn ? .white : .appTextPrimary)
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .frame(maxWidth: .infinity)
            .background(RoundedRectangle(cornerRadius: 10).fill(isOn ? color : Color.appSurface))
        }
        .buttonStyle(.borderless) // prevents Form rows from swallowing sibling button taps
    }
}

// MARK: - Dietary Info Display (for MenuDetailView)

struct DietaryInfoSection: View {
    let dietaryInfo: DietaryInfo

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 6) {
                Image(systemName: "info.circle.fill")
                    .foregroundColor(.appPrimary)
                    .font(.system(size: 14))
                Text("Información dietética")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(.appTextPrimary)
            }

            // Diet + protein badges
            let dietBadges: [(String, String, Color)] = [
                dietaryInfo.isVegan       ? ("Vegano",          "leaf.fill",  Color.green) : nil,
                dietaryInfo.isVegetarian && !dietaryInfo.isVegan
                                          ? ("Vegetariano",     "leaf",       Color.green) : nil,
                dietaryInfo.hasMeat       ? ("Carne",           "fork.knife", Color.appTextSecondary) : nil,
                dietaryInfo.hasFish       ? ("Pescado/Marisco", "fish.fill",  Color.blue) : nil,
            ].compactMap { $0 }

            if !dietBadges.isEmpty {
                HStack(spacing: 8) {
                    ForEach(dietBadges, id: \.0) { label, icon, color in
                        DietaryBadge(label: label, icon: icon, color: color)
                    }
                }
            }

            // Allergens
            let allergens = [
                dietaryInfo.hasGluten  ? "Gluten"       : nil,
                dietaryInfo.hasLactose ? "Lácteos"      : nil,
                dietaryInfo.hasNuts    ? "Frutos secos" : nil,
                dietaryInfo.hasEgg     ? "Huevo"        : nil,
            ].compactMap { $0 }

            if !allergens.isEmpty {
                HStack(spacing: 6) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.system(size: 11))
                        .foregroundColor(.orange)
                    Text("Alérgenos: \(allergens.joined(separator: ", "))")
                        .font(.system(size: 13))
                        .foregroundColor(.orange)
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.appSurface)
    }
}

struct DietaryBadge: View {
    let label: String
    let icon: String
    let color: Color

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: icon)
                .font(.system(size: 11))
            Text(label)
                .font(.system(size: 12, weight: .medium))
        }
        .foregroundColor(color)
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(color.opacity(0.12))
        .cornerRadius(8)
    }
}

#Preview {
    CreateMenuView(activeMenusCount: 0)
        .environmentObject(AppState())
}
