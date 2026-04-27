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

/// Estado de la suscripción de un merchant.
/// - `trial`: dentro del periodo gratuito de 90 días tras registro
/// - `active`: suscripción de pago vigente (RevenueCat webhook)
/// - `expired`: trial o suscripción caducada — no puede publicar nuevas ofertas
enum SubscriptionStatus: String, Codable {
    case trial
    case active
    case expired
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
    let planTier: String?     // Legacy: "free", "pro" — queda por compat con Cloud Functions
    let isHighlighted: Bool?
    /// NIF/CIF (España) del negocio. Obligatorio para verificar que es un restaurante
    /// real. Se guarda normalizado: sin espacios y en mayúsculas.
    let taxId: String?
    /// Estado efectivo. Si falta (legacy) se asume `trial`.
    let subscriptionStatus: SubscriptionStatus?
    /// Fin del periodo de prueba (createdAt + 90 días). ISO8601 en Firestore.
    let trialEndsAt: Date?
    /// Fin de la suscripción de pago vigente. Se actualiza desde el webhook de RevenueCat.
    let subscriptionActiveUntil: Date?

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
        isHighlighted: Bool? = false,
        taxId: String? = nil,
        subscriptionStatus: SubscriptionStatus? = nil,
        trialEndsAt: Date? = nil,
        subscriptionActiveUntil: Date? = nil
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
        self.taxId = taxId
        self.subscriptionStatus = subscriptionStatus
        self.trialEndsAt = trialEndsAt
        self.subscriptionActiveUntil = subscriptionActiveUntil
    }

    /// ¿El merchant puede publicar nuevas ofertas ahora?
    /// Trial no expirado o suscripción de pago vigente.
    func canPublish(now: Date = Date()) -> Bool {
        switch subscriptionStatus ?? .trial {
        case .trial:
            return (trialEndsAt ?? .distantPast) > now
        case .active:
            return (subscriptionActiveUntil ?? .distantPast) > now
        case .expired:
            return false
        }
    }

    /// Días restantes de trial (solo si `subscriptionStatus == .trial`). 0 si ya expiró.
    func trialDaysRemaining(now: Date = Date()) -> Int? {
        guard subscriptionStatus == nil || subscriptionStatus == .trial,
              let end = trialEndsAt else { return nil }
        let days = Calendar.current.dateComponents([.day], from: now, to: end).day ?? 0
        return max(0, days)
    }
}
