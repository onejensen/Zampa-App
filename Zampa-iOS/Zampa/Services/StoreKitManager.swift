import Foundation
import StoreKit

/// Singleton que gestiona la suscripción in-app vía StoreKit 2.
///
/// Flujo:
/// 1. `loadProduct()` carga el producto desde la App Store (precio, título...).
/// 2. `purchase()` lanza el sheet nativo de Apple. Antes obtiene un UUID estable
///    desde `businesses/{uid}.appAccountToken` para que el webhook server-side
///    (`appStoreNotifications`) pueda mapear la transacción al merchant correcto.
/// 3. `Transaction.updates` se escucha en background. Cada transacción verificada
///    se cierra con `transaction.finish()`. El estado real de la suscripción
///    (`subscriptionStatus`, `subscriptionActiveUntil`) se escribe en Firestore
///    desde la Cloud Function tras recibir Apple's ASSN v2 — la app sólo cierra
///    la transacción de StoreKit.
@MainActor
final class StoreKitManager: ObservableObject {
    static let shared = StoreKitManager()

    /// Product ID que coincide con App Store Connect, Play Console y backend.
    static let productId = "zampa_pro_monthly"

    @Published private(set) var product: Product?
    @Published private(set) var isPurchasing: Bool = false
    @Published var lastError: String?

    private var updatesTask: Task<Void, Never>?

    private init() {}

    /// Inicia el listener global de `Transaction.updates`. Llamar UNA vez al
    /// arrancar la app (desde `ZampaApp` o equivalente). Sigue vivo el resto
    /// del ciclo de vida del proceso.
    func startTransactionListener() {
        updatesTask?.cancel()
        updatesTask = Task.detached { [weak self] in
            for await result in Transaction.updates {
                guard let self else { continue }
                await self.handle(transactionResult: result)
            }
        }
    }

    /// Carga el producto desde la App Store. Llamar al abrir la pantalla de suscripción.
    func loadProduct() async {
        do {
            let products = try await Product.products(for: [Self.productId])
            self.product = products.first
            if products.first == nil {
                self.lastError = "Producto no disponible (¿configurado en App Store Connect?)"
            }
        } catch {
            self.lastError = "No se pudo cargar el producto: \(error.localizedDescription)"
        }
    }

    /// Lanza el flujo de compra. Devuelve `true` si terminó OK (purchased o pending),
    /// `false` si el usuario canceló o hubo error.
    func purchase() async -> Bool {
        guard let product else {
            lastError = "Producto no cargado todavía."
            return false
        }
        isPurchasing = true
        defer { isPurchasing = false }

        do {
            // Obtener/crear el UUID que mapea esta cuenta. Crítico: si fallara este
            // paso, el webhook no podría asociar la compra al merchant correcto.
            let token = try await FirebaseService.shared.getOrCreateAppAccountToken()

            let result = try await product.purchase(options: [.appAccountToken(token)])
            switch result {
            case .success(let verification):
                // Verificación local (Apple firma cada Transaction con su clave).
                // No es validación server-side — eso lo hace el webhook con
                // App Store Server API. Aquí sólo cerramos la transacción local.
                if case .verified(let transaction) = verification {
                    await transaction.finish()
                    return true
                } else {
                    lastError = "Transacción no verificada por Apple."
                    return false
                }
            case .userCancelled:
                return false
            case .pending:
                // Compra pendiente (e.g. Ask to Buy de menores). El webhook recibirá
                // el evento cuando se aprueba; el merchant no entra a "active" todavía.
                lastError = "Compra pendiente de aprobación."
                return true
            @unknown default:
                lastError = "Resultado de compra desconocido."
                return false
            }
        } catch {
            lastError = "Error en la compra: \(error.localizedDescription)"
            return false
        }
    }

    /// Procesa cada transacción recibida vía `Transaction.updates` (renovaciones,
    /// upgrades, restores, etc.). Sólo cierra la transacción — el estado en
    /// Firestore lo gestiona el webhook.
    private func handle(transactionResult: VerificationResult<Transaction>) async {
        if case .verified(let transaction) = transactionResult {
            await transaction.finish()
        }
    }
}
