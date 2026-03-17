import Foundation

/// Modelo de favorito (colección `favorites` en Firestore)
struct Favorite: Codable, Identifiable {
    let id: String
    let customerId: String
    let businessId: String
    let createdAt: String
    let notificationsEnabled: Bool
    
    init(
        id: String = UUID().uuidString,
        customerId: String,
        businessId: String,
        createdAt: String = ISO8601DateFormatter().string(from: Date()),
        notificationsEnabled: Bool = true
    ) {
        self.id = id
        self.customerId = customerId
        self.businessId = businessId
        self.createdAt = createdAt
        self.notificationsEnabled = notificationsEnabled
    }
}
