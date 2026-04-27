import Foundation

/// Alias para evitar colisión con FirebaseAuth.User sin depender del module name
typealias AppUser = User

/// Modelo de usuario de la aplicación
struct User: Codable, Identifiable {
    let id: String
    let email: String
    let name: String
    let role: UserRole
    let phone: String?
    let photoUrl: String?
    /// Fecha en la que el usuario solicitó la eliminación de su cuenta.
    /// Si es no-nil, la cuenta está en periodo de gracia hasta `scheduledPurgeAt`.
    let deletedAt: Date?
    /// Fecha programada para el purgado definitivo (deletedAt + 30 días).
    let scheduledPurgeAt: Date?
    /// Código ISO 4217 de la moneda preferida para mostrar precios.
    /// Default `"EUR"` cuando el campo no está presente en Firestore.
    let currencyPreference: String
    let languagePreference: String

    init(
        id: String,
        email: String,
        name: String,
        role: UserRole,
        phone: String? = nil,
        photoUrl: String? = nil,
        deletedAt: Date? = nil,
        scheduledPurgeAt: Date? = nil,
        currencyPreference: String = "EUR",
        languagePreference: String = "auto"
    ) {
        self.id = id
        self.email = email
        self.name = name
        self.role = role
        self.phone = phone
        self.photoUrl = photoUrl
        self.deletedAt = deletedAt
        self.scheduledPurgeAt = scheduledPurgeAt
        self.currencyPreference = currencyPreference
        self.languagePreference = languagePreference
    }

    enum UserRole: String, Codable {
        case cliente = "CLIENTE"
        case comercio = "COMERCIO"
    }
}
