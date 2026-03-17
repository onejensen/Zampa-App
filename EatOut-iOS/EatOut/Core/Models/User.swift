import Foundation

/// Modelo de usuario de la aplicación
struct User: Codable, Identifiable {
    let id: String
    let email: String
    let name: String
    let role: UserRole
    let phone: String?
    let photoUrl: String?

    init(id: String, email: String, name: String, role: UserRole, phone: String? = nil, photoUrl: String? = nil) {
        self.id = id
        self.email = email
        self.name = name
        self.role = role
        self.phone = phone
        self.photoUrl = photoUrl
    }

    enum UserRole: String, Codable {
        case cliente = "CLIENTE"
        case comercio = "COMERCIO"
    }
}
