import Foundation
import FirebaseAuth

/// Cliente HTTP para comunicarse con la API del backend
class APIClient {
    static let shared = APIClient()
    
    private let baseURL: String
    private let session: URLSession
    private let keychainManager = KeychainManager.shared
    
    private init() {
        self.baseURL = Config.shared.apiBaseURL
        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = 30
        configuration.timeoutIntervalForResource = 60
        self.session = URLSession(configuration: configuration)
    }
    
    // MARK: - Request Methods
    
    /// Realiza una petición GET
    func get<T: Decodable>(_ endpoint: String, responseType: T.Type) async throws -> T {
        return try await request(endpoint, method: "GET", body: nil, responseType: responseType)
    }
    
    /// Realiza una petición POST
    func post<T: Decodable>(_ endpoint: String, body: Encodable?, responseType: T.Type) async throws -> T {
        return try await request(endpoint, method: "POST", body: body, responseType: responseType)
    }
    
    /// Realiza una petición PATCH
    func patch<T: Decodable>(_ endpoint: String, body: Encodable?, responseType: T.Type) async throws -> T {
        return try await request(endpoint, method: "PATCH", body: body, responseType: responseType)
    }
    
    /// Realiza una petición DELETE
    func delete<T: Decodable>(_ endpoint: String, responseType: T.Type) async throws -> T {
        return try await request(endpoint, method: "DELETE", body: nil, responseType: responseType)
    }
    
    // MARK: - Private Methods
    
    private func request<T: Decodable>(
        _ endpoint: String,
        method: String,
        body: Encodable?,
        responseType: T.Type
    ) async throws -> T {
        guard let url = URL(string: "\(baseURL)\(endpoint)") else {
            throw APIError.invalidURL
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        // Agregar token de autenticación si está disponible
        if let accessToken = keychainManager.getAccessToken() {
            request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        }
        
        // Agregar body si existe
        if let body = body {
            let encoder = JSONEncoder()
            encoder.dateEncodingStrategy = .iso8601
            request.httpBody = try encoder.encode(body)
        }
        
        // Realizar la petición
        let (data, response) = try await session.data(for: request)
        
        // Verificar respuesta HTTP
        guard let httpResponse = response as? HTTPURLResponse else {
            throw APIError.invalidResponse
        }
        
        // Manejar errores HTTP
        guard (200...299).contains(httpResponse.statusCode) else {
            if httpResponse.statusCode == 401 {
                // Token inválido, intentar refresh
                if let newToken = try? await refreshAccessToken() {
                    // Reintentar la petición con el nuevo token
                    request.setValue("Bearer \(newToken)", forHTTPHeaderField: "Authorization")
                    let (retryData, retryResponse) = try await session.data(for: request)
                    
                    guard let retryHttpResponse = retryResponse as? HTTPURLResponse else {
                        throw APIError.invalidResponse
                    }
                    
                    guard (200...299).contains(retryHttpResponse.statusCode) else {
                        throw APIError.httpError(statusCode: retryHttpResponse.statusCode)
                    }
                    
                    // Decodificar respuesta del retry
                    let decoder = JSONDecoder()
                    decoder.dateDecodingStrategy = .iso8601
                    return try decoder.decode(T.self, from: retryData)
                } else {
                    // No se pudo refrescar el token, cerrar sesión
                    throw APIError.unauthorized
                }
            }
            throw APIError.httpError(statusCode: httpResponse.statusCode)
        }
        
        // Decodificar respuesta
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        
        do {
            return try decoder.decode(T.self, from: data)
        } catch {
            throw APIError.decodingError(error)
        }
    }
    
    /// Refresca el token de acceso automáticamente usando Firebase
    private func refreshAccessToken() async throws -> String {
        guard let user = FirebaseAuth.Auth.auth().currentUser else {
            throw APIError.unauthorized
        }
        return try await user.getIDToken(forcingRefresh: true)
    }
}

// MARK: - API Errors

enum APIError: LocalizedError {
    case invalidURL
    case invalidResponse
    case unauthorized
    case httpError(statusCode: Int)
    case decodingError(Error)
    case networkError(Error)
    
    var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "URL inválida"
        case .invalidResponse:
            return "Respuesta inválida del servidor"
        case .unauthorized:
            return "No autorizado. Por favor, inicia sesión nuevamente"
        case .httpError(let statusCode):
            return "Error HTTP: \(statusCode)"
        case .decodingError(let error):
            return "Error al decodificar respuesta: \(error.localizedDescription)"
        case .networkError(let error):
            return "Error de red: \(error.localizedDescription)"
        }
    }
}





