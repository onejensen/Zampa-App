import Foundation

/// Modelo de suscripción de comercio (colección `subscriptions` en Firestore)
struct Subscription: Codable, Identifiable {
    let id: String
    let businessId: String
    let type: SubscriptionType
    let status: SubscriptionStatus
    let startDate: String
    let endDate: String
    let createdAt: String
    let updatedAt: String
    
    enum SubscriptionType: String, Codable {
        case monthly = "MONTHLY"
        case yearly = "YEARLY"
    }
    
    enum SubscriptionStatus: String, Codable {
        case active = "ACTIVE"
        case pending = "PENDING"
        case cancelled = "CANCELLED"
        case expired = "EXPIRED"
    }
    
    init(
        id: String = UUID().uuidString,
        businessId: String,
        type: SubscriptionType = .monthly,
        status: SubscriptionStatus = .active,
        startDate: String = ISO8601DateFormatter().string(from: Date()),
        endDate: String = "",
        createdAt: String = ISO8601DateFormatter().string(from: Date()),
        updatedAt: String = ISO8601DateFormatter().string(from: Date())
    ) {
        self.id = id
        self.businessId = businessId
        self.type = type
        self.status = status
        self.startDate = startDate
        self.endDate = endDate
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }
}
