import Foundation

/// Configuración de la aplicación
struct Config {
    static let shared = Config()
    
    // MARK: - API Configuration
    
    /// URL base de la API
    var apiBaseURL: String {
        #if DEBUG
        return "http://localhost:3000/api/v1"
        #else
        return "https://api.eatout.com/api/v1"
        #endif
    }
    
    // MARK: - App Configuration
    
    var appName: String {
        return "EatOut"
    }
    
    var bundleIdentifier: String {
        return "com.Sozolab.eatout"
    }
    
    private init() {}
}





