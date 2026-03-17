import Foundation

/// Notificación del sistema (colección `notifications` en Firestore)
struct AppNotification: Codable, Identifiable {
    let id: String
    let userId: String
    let businessId: String?
    let offerId: String?
    let type: String
    let title: String
    let body: String
    let read: Bool
    let createdAt: String
    
    init(
        id: String = UUID().uuidString,
        userId: String,
        businessId: String? = nil,
        offerId: String? = nil,
        type: String = "NEW_OFFER_FAVORITE",
        title: String,
        body: String,
        read: Bool = false,
        createdAt: String = ISO8601DateFormatter().string(from: Date())
    ) {
        self.id = id
        self.userId = userId
        self.businessId = businessId
        self.offerId = offerId
        self.type = type
        self.title = title
        self.body = body
        self.read = read
        self.createdAt = createdAt
    }
}
