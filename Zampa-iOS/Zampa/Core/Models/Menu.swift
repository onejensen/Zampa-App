import Foundation

// MARK: - DietaryInfo

struct DietaryInfo: Codable, Equatable {
    var isVegetarian: Bool = false
    var isVegan: Bool = false
    var hasMeat: Bool = false
    var hasFish: Bool = false
    var hasGluten: Bool = false
    var hasLactose: Bool = false
    var hasNuts: Bool = false
    var hasEgg: Bool = false

    var hasAnyAllergen: Bool { hasGluten || hasLactose || hasNuts || hasEgg }
    var hasAnyInfo: Bool { isVegetarian || isVegan || hasMeat || hasFish || hasAnyAllergen }

    var firestoreMap: [String: Any] {
        ["isVegetarian": isVegetarian, "isVegan": isVegan,
         "hasMeat": hasMeat, "hasFish": hasFish,
         "hasGluten": hasGluten, "hasLactose": hasLactose,
         "hasNuts": hasNuts, "hasEgg": hasEgg]
    }

    static func from(_ map: [String: Any]) -> DietaryInfo {
        DietaryInfo(
            isVegetarian: map["isVegetarian"] as? Bool ?? false,
            isVegan:       map["isVegan"]       as? Bool ?? false,
            hasMeat:       map["hasMeat"]       as? Bool ?? false,
            hasFish:       map["hasFish"]       as? Bool ?? false,
            hasGluten:     map["hasGluten"]     as? Bool ?? false,
            hasLactose:    map["hasLactose"]    as? Bool ?? false,
            hasNuts:       map["hasNuts"]       as? Bool ?? false,
            hasEgg:        map["hasEgg"]        as? Bool ?? false
        )
    }
}

// MARK: - OfferTypes

/// Valores canónicos de `offerType` tal como se guardan en Firestore.
enum OfferTypes {
    static let menuDelDia = "Menú del día"
    static let platoDelDia = "Plato del día"
    static let ofertaDelDia = "Oferta del día"
    static let ofertaPermanente = "Oferta permanente"
    static let all = [menuDelDia, platoDelDia, ofertaDelDia, ofertaPermanente]

    /// Traduce un valor canónico a la etiqueta del idioma actual.
    /// Si el valor es desconocido (datos legacy en otro idioma), lo devuelve tal cual.
    static func label(for value: String) -> String {
        let t = LocalizationManager.shared
        switch value {
        case menuDelDia: return t.t("offer_type_menu")
        case platoDelDia: return t.t("offer_type_plato")
        case ofertaDelDia: return t.t("offer_type_oferta")
        case ofertaPermanente: return t.t("offer_type_permanente")
        default: return value
        }
    }
}

// MARK: - Menu

/// Modelo de oferta diaria (colección `dailyOffers` en Firestore)
struct Menu: Codable, Identifiable, Equatable {
    let id: String
    let businessId: String
    let date: String
    let title: String
    let description: String?
    let priceTotal: Double
    let currency: String
    let photoUrls: [String]
    let tags: [String]?
    let createdAt: String
    let updatedAt: String
    let isActive: Bool
    let isMerchantPro: Bool?
    /// Denormalizado de `businesses/{businessId}.isVerified`. Ausente o `true` = visible.
    /// Las ofertas con `false` se filtran del feed hasta que admin verifique el comercio.
    let isMerchantVerified: Bool?
    let dietaryInfo: DietaryInfo
    /// "Menú", "Plato del día" or "Oferta". Nil = not specified.
    let offerType: String?
    let includesDrink: Bool
    let includesDessert: Bool
    let includesCoffee: Bool
    /// Horario de la oferta: "lunch", "dinner" o "both"
    let serviceTime: String
    /// Oferta permanente (no expira a las 24h)
    let isPermanent: Bool
    /// Días de la semana en que esta oferta permanente es visible (0=lun…6=dom). Nil = todos los días (legado).
    let recurringDays: [Int]?

    init(
        id: String,
        businessId: String,
        date: String,
        title: String,
        description: String? = nil,
        priceTotal: Double,
        currency: String = "EUR",
        photoUrls: [String] = [],
        tags: [String]? = nil,
        createdAt: String = "",
        updatedAt: String = "",
        isActive: Bool = true,
        isMerchantPro: Bool? = false,
        isMerchantVerified: Bool? = nil,
        dietaryInfo: DietaryInfo = DietaryInfo(),
        offerType: String? = nil,
        includesDrink: Bool = false,
        includesDessert: Bool = false,
        includesCoffee: Bool = false,
        serviceTime: String = "both",
        isPermanent: Bool = false,
        recurringDays: [Int]? = nil
    ) {
        self.id = id
        self.businessId = businessId
        self.date = date
        self.title = title
        self.description = description
        self.priceTotal = priceTotal
        self.currency = currency
        self.photoUrls = photoUrls
        self.tags = tags
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.isActive = isActive
        self.isMerchantPro = isMerchantPro
        self.isMerchantVerified = isMerchantVerified
        self.dietaryInfo = dietaryInfo
        self.offerType = offerType
        self.includesDrink = includesDrink
        self.includesDessert = includesDessert
        self.includesCoffee = includesCoffee
        self.serviceTime = serviceTime
        self.isPermanent = isPermanent
        self.recurringDays = recurringDays
    }

    /// Precio formateado con el símbolo de la moneda del restaurante (ej: "10,50 €", "$10.50", "£9.99")
    var formattedPrice: String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .currency
        formatter.currencyCode = currency.isEmpty ? "EUR" : currency
        return formatter.string(from: NSNumber(value: priceTotal))
            ?? String(format: "%.2f \(currency)", priceTotal)
    }

    var isToday: Bool {
        if isPermanent || offerType == "Oferta permanente" { return true }
        let formatter = ISO8601DateFormatter()
        let formatterWithFractional = ISO8601DateFormatter()
        formatterWithFractional.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        guard let parsedDate = formatter.date(from: createdAt)
                ?? formatterWithFractional.date(from: createdAt)
                ?? formatter.date(from: self.date)
                ?? formatterWithFractional.date(from: self.date) else { return false }
        return Date().timeIntervalSince(parsedDate) < 24 * 60 * 60 // Menos de 24 horas
    }

    /// Texto localizado del horario de la oferta
    var serviceTimeLabel: String {
        switch serviceTime {
        case "lunch": return "Mediodía"
        case "dinner": return "Noche"
        default: return "Mediodía y noche"
        }
    }

    /// Returns true if this offer should appear in the feed on the given weekday index.
    /// weekday: 0=Monday…6=Sunday. Non-permanent offers always return true.
    func isVisibleOnDay(_ weekday: Int) -> Bool {
        guard isPermanent else { return true }
        guard let days = recurringDays, !days.isEmpty else { return true }
        return days.contains(weekday)
    }

    /// Returns the set of weekday indices (0=Mon…6=Sun) already occupied by the given permanent offers.
    /// Permanents without recurringDays (legacy) are treated as occupying all 7 days.
    static func occupiedDays(from permanents: [Menu]) -> Set<Int> {
        var occupied = Set<Int>()
        for menu in permanents where menu.isPermanent {
            if let days = menu.recurringDays, !days.isEmpty {
                days.forEach { occupied.insert($0) }
            } else {
                (0...6).forEach { occupied.insert($0) }
            }
        }
        return occupied
    }
}
