import Foundation

/// Parsea URLs entrantes (Universal Links + custom scheme `zampa://`) y devuelve
/// el offer ID a abrir en `MenuDetailView`. Devuelve `nil` si el URL no corresponde
/// a una ruta soportada.
///
/// Formatos soportados:
/// - `https://eatout-70b8b.web.app/o/{offerId}`
/// - `https://eatout-70b8b.firebaseapp.com/o/{offerId}`
/// - `zampa://offer/{offerId}`
enum DeepLinkRouter {

    static let supportedHosts: Set<String> = [
        "eatout-70b8b.web.app",
        "eatout-70b8b.firebaseapp.com",
    ]

    static func offerId(from url: URL) -> String? {
        // Custom scheme: zampa://offer/{id}
        if url.scheme == "zampa" {
            // Host puede ser "offer" o el primer path component según iOS lo parsee.
            if url.host == "offer" {
                let id = url.pathComponents.first(where: { $0 != "/" })
                return id?.isEmpty == false ? id : nil
            }
            // Fallback: zampa:///offer/{id}
            let parts = url.pathComponents.filter { $0 != "/" }
            if parts.first == "offer", parts.count >= 2 {
                return parts[1]
            }
            return nil
        }

        // Universal Link: https://{host}/o/{id}
        guard let host = url.host, supportedHosts.contains(host) else { return nil }
        let parts = url.pathComponents.filter { $0 != "/" }
        guard parts.count >= 2, parts[0] == "o" else { return nil }
        let id = parts[1]
        return id.isEmpty ? nil : id
    }
}
