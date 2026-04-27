import SwiftUI
import PhotosUI
import UIKit

struct CreateMenuView: View {
    @ObservedObject var localization = LocalizationManager.shared
    @Environment(\.dismiss) var dismiss
    @EnvironmentObject var appState: AppState
    @StateObject private var viewModel = CreateMenuViewModel()
    @State private var showingSubscription = false
    @State private var mealType: MealType = .lunch
    @State private var showingPhotoSourceDialog = false
    @State private var activePhotoSheet: PhotoSheet?
    let activeMenusCount: Int

    enum MealType: String, CaseIterable {
        case lunch = "Comida"
        case dinner = "Cena"
    }

    private enum PhotoSheet: Identifiable {
        case camera, gallery
        var id: Int { hashValue }
    }

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 20) {

                    // ── PHOTO ───────────────────────────────────────────
                    Button {
                        showingPhotoSourceDialog = true
                    } label: {
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
                                                .font(.custom("Sora-Regular", size: 36))
                                                .foregroundColor(.appTextSecondary)
                                            Text(localization.t("create_menu_add_photo"))
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
                    .buttonStyle(.plain)

                    // ── MEAL TYPE PICKER ────────────────────────────────
                    Picker("Tipo", selection: $mealType) {
                        Text(localization.t("create_menu_lunch")).tag(MealType.lunch)
                        Text(localization.t("create_menu_dinner")).tag(MealType.dinner)
                    }
                    .pickerStyle(.segmented)

                    // ── OFFER TYPE + INCLUDES ─────────────────────────────
                    OfferDetailsSection(
                        offerType: $viewModel.offerType,
                        includesDrink: $viewModel.includesDrink,
                        includesDessert: $viewModel.includesDessert,
                        includesCoffee: $viewModel.includesCoffee,
                        serviceTime: $viewModel.serviceTime
                    )

                    // ── TITLE FIELD ─────────────────────────────────────
                    CustomTextField(title: localization.t("create_menu_menu_title"), text: $viewModel.title, icon: "text.alignleft")

                    // ── DESCRIPTION ─────────────────────────────────────
                    ZStack(alignment: .topLeading) {
                        if viewModel.description.isEmpty {
                            Text(localization.t("create_menu_description"))
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
                        TextField(localization.t("create_menu_price"), text: $viewModel.priceText)
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
                                Text(localization.t("create_menu_limit_title"))
                                    .font(.custom("Sora-Bold", size: 15))
                                    .foregroundColor(.appTextPrimary)
                            }
                            Text(localization.t("create_menu_limit_body"))
                                .font(.appCaption)
                                .foregroundColor(.appTextSecondary)
                                .multilineTextAlignment(.center)
                            Button(localization.t("create_menu_see_pro")) { showingSubscription = true }
                                .buttonStyle(AppDesign.ButtonStyle(isPrimary: true))
                        }
                        .padding(16)
                        .background(RoundedRectangle(cornerRadius: 14).fill(Color.appPrimarySurface))
                    } else {
                        Button(action: {
                            Task {
                                viewModel.selectedTags = [mealType.rawValue]
                                await viewModel.createMenu()
                                if viewModel.isSuccess { dismiss() }
                            }
                        }) {
                            HStack(spacing: 10) {
                                if viewModel.isLoading {
                                    ProgressView()
                                        .progressViewStyle(.circular)
                                        .tint(.white)
                                }
                                Text(viewModel.isLoading ? localization.t("create_menu_publishing") : localization.t("create_menu_publish"))
                                    .font(.custom("Sora-Bold", size: 17))
                            }
                        }
                        .buttonStyle(AppDesign.ButtonStyle(isPrimary: true, isDisabled: !viewModel.isValid || viewModel.isLoading))
                        .disabled(!viewModel.isValid || viewModel.isLoading)
                    }
                }
                .padding(16)
            }
            .navigationTitle(localization.t("create_menu_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(localization.t("common_cancel")) { dismiss() }
                }
            }
            .sheet(isPresented: $showingSubscription) {
                SubscriptionView()
            }
            .alert(localization.t("common_error"), isPresented: $viewModel.showingError) {
                Button(localization.t("common_ok"), role: .cancel) {}
            } message: {
                Text(viewModel.errorMessage)
            }
            .confirmationDialog(localization.t("create_menu_photo"), isPresented: $showingPhotoSourceDialog, titleVisibility: .visible) {
                Button(localization.t("profile_camera")) { activePhotoSheet = .camera }
                Button(localization.t("profile_gallery")) { activePhotoSheet = .gallery }
                Button(localization.t("common_cancel"), role: .cancel) {}
            }
            .sheet(item: $activePhotoSheet) { sheet in
                switch sheet {
                case .camera:
                    CameraImagePicker(image: $viewModel.selectedImage)
                case .gallery:
                    GalleryImagePicker(image: $viewModel.selectedImage)
                }
            }
        }
    }
}

