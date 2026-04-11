import Foundation

/// Configuración de la aplicación.
/// La app usa Firebase como única fuente de datos — no hay backend REST.
struct Config {
    static let shared = Config()

    // MARK: - App Configuration

    var appName: String {
        return "Zampa"
    }

    var bundleIdentifier: String {
        return "com.Sozolab.zampa"
    }

    private init() {}
}





