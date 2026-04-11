import Foundation
import SwiftUI
import Combine

enum ColorSchemePreference: String, CaseIterable {
    case system, light, dark

    var label: String {
        switch self {
        case .system: return "Sistema"
        case .light:  return "Claro"
        case .dark:   return "Oscuro"
        }
    }

    var colorScheme: ColorScheme? {
        switch self {
        case .system: return nil
        case .light:  return .light
        case .dark:   return .dark
        }
    }
}

// MARK: - DietaryPreferences

struct DietaryPreferences: Codable, Equatable {
    var isVegetarian: Bool = false
    var isVegan: Bool = false
    var isGlutenFree: Bool = false
    var isLactoseFree: Bool = false
    var isNutFree: Bool = false
    var isMeatFree: Bool = false
    var isFishFree: Bool = false

    var isEmpty: Bool {
        !isVegetarian && !isVegan && !isGlutenFree && !isLactoseFree && !isNutFree
        && !isMeatFree && !isFishFree
    }

    /// Returns true if the given menu is compatible with these preferences.
    /// Menus without any dietary info filled in are always shown (can't know).
    func allows(_ menu: Menu) -> Bool {
        guard !isEmpty else { return true }
        let info = menu.dietaryInfo
        guard info.hasAnyInfo else { return true }

        if isVegan && !info.isVegan {
            if info.hasMeat || info.hasFish || info.hasLactose || info.hasEgg { return false }
        } else if isVegetarian && !info.isVegetarian {
            if info.hasMeat || info.hasFish { return false }
        }
        if isGlutenFree && info.hasGluten { return false }
        if isLactoseFree && info.hasLactose { return false }
        if isNutFree && info.hasNuts { return false }

        if isMeatFree && info.hasMeat { return false }
        if isFishFree && info.hasFish { return false }

        return true
    }
}

// MARK: - AppState

/// Estado global de la aplicación
class AppState: ObservableObject {
    @Published var isAuthenticated: Bool = false
    @Published var currentUser: User?
    @Published var merchantProfile: Merchant?
    @Published var isLoading: Bool = true
    @Published var needsMerchantSetup: Bool = false
    @Published var isPremium: Bool = false

    @Published var dietaryPreferences: DietaryPreferences = DietaryPreferences() {
        didSet { saveDietaryPreferences() }
    }

    @Published var appColorScheme: ColorSchemePreference = {
        let raw = UserDefaults.standard.string(forKey: "appColorScheme") ?? "system"
        return ColorSchemePreference(rawValue: raw) ?? .system
    }() {
        didSet { UserDefaults.standard.set(appColorScheme.rawValue, forKey: "appColorScheme") }
    }

    @Published var locationManager = LocationManager()

    /// Offer ID pendiente de abrir tras un deep link (Universal Link o `eatout://offer/{id}`).
    /// `ContentView` lo observa y presenta `MenuDetailView(menuId:)` cuando se establece.
    @Published var pendingDeepLinkOfferId: String?

    private let keychainManager = KeychainManager.shared
    
    init() {
        checkAuthenticationStatus()
    }
    
    /// Verifica el estado de autenticación al iniciar la app
    private func checkAuthenticationStatus() {
        isLoading = true
        
        // Check if there's a Firebase user session
        if FirebaseService.shared.isAuthenticated {
            Task {
                do {
                    if let user = try await FirebaseService.shared.getCurrentUser() {
                        await MainActor.run {
                            self.currentUser = user
                            self.isAuthenticated = true
                            self.isLoading = false
                            self.loadDietaryPreferences(for: user.id)
                            PushManager.shared.refreshTokenIfNeeded()
                        }
                        // Carga tasas de cambio en background (fallback embebido
                        // cubre hasta que esta llamada termine).
                        Task { await CurrencyService.shared.loadIfNeeded() }

                        // Si es merchant, cargar perfil
                        if user.role == .comercio {
                            await loadMerchantProfile(merchantId: user.id)
                        }
                    } else {
                        await MainActor.run {
                            self.isAuthenticated = false
                            self.isLoading = false
                        }
                    }
                } catch {
                    await MainActor.run {
                        self.isAuthenticated = false
                        self.isLoading = false
                    }
                }
            }
        } else {
            self.isAuthenticated = false
            self.isLoading = false
        }
    }
    
    /// Carga el perfil de merchant desde Firestore
    func loadMerchantProfile(merchantId: String) async {
        do {
            let profile = try await FirebaseService.shared.getMerchantProfile(merchantId: merchantId)
            let isComplete = try await FirebaseService.shared.isMerchantProfileComplete(merchantId: merchantId)
            
            await MainActor.run {
                self.merchantProfile = profile
                self.isPremium = profile?.planTier == "pro"
                self.needsMerchantSetup = !isComplete
            }
        } catch {
            await MainActor.run {
                self.needsMerchantSetup = true
            }
        }
    }
    
    /// Establece el usuario como autenticado
    func setAuthenticated(user: User) {
        self.currentUser = user
        self.isAuthenticated = true
        self.isLoading = false
        loadDietaryPreferences(for: user.id)
        PushManager.shared.refreshTokenIfNeeded()
        Task { await CurrencyService.shared.loadIfNeeded() }

        if user.role == .comercio {
            Task {
                await loadMerchantProfile(merchantId: user.id)
            }
        }
    }

    // MARK: - Dietary Preferences persistence

    private func dietaryKey(for uid: String) -> String { "dietaryPreferences_\(uid)" }

    func loadDietaryPreferences(for uid: String) {
        guard let data = UserDefaults.standard.data(forKey: dietaryKey(for: uid)),
              let prefs = try? JSONDecoder().decode(DietaryPreferences.self, from: data) else { return }
        self.dietaryPreferences = prefs
    }

    private func saveDietaryPreferences() {
        guard let uid = currentUser?.id,
              let data = try? JSONEncoder().encode(dietaryPreferences) else { return }
        UserDefaults.standard.set(data, forKey: dietaryKey(for: uid))
    }
    
    /// Cierra la sesión del usuario
    func logout() {
        try? FirebaseService.shared.logout()
        self.currentUser = nil
        self.merchantProfile = nil
        self.isAuthenticated = false
        self.needsMerchantSetup = false
        self.dietaryPreferences = DietaryPreferences()
        
        // Limpiar Keychain tokens legacy
        keychainManager.deleteAccessToken()
        keychainManager.deleteRefreshToken()
    }
}
