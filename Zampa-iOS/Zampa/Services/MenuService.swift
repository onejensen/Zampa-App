import FirebaseFirestore

/// Servicio de menús — wrapper sobre FirebaseService para operaciones de menú
class MenuService {
    static let shared = MenuService()
    
    private let firebase = FirebaseService.shared
    
    private init() {}
    
    // MARK: - Public Methods
    
    /// Obtiene menús activos para el feed
    func getMenus(
        limit: Int = 20,
        lastDocument: DocumentSnapshot? = nil,
        cuisineFilters: [String]? = nil,
        maxPrice: Double? = nil
    ) async throws -> (menus: [Menu], lastDoc: DocumentSnapshot?) {
        return try await firebase.getActiveMenus(
            limit: limit,
            lastDocument: lastDocument,
            cuisineFilters: cuisineFilters,
            maxPrice: maxPrice
        )
    }
    
    /// Obtiene menús de un merchant específico
    func getMenusByMerchant(merchantId: String) async throws -> [Menu] {
        return try await firebase.getMenusByMerchant(merchantId: merchantId)
    }
    
    /// Crea un nuevo menú con imagen.
    /// `isMerchantPro` se determina server-side leyendo `businesses/{uid}.planTier`.
    func createMenu(
        title: String,
        description: String,
        price: Double,
        currency: String = "EUR",
        photoData: Data,
        tags: [String]? = nil,
        dietaryInfo: DietaryInfo = DietaryInfo(),
        offerType: String? = nil,
        includesDrink: Bool = false,
        includesDessert: Bool = false,
        includesCoffee: Bool = false,
        serviceTime: String = "both",
        isPermanent: Bool = false,
        recurringDays: [Int]? = nil
    ) async throws -> Menu {
        return try await firebase.createMenu(
            title: title,
            description: description,
            price: price,
            currency: currency,
            photoData: photoData,
            tags: tags,
            dietaryInfo: dietaryInfo,
            offerType: offerType,
            includesDrink: includesDrink,
            includesDessert: includesDessert,
            includesCoffee: includesCoffee,
            serviceTime: serviceTime,
            isPermanent: isPermanent,
            recurringDays: recurringDays
        )
    }
    
    /// Elimina un menú
    func deleteMenu(menuId: String) async throws {
        try await firebase.deleteMenu(menuId: menuId)
    }
    
    /// Actualiza campos de un menú
    func updateMenu(menuId: String, data: [String: Any]) async throws {
        try await firebase.updateMenu(menuId: menuId, data: data)
    }
}
