import Foundation

/// Catálogo de tipos de cocina (colección `cuisineTypes` en Firestore)
struct CuisineType: Codable, Identifiable {
    let id: String
    let name: String
}