class CreateMenuViewModel: ObservableObject {
    @Published var title: String = ""
    @Published var description: String = ""
    /// El precio se gestiona como String para que el campo aparezca vacío al
    /// abrir el formulario (en vez de un "0" pegado que obliga a borrarlo).
    /// Se parsea a Double al publicar. Acepta coma o punto como separador decimal.
    @Published var priceText: String = ""
    @Published var selectedImage: UIImage?
    @Published var selectedTags: [String] = []
    @Published var dietaryInfo: DietaryInfo = DietaryInfo()
    @Published var offerType: String? = nil
    @Published var includesDrink: Bool = false
    @Published var includesDessert: Bool = false
    @Published var includesCoffee: Bool = false
    @Published var serviceTime: String = "both"

    @Published var availableTags: [String] = []

    @Published var isLoading: Bool = false
    @Published var isSuccess: Bool = false
    @Published var showingError: Bool = false
    @Published var showingSuccess: Bool = false
    @Published var errorMessage: String = ""

    /// Precio parseado. Devuelve 0 si el campo está vacío o mal formado;
    /// `isValid` garantiza que no se publique con precio 0.
    var price: Double {
        Double(priceText.replacingOccurrences(of: ",", with: ".")) ?? 0
    }

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
    func createMenu() async {
        guard isValid, let image = selectedImage else { return }

        isLoading = true
        defer { isLoading = false }

        do {
            guard let imageData = image.jpegData(compressionQuality: 0.8) else {
                errorMessage = LocalizationManager.shared.t("profile_photo_error")
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
                dietaryInfo: dietaryInfo,
                offerType: offerType,
                includesDrink: includesDrink,
                includesDessert: includesDessert,
                includesCoffee: includesCoffee,
                serviceTime: serviceTime,
                isPermanent: offerType == OfferTypes.ofertaPermanente
            )

            isSuccess = true
            showingSuccess = true

            title = ""
            description = ""
            priceText = ""
            selectedImage = nil
        } catch {
            errorMessage = error.localizedDescription
            showingError = true
        }
    }
}

// MARK: - Offer Details Section (type + inclusions)

struct OfferDetailsSection: View {
    @ObservedObject var localization = LocalizationManager.shared
    @Binding var offerType: String?
    @Binding var includesDrink: Bool
    @Binding var includesDessert: Bool
    @Binding var includesCoffee: Bool
    @Binding var serviceTime: String

    /// (value, label): `value` es el valor canónico ES que se guarda en Firestore;
    /// `label` es la traducción mostrada al usuario. Nunca persistir `label`.
    private var typeOptions: [(value: String, label: String)] {[
        (OfferTypes.menuDelDia, localization.t("create_menu_menu_del_dia")),
        (OfferTypes.platoDelDia, localization.t("create_menu_plato_del_dia")),
        (OfferTypes.ofertaDelDia, localization.t("create_menu_oferta_del_dia")),
        (OfferTypes.ofertaPermanente, localization.t("create_menu_permanent")),
    ]}
    private var serviceTimes: [(String, String)] { [("lunch", localization.t("create_menu_midday")), ("dinner", localization.t("create_menu_night")), ("both", localization.t("create_menu_both"))] }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            // Offer type
            VStack(alignment: .leading, spacing: 8) {
                Label(localization.t("create_menu_offer_type"), systemImage: "tag")
                    .font(.custom("Sora-SemiBold", size: 15))
                    .foregroundColor(.appTextPrimary)
                ChipFlow(spacing: 8) {
                    ForEach(typeOptions, id: \.value) { option in
                        let selected = offerType == option.value
                        Button(action: { offerType = selected ? nil : option.value }) {
                            Text(option.label)
                                .font(.custom("Sora-SemiBold", size: 13))
                                .lineLimit(1)
                                .foregroundColor(selected ? .white : .appTextPrimary)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 9)
                                .background(RoundedRectangle(cornerRadius: 10).fill(
                                    selected ? Color.appPrimary : Color.appSurface
                                ))
                        }
                        .buttonStyle(.borderless)
                    }
                }
            }

            // Service time (horario)
            VStack(alignment: .leading, spacing: 8) {
                Label(localization.t("create_menu_schedule"), systemImage: "clock")
                    .font(.custom("Sora-SemiBold", size: 15))
                    .foregroundColor(.appTextPrimary)
                HStack(spacing: 8) {
                    ForEach(serviceTimes, id: \.0) { (value, label) in
                        let selected = serviceTime == value
                        Button(action: { serviceTime = value }) {
                            Text(label)
                                .font(.custom("Sora-SemiBold", size: 13))
                                .lineLimit(1)
                                .foregroundColor(selected ? .white : .appTextPrimary)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 9)
                                .frame(maxWidth: .infinity)
                                .background(RoundedRectangle(cornerRadius: 10).fill(
                                    selected ? Color.appPrimary : Color.appSurface
                                ))
                        }
                        .buttonStyle(.borderless)
                    }
                }
            }

            // Includes
            VStack(alignment: .leading, spacing: 8) {
                Label(localization.t("create_menu_includes"), systemImage: "plus.circle")
                    .font(.appLabel)
                    .foregroundColor(.appTextSecondary)
                HStack(spacing: 8) {
                    DietaryChip(label: localization.t("create_menu_drink"), icon: "wineglass.fill", isOn: $includesDrink, color: .blue)
                    DietaryChip(label: localization.t("create_menu_dessert"), icon: "birthday.cake.fill", isOn: $includesDessert, color: .pink)
                    DietaryChip(label: localization.t("create_menu_coffee"), icon: "cup.and.saucer.fill", isOn: $includesCoffee, color: Color(red: 0.5, green: 0.3, blue: 0.1))
                }
            }
        }
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 12).fill(Color.appInputBackground))
    }
}

