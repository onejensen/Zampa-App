import Foundation

/// Dirección del comercio con coordenadas
struct MerchantAddress: Codable {
    let formatted: String
    let lat: Double
    let lng: Double
    let placeId: String?
}

/// Horario de un día de la semana
struct ScheduleEntry: Codable, Identifiable {
    var id: String { day }
    let day: String           // "monday", "tuesday", etc.
    let open: String          // "10:00"
    let close: String         // "23:00"
}

/// Perfil público de un comercio (colección `businesses` en Firestore)
struct Merchant: Codable, Identifiable {
    let id: String
    let userId: String?
    let name: String
    let phone: String?
    let address: MerchantAddress?
    let addressText: String?
    let schedule: [ScheduleEntry]?
    let cuisineTypes: [String]?
    let acceptsReservations: Bool
    let shortDescription: String?
    let coverPhotoUrl: String?
    let profilePhotoUrl: String?
    let planTier: String?     // "free", "pro"
    let isHighlighted: Bool?
    
    /// Initializer con valores por defecto para creación
    init(
        id: String,
        userId: String? = nil,
        name: String,
        phone: String? = nil,
        address: MerchantAddress? = nil,
        addressText: String? = nil,
        schedule: [ScheduleEntry]? = nil,
        cuisineTypes: [String]? = nil,
        acceptsReservations: Bool = false,
        shortDescription: String? = nil,
        coverPhotoUrl: String? = nil,
        profilePhotoUrl: String? = nil,
        planTier: String? = "free",
        isHighlighted: Bool? = false
    ) {
        self.id = id
        self.userId = userId
        self.name = name
        self.phone = phone
        self.address = address
        self.addressText = addressText
        self.schedule = schedule
        self.cuisineTypes = cuisineTypes
        self.acceptsReservations = acceptsReservations
        self.shortDescription = shortDescription
        self.coverPhotoUrl = coverPhotoUrl
        self.profilePhotoUrl = profilePhotoUrl
        self.planTier = planTier
        self.isHighlighted = isHighlighted
    }
}
