package com.sozolab.eatout.data.model

/** Modelo de usuario */
data class User(
    val id: String = "",
    val email: String = "",
    val name: String = "",
    val role: UserRole = UserRole.CLIENTE,
    val phone: String? = null,
    val photoUrl: String? = null,
) {
    enum class UserRole {
        CLIENTE, COMERCIO;
        companion object {
            fun fromString(s: String) = when(s.uppercase()) {
                "COMERCIO" -> COMERCIO
                else -> CLIENTE
            }
        }
        fun toFirestore() = name
    }
}

/** Dirección del comercio */
data class MerchantAddress(
    val formatted: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val placeId: String? = null,
)

/** Entrada de horario */
data class ScheduleEntry(
    val day: String = "",
    val open: String = "",
    val close: String = "",
)

/** Perfil de comercio (colección `businesses` en Firestore) */
data class Merchant(
    val id: String = "",
    val userId: String? = null,
    val name: String = "",
    val phone: String? = null,
    val address: MerchantAddress? = null,
    val addressText: String? = null,
    val schedule: List<ScheduleEntry>? = null,
    val cuisineTypes: List<String>? = null,
    val acceptsReservations: Boolean = false,
    val shortDescription: String? = null,
    val coverPhotoUrl: String? = null,
    val profilePhotoUrl: String? = null,
    val planTier: String? = "free",
    val isHighlighted: Boolean? = false,
)

/** Información dietética y alérgenos de una oferta */
data class DietaryInfo(
    val isVegetarian: Boolean = false,
    val isVegan: Boolean = false,
    val hasMeat: Boolean = false,
    val hasFish: Boolean = false,
    val hasGluten: Boolean = false,
    val hasLactose: Boolean = false,
    val hasNuts: Boolean = false,
    val hasEgg: Boolean = false,
) {
    val hasAnyAllergen get() = hasGluten || hasLactose || hasNuts || hasEgg
    val hasAnyInfo get() = isVegetarian || isVegan || hasMeat || hasFish || hasAnyAllergen

    fun toMap(): Map<String, Any> = mapOf(
        "isVegetarian" to isVegetarian, "isVegan" to isVegan,
        "hasMeat" to hasMeat, "hasFish" to hasFish,
        "hasGluten" to hasGluten, "hasLactose" to hasLactose,
        "hasNuts" to hasNuts, "hasEgg" to hasEgg,
    )

    companion object {
        fun from(map: Map<String, Any>): DietaryInfo = DietaryInfo(
            isVegetarian = map["isVegetarian"] as? Boolean ?: false,
            isVegan       = map["isVegan"]       as? Boolean ?: false,
            hasMeat       = map["hasMeat"]       as? Boolean ?: false,
            hasFish       = map["hasFish"]       as? Boolean ?: false,
            hasGluten     = map["hasGluten"]     as? Boolean ?: false,
            hasLactose    = map["hasLactose"]    as? Boolean ?: false,
            hasNuts       = map["hasNuts"]       as? Boolean ?: false,
            hasEgg        = map["hasEgg"]        as? Boolean ?: false,
        )
    }
}

/** Oferta diaria (colección `dailyOffers` en Firestore) */
data class Menu(
    val id: String = "",
    val businessId: String = "",
    val date: String = "",
    val title: String = "",
    val description: String? = null,
    val priceTotal: Double = 0.0,
    val currency: String = "EUR",
    val photoUrls: List<String> = emptyList(),
    val tags: List<String>? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
    val isActive: Boolean = true,
    val isMerchantPro: Boolean? = false,
    val dietaryInfo: DietaryInfo = DietaryInfo(),
    val offerType: String? = null,
    val includesDrink: Boolean = false,
    val includesDessert: Boolean = false,
    val includesCoffee: Boolean = false,
) {
    val isToday: Boolean
        get() {
            if (offerType == "Oferta permanente") return true
            try {
                val iso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                val dateObj = iso.parse(createdAt) ?: iso.parse(date) ?: return false
                val today = java.util.Calendar.getInstance()
                val menuDay = java.util.Calendar.getInstance().apply { time = dateObj }
                return today.get(java.util.Calendar.YEAR) == menuDay.get(java.util.Calendar.YEAR) &&
                       today.get(java.util.Calendar.DAY_OF_YEAR) == menuDay.get(java.util.Calendar.DAY_OF_YEAR)
            } catch (e: Exception) {
                return false
            }
        }
}

/** Favorito (colección `favorites` en Firestore) */
data class Favorite(
    val id: String = "",
    val customerId: String = "",
    val businessId: String = "",
    val createdAt: String = "",
    val notificationsEnabled: Boolean = true,
)

/** Catálogo de tipos de cocina (colección `cuisineTypes` en Firestore) */
data class CuisineType(
    val id: String = "",
    val name: String = "",
)

/** Modelo de suscripción de comercio (colección `subscriptions` en Firestore) */
data class Subscription(
    val id: String = "",
    val businessId: String = "",
    val type: String = "MONTHLY",
    val status: String = "ACTIVE",
    val startDate: String = "",
    val endDate: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
)

/** Notificación del sistema (colección `notifications` en Firestore) */
data class AppNotification(
    val id: String = "",
    val userId: String = "",
    val businessId: String? = null,
    val offerId: String? = null,
    val type: String = "NEW_OFFER_FAVORITE",
    val title: String = "",
    val body: String = "",
    val read: Boolean = false,
    val createdAt: String = "",
)
