import Foundation
import FirebaseFirestore

/// Tasas de cambio en memoria. Base es siempre EUR. `rates[code]` es
/// cuántas unidades de `code` equivalen a 1 EUR.
struct ExchangeRates {
    let base: String
    let rates: [String: Double]
    let updatedAt: Date
}

/// Servicio shared que carga las tasas de `config/exchangeRates` una vez
/// por sesión y expone helpers de conversión y formato.
@MainActor
final class CurrencyService: ObservableObject {
    static let shared = CurrencyService()

    @Published private(set) var rates: ExchangeRates?

    /// ISO codes soportados en v1 (orden para el picker).
    static let supported: [String] = [
        "EUR", "USD", "GBP", "JPY", "CHF",
        "SEK", "NOK", "DKK", "CAD", "AUD"
    ]

    /// Snapshot embebido como fallback cuando la primera lectura de
    /// Firestore aún no ha terminado (o no tiene conectividad).
    /// Actualizado a mano en cada release grande.
    static let fallbackRates: [String: Double] = [
        "USD": 1.09, "GBP": 0.85, "JPY": 158.3, "CHF": 0.96,
        "SEK": 11.42, "NOK": 11.78, "DKK": 7.46, "CAD": 1.48, "AUD": 1.63
    ]

    private var hasLoaded = false
    private let db = Firestore.firestore()

    private init() {}

    /// Dispara una sola vez por sesión. Si falla silenciosamente, el
    /// fallback embebido cubre todas las conversiones.
    func loadIfNeeded() async {
        guard !hasLoaded else { return }
        hasLoaded = true
        do {
            let doc = try await db.collection("config").document("exchangeRates").getDocument()
            guard let data = doc.data() else { return }
            guard let base = data["base"] as? String,
                  let ratesAny = data["rates"] as? [String: Any],
                  let ts = data["updatedAt"] as? Timestamp else {
                return
            }
            var parsed: [String: Double] = [:]
            for (key, value) in ratesAny {
                if let d = value as? Double {
                    parsed[key] = d
                } else if let n = value as? NSNumber {
                    parsed[key] = n.doubleValue
                }
            }
            self.rates = ExchangeRates(base: base, rates: parsed, updatedAt: ts.dateValue())
        } catch {
            print("CurrencyService.loadIfNeeded error:", error.localizedDescription)
        }
    }

    /// Convierte un importe en EUR al código destino. Retorna nil si el
    /// código no es conocido ni en memoria ni en fallback.
    func convert(eurAmount: Double, to code: String) -> Double? {
        if code == "EUR" { return eurAmount }
        let rate = rates?.rates[code] ?? Self.fallbackRates[code]
        guard let rate else { return nil }
        return eurAmount * rate
    }

    /// Formatea "12,50 €" / "$13.60 USD" / "¥1980 JPY" etc.
    /// JPY usa 0 decimales; el resto 2. EUR usa coma decimal (es_ES);
    /// el resto usa punto (en_US) por convención internacional.
    static func format(amount: Double, code: String) -> String {
        let isJPY = code == "JPY"
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.minimumFractionDigits = isJPY ? 0 : 2
        formatter.maximumFractionDigits = isJPY ? 0 : 2
        formatter.locale = code == "EUR" ? Locale(identifier: "es_ES") : Locale(identifier: "en_US")
        let numStr = formatter.string(from: NSNumber(value: amount)) ?? "\(amount)"

        switch code {
        case "EUR": return "\(numStr) €"
        case "USD": return "$\(numStr) USD"
        case "GBP": return "£\(numStr) GBP"
        case "JPY": return "¥\(numStr) JPY"
        case "CHF": return "\(numStr) CHF"
        case "SEK": return "\(numStr) kr SEK"
        case "NOK": return "\(numStr) kr NOK"
        case "DKK": return "\(numStr) kr DKK"
        case "CAD": return "C$\(numStr) CAD"
        case "AUD": return "A$\(numStr) AUD"
        default:    return "\(numStr) \(code)"
        }
    }

    /// Helper ergonómico: convierte un precio en EUR a `code` y devuelve el
    /// string listo para mostrar. Si la conversión no es posible, devuelve nil.
    static func formatConverted(eurAmount: Double, to code: String) -> String? {
        guard let converted = CurrencyService.shared.convert(eurAmount: eurAmount, to: code) else {
            return nil
        }
        return format(amount: converted, code: code)
    }
}