// MARK: - Dietary Info Editor (shared by Create & Edit)

struct DietaryInfoEditor: View {
    @ObservedObject var localization = LocalizationManager.shared
    @Binding var dietaryInfo: DietaryInfo

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(localization.t("create_menu_dietary_info"))
                .font(.custom("Sora-SemiBold", size: 15))
                .foregroundColor(.appTextPrimary)

            // Diet
            VStack(alignment: .leading, spacing: 8) {
                Label(localization.t("create_menu_diet"), systemImage: "leaf")
                    .font(.appLabel)
                    .foregroundColor(.appTextSecondary)
                HStack(spacing: 8) {
                    DietaryChip(label: localization.t("dietary_vegetarian"), icon: "leaf", isOn: $dietaryInfo.isVegetarian, color: .green)
                    DietaryChip(label: localization.t("dietary_vegan"), icon: "leaf.fill", isOn: $dietaryInfo.isVegan, color: .green)
                }
            }

            // Protein
            VStack(alignment: .leading, spacing: 8) {
                Label(localization.t("create_menu_protein"), systemImage: "fork.knife")
                    .font(.appLabel)
                    .foregroundColor(.appTextSecondary)
                HStack(spacing: 8) {
                    DietaryChip(label: localization.t("create_menu_meat"), icon: "fork.knife", isOn: $dietaryInfo.hasMeat)
                    DietaryChip(label: localization.t("create_menu_fish"), icon: "fish.fill", isOn: $dietaryInfo.hasFish, color: .blue)
                }
            }

            // Allergens
            VStack(alignment: .leading, spacing: 8) {
                Label(localization.t("create_menu_allergens_present"), systemImage: "exclamationmark.triangle")
                    .font(.appLabel)
                    .foregroundColor(.appTextSecondary)
                HStack(spacing: 8) {
                    DietaryChip(label: localization.t("create_menu_gluten"), icon: "exclamationmark.triangle.fill", isOn: $dietaryInfo.hasGluten, color: .orange)
                    DietaryChip(label: localization.t("create_menu_dairy"), icon: "exclamationmark.triangle.fill", isOn: $dietaryInfo.hasLactose, color: .orange)
                }
                HStack(spacing: 8) {
                    DietaryChip(label: localization.t("create_menu_nuts"), icon: "exclamationmark.triangle.fill", isOn: $dietaryInfo.hasNuts, color: .orange)
                    DietaryChip(label: localization.t("create_menu_egg"), icon: "exclamationmark.triangle.fill", isOn: $dietaryInfo.hasEgg, color: .orange)
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
                    .font(.custom("Sora-Regular", size: 12))
                Text(label)
                    .font(.custom("Sora-Medium", size: 13))
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
    @ObservedObject var localization = LocalizationManager.shared
    let dietaryInfo: DietaryInfo

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 6) {
                Image(systemName: "info.circle.fill")
                    .foregroundColor(.appPrimary)
                    .font(.custom("Sora-Regular", size: 14))
                Text(localization.t("create_menu_dietary_info"))
                    .font(.custom("Sora-SemiBold", size: 15))
                    .foregroundColor(.appTextPrimary)
            }

            // Diet + protein badges
            let dietBadges: [(String, String, Color)] = [
                dietaryInfo.isVegan       ? (localization.t("dietary_vegan"),          "leaf.fill",  Color.green) : nil,
                dietaryInfo.isVegetarian && !dietaryInfo.isVegan
                                          ? (localization.t("dietary_vegetarian"),     "leaf",       Color.green) : nil,
                dietaryInfo.hasMeat       ? (localization.t("create_menu_meat"),           "fork.knife", Color.appTextSecondary) : nil,
                dietaryInfo.hasFish       ? (localization.t("create_menu_fish"), "fish.fill",  Color.blue) : nil,
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
                dietaryInfo.hasGluten  ? localization.t("create_menu_gluten")       : nil,
                dietaryInfo.hasLactose ? localization.t("create_menu_dairy")      : nil,
                dietaryInfo.hasNuts    ? localization.t("create_menu_nuts") : nil,
                dietaryInfo.hasEgg     ? localization.t("create_menu_egg")        : nil,
            ].compactMap { $0 }

            if !allergens.isEmpty {
                HStack(spacing: 6) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.custom("Sora-Regular", size: 11))
                        .foregroundColor(.orange)
                    Text("\(localization.t("detail_allergens")) \(allergens.joined(separator: ", "))")
                        .font(.custom("Sora-Regular", size: 13))
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
                .font(.custom("Sora-Regular", size: 11))
            Text(label)
                .font(.custom("Sora-Medium", size: 12))
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
