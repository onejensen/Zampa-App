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

    /// Product IDs activos. Apple requiere un product por duración.
    /// Si alguno no está creado en App Store Connect todavía, se ignora silenciosamente
    /// (la UI sólo muestra los planes que cargaron OK).
    static let monthlyProductId = "zampa_pro_monthly"
    static let annualProductId = "zampa_pro_annual"
    static let productIds = [monthlyProductId, annualProductId]

    @Published private(set) var monthlyProduct: Product?
    @Published private(set) var annualProduct: Product?
    @Published private(set) var isPurchasing: Bool = false
    @Published var lastError: String?

    /// Compatibilidad con código viejo que usaba `product` singular: devuelve el mensual.
    var product: Product? { monthlyProduct }

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

    /// Carga ambos productos (mensual + anual) desde la App Store. Si alguno no
    /// está configurado todavía, se queda nil y la UI sólo muestra el que cargó.
    func loadProduct() async {
        do {
            let products = try await Product.products(for: Set(Self.productIds))
            self.monthlyProduct = products.first { $0.id == Self.monthlyProductId }
            self.annualProduct = products.first { $0.id == Self.annualProductId }
            if monthlyProduct == nil && annualProduct == nil {
                self.lastError = "Productos no disponibles (¿configurados en App Store Connect?)"
            }
        } catch {
            self.lastError = "No se pudieron cargar los productos: \(error.localizedDescription)"
        }
    }

    /// Lanza el flujo de compra del producto indicado. Devuelve `true` si terminó OK
    /// (purchased o pending), `false` si el usuario canceló o hubo error.
    func purchase(_ product: Product) async -> Bool {
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
