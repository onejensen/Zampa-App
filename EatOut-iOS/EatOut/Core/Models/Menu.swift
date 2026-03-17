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
    let dietaryInfo: DietaryInfo
    /// "Menú", "Plato del día" or "Oferta". Nil = not specified.
    let offerType: String?
    let includesDrink: Bool
    let includesDessert: Bool
    let includesCoffee: Bool

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
        dietaryInfo: DietaryInfo = DietaryInfo(),
        offerType: String? = nil,
        includesDrink: Bool = false,
        includesDessert: Bool = false,
        includesCoffee: Bool = false
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
        self.dietaryInfo = dietaryInfo
        self.offerType = offerType
        self.includesDrink = includesDrink
        self.includesDessert = includesDessert
        self.includesCoffee = includesCoffee
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
        if offerType == "Oferta permanente" { return true }
        let formatter = ISO8601DateFormatter()
        guard let parsedDate = formatter.date(from: createdAt) ?? formatter.date(from: self.date) else { return false }
        return Calendar.current.isDateInToday(parsedDate)
    }
}
