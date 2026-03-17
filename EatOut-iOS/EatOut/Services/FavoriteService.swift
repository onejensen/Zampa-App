import Foundation

/// Servicio para gestionar favoritos via REST API
class FavoriteService {
    static let shared = FavoriteService()
    
    private let apiClient = APIClient.shared
    
    private init() {}
    
    // MARK: - Models
    
    struct FavoriteItem: Codable {
        let merchantId: String
        let notify: Bool
        let merchant: FavoriteMerchant
        let lastMenu: LastMenu?
    }
    
    struct FavoriteMerchant: Codable {
        let id: String
        let name: String
        let phone: String?
        let cuisineTypes: [String]?
        let profilePhotoUrl: String?
    }
    
    struct LastMenu: Codable {
        let id: String
        let title: String
        let price: Double
        let currency: String
        let photoUrl: String?
        let publishedAt: String
    }
    
    // MARK: - Public Methods
    
    /// Obtiene la lista de favoritos
    func getFavorites() async throws -> [FavoriteItem] {
        return try await apiClient.get("/favorites", responseType: [FavoriteItem].self)
    }
    
    /// Agrega un merchant a favoritos
    func addFavorite(merchantId: String) async throws {
        struct AddRequest: Codable { let merchantId: String }
        struct AddResponse: Codable { let message: String }
        _ = try await apiClient.post(
            "/favorites",
            body: AddRequest(merchantId: merchantId),
            responseType: AddResponse.self
        )
    }
    
    /// Elimina un merchant de favoritos
    func removeFavorite(merchantId: String) async throws {
        struct DeleteResponse: Codable { let message: String }
        _ = try await apiClient.delete("/favorites/\(merchantId)", responseType: DeleteResponse.self)
    }
}
